package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.EarnTimeApplication
import kotlinx.coroutines.*

class EarnTimeMonitorService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var appTracker: AppTracker
    private lateinit var usageStatsManager: UsageStatsManager

    override fun onCreate() {
        super.onCreate()
        val app = application as EarnTimeApplication
        appTracker = app.container.appTracker
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val channelId = "earntime_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage to block restricted apps"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EarnTime Active")
            .setContentText("Monitoring app usage...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: use app icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            var lastApp = ""
            while (isActive) {
                val currentApp = getForegroundApp()
                if (currentApp != null && currentApp != lastApp) {
                    lastApp = currentApp
                    withContext(Dispatchers.Main) {
                        appTracker.onAppForegrounded(currentApp, this@EarnTimeMonitorService)
                    }
                }
                delay(1000) // Poll every second
            }
        }
    }

    private fun getForegroundApp(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Look back 10 seconds

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null
        var lastEventTime = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > lastEventTime) {
                    lastEventTime = event.timeStamp
                    lastForegroundApp = event.packageName
                }
            }
        }
        return lastForegroundApp
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
