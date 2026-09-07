package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.EarnTimeApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EarnTimeMonitorService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private lateinit var appTracker: AppTracker
    private lateinit var usageStatsManager: UsageStatsManager

    /**
     * The usage-events API only reports transitions. Between transitions there are no events at
     * all, so we remember the last one we saw and keep reporting it; otherwise enforcement would
     * go blind as soon as the user sat still in an app.
     */
    private var lastKnownForegroundApp: String? = null
    private var lastQueryTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val app = application as EarnTimeApplication
        appTracker = app.container.appTracker
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        lastQueryTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage to block restricted apps"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EarnTime Active")
            .setContentText("Controlled apps stay locked until you spend credits")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                // Report every sample, not just changes: the gate re-evaluates continuously so
                // that a locked app cannot be re-entered after the blocker is dismissed.
                getForegroundApp()?.let { appTracker.onForegroundApp(it, this@EarnTimeMonitorService) }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun getForegroundApp(): String? {
        val endTime = System.currentTimeMillis()
        // Overlap the previous window slightly so no transition can slip between polls.
        val startTime = (lastQueryTime - QUERY_OVERLAP_MILLIS).coerceAtMost(endTime)

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var latestTimestamp = 0L

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val isResume = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (isResume && event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    lastKnownForegroundApp = event.packageName
                }
            }
        } catch (e: SecurityException) {
            // Usage access was revoked; the dashboard prompts for it again on next launch.
            return null
        }

        lastQueryTime = endTime
        return lastKnownForegroundApp
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Swiping the app away from recents must not disable enforcement.
        val restartServiceIntent = Intent(applicationContext, EarnTimeMonitorService::class.java)
            .also { it.setPackage(packageName) }
        val restartServicePendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "earntime_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MILLIS = 300L
        private const val QUERY_OVERLAP_MILLIS = 2_000L
        private const val INITIAL_LOOKBACK_MILLIS = 60_000L
    }
}
