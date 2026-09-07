package com.example.service

import android.content.Context
import com.example.data.entities.ControlledAppModes
import com.example.data.repository.AppUnlockRepository
import com.example.data.repository.ControlledAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Second line of defence for device-owner / profile-owner installs: OS-level package suspension.
 * A controlled app is suspended unless it currently has a paid access window, which means it
 * cannot even be launched rather than merely being covered by the blocker.
 *
 * No-ops on ordinary installs, where [AppTracker] does the enforcing.
 */
class AppSuspensionManager(
    private val context: Context,
    private val controlledAppRepository: ControlledAppRepository,
    private val appUnlockRepository: AppUnlockRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startListening() {
        scope.launch {
            combine(
                controlledAppRepository.selectedApps,
                appUnlockRepository.activeSessions
            ) { apps, sessions -> apps to sessions.map { it.packageName }.toSet() }
                // The meter persists progress every few seconds; without this we would call
                // into DevicePolicyManager on that same cadence for no reason.
                .distinctUntilChanged()
                .collect { (apps, unlockedPackages) ->
                    apps.forEach { app ->
                        val shouldSuspend = when (app.mode) {
                            ControlledAppModes.ALWAYS_BLOCKED -> true
                            // Locked by default; only an open access window lifts the suspension.
                            ControlledAppModes.REWARD -> app.packageName !in unlockedPackages
                            else -> false
                        }
                        AppSuspender.setAppSuspended(context, app.packageName, shouldSuspend)
                    }
                }
        }
    }
}
