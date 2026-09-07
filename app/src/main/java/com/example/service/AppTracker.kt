package com.example.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.entities.AppUnlockSessionEntity
import com.example.data.entities.ControlledAppEntity
import com.example.data.entities.ControlledAppModes
import com.example.data.entities.UnlockEndReasons
import com.example.data.repository.AppUnlockRepository
import com.example.data.repository.ControlledAppRepository
import com.example.presentation.blocker.BlockerActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enforces the core rule: a controlled app is LOCKED unless the user has spent reward
 * credits to open an access window for it, and it re-locks the moment that window is used up.
 *
 * Enforcement is continuous rather than transition-based. Every foreground sample is
 * re-evaluated, so returning to a blocked app, dismissing the blocker, or simply sitting in
 * the app while its purchased time drains all lead straight back to the block screen.
 */
class AppTracker(
    private val controlledAppRepository: ControlledAppRepository,
    private val unlockRepository: AppUnlockRepository
) {
    sealed interface Decision {
        data object Allowed : Decision
        data class Blocked(val appName: String, val reason: String) : Decision
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** In-memory mirrors of the DB, kept fresh by flows so the 1 Hz gate never hits disk. */
    @Volatile private var controlledApps: Map<String, ControlledAppEntity> = emptyMap()
    @Volatile private var activeSessions: Map<String, AppUnlockSessionEntity> = emptyMap()
    private val appsLoaded = CompletableDeferred<Unit>()
    private val sessionsLoaded = CompletableDeferred<Unit>()

    @Volatile private var foregroundPackage: String? = null
    @Volatile private var appContext: Context? = null

    private var enforcementJob: Job? = null
    private val evaluationLock = Mutex()

    // Foreground metering for the session currently being consumed.
    private var meteredSessionId: Int? = null
    private var meteredConsumedSeconds: Int = 0
    private var meterAnchorMillis: Long = 0L
    private var lastPersistMillis: Long = 0L

    private var lastBlockAtMillis: Long = 0L
    private var lastBlockedPackage: String? = null

    init {
        scope.launch {
            controlledAppRepository.selectedApps.collect { apps ->
                controlledApps = apps.associateBy { it.packageName }
                if (!appsLoaded.isCompleted) appsLoaded.complete(Unit)
            }
        }
        scope.launch {
            unlockRepository.activeSessions.collect { sessions ->
                activeSessions = sessions.associateBy { it.packageName }
                if (!sessionsLoaded.isCompleted) sessionsLoaded.complete(Unit)
            }
        }
    }

    /**
     * Called for every foreground sample taken by [EarnTimeMonitorService], including
     * repeated samples of the same app. The repetition is the point: it is what stops a user
     * from walking back into a locked app after dismissing the blocker.
     */
    fun onForegroundApp(packageName: String, context: Context) {
        appContext = context.applicationContext
        val changed = foregroundPackage != packageName
        foregroundPackage = packageName
        ensureEnforcementRunning()
        if (changed) {
            // React immediately instead of waiting for the next tick.
            scope.launch { enforceOnce() }
        }
    }

    /** Kept for source compatibility with the previous transition-based API. */
    fun onAppForegrounded(packageName: String, context: Context) = onForegroundApp(packageName, context)

    private fun ensureEnforcementRunning() {
        if (enforcementJob?.isActive == true) return
        enforcementJob = scope.launch {
            while (isActive) {
                enforceOnce()
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun enforceOnce(): Unit = evaluationLock.withLock {
        val context = appContext ?: return@withLock
        val packageName = foregroundPackage ?: return@withLock

        if (isExempt(packageName, context)) {
            // Nothing controlled is on screen, so freeze the meter rather than burn purchased time.
            pauseMeter()
            lastBlockedPackage = null
            return@withLock
        }

        when (val decision = evaluate(packageName)) {
            is Decision.Allowed -> lastBlockedPackage = null
            is Decision.Blocked -> {
                pauseMeter()
                block(context, packageName, decision.appName, decision.reason)
            }
        }
    }

    /** Resolves whether [packageName] may currently be on screen, metering it if so. */
    private suspend fun evaluate(packageName: String): Decision {
        val app = resolveControlledApp(packageName)

        if (app == null || !app.isSelected || app.mode == ControlledAppModes.FREE) {
            pauseMeter()
            return Decision.Allowed
        }

        if (app.mode == ControlledAppModes.ALWAYS_BLOCKED) {
            return Decision.Blocked(app.appName, BlockerActivity.REASON_ALWAYS_BLOCKED)
        }

        // REWARD mode: locked by default, open only while a purchased session has time left.
        sessionsLoaded.await()
        val session = activeSessions[packageName]
            ?: unlockRepository.getActiveSession(packageName)
            ?: return Decision.Blocked(app.appName, BlockerActivity.REASON_LOCKED)

        val consumed = meterForeground(session)
        if (consumed >= session.grantedSeconds) {
            finishSession(session, consumed, UnlockEndReasons.EXHAUSTED)
            return Decision.Blocked(app.appName, BlockerActivity.REASON_TIME_UP)
        }
        return Decision.Allowed
    }

    /**
     * Snapshot lookup, falling back to the DB when the flows have not emitted yet, so a freshly
     * started service can never wave an app through just because its cache is cold.
     */
    private suspend fun resolveControlledApp(packageName: String): ControlledAppEntity? {
        if (appsLoaded.isCompleted) return controlledApps[packageName]
        controlledAppRepository.getApp(packageName)?.let { if (it.isSelected) return it }
        appsLoaded.await()
        return controlledApps[packageName]
    }

    /**
     * Advances the meter for [session] using elapsed real time, so the count keeps running
     * even if the device dozes, and persists it periodically.
     */
    private suspend fun meterForeground(session: AppUnlockSessionEntity): Int {
        val now = SystemClock.elapsedRealtime()
        if (meteredSessionId != session.id) {
            // Flush the outgoing session before adopting the new one, so switching between two
            // unlocked apps cannot silently hand back unsaved seconds.
            pauseMeter()
            meteredSessionId = session.id
            meteredConsumedSeconds = session.consumedSeconds
            meterAnchorMillis = now
            lastPersistMillis = now
            return meteredConsumedSeconds
        }

        val elapsedSeconds = ((now - meterAnchorMillis) / 1000L).toInt()
        if (elapsedSeconds > 0) {
            meterAnchorMillis += elapsedSeconds * 1000L
            meteredConsumedSeconds = (meteredConsumedSeconds + elapsedSeconds)
                .coerceAtMost(session.grantedSeconds)
        }

        if (now - lastPersistMillis >= PERSIST_INTERVAL_MILLIS) {
            lastPersistMillis = now
            unlockRepository.saveProgress(session.id, meteredConsumedSeconds)
        }
        return meteredConsumedSeconds
    }

    /** Flush and stop metering, used whenever the metered app leaves the foreground. */
    private suspend fun pauseMeter() {
        val sessionId = meteredSessionId ?: return
        meteredSessionId = null
        unlockRepository.saveProgress(sessionId, meteredConsumedSeconds)
    }

    private suspend fun finishSession(session: AppUnlockSessionEntity, consumed: Int, reason: String) {
        meteredSessionId = null
        // Time is fully used up, so there is nothing to refund.
        unlockRepository.endSession(session.id, consumed, reason, refundUnused = false)
    }

    private fun block(context: Context, packageName: String, appName: String, reason: String) {
        val now = System.currentTimeMillis()
        if (lastBlockedPackage == packageName && now - lastBlockAtMillis < REBLOCK_THROTTLE_MILLIS) return

        lastBlockedPackage = packageName
        lastBlockAtMillis = now

        val intent = Intent(context, BlockerActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockerActivity.EXTRA_APP_NAME, appName)
            putExtra(BlockerActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockerActivity.EXTRA_REASON, reason)
        }
        context.startActivity(intent)
    }

    private fun isExempt(packageName: String, context: Context): Boolean =
        packageName == context.packageName || packageName in EXEMPT_PACKAGES

    companion object {
        private const val TICK_MILLIS = 1_000L
        private const val PERSIST_INTERVAL_MILLIS = 5_000L

        /**
         * Short enough that a user who dismisses the blocker is bounced back almost instantly,
         * long enough that we do not thrash the activity manager.
         */
        private const val REBLOCK_THROTTLE_MILLIS = 600L

        private val EXEMPT_PACKAGES = setOf(
            "com.android.systemui",
            "android"
        )
    }
}
