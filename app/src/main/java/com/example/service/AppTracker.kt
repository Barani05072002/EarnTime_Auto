package com.example.service

import android.content.Context
import android.content.Intent
import com.example.data.entities.ControlledAppEntity
import com.example.data.entities.CreditTransactionEntity
import com.example.data.repository.ControlledAppRepository
import com.example.data.repository.CreditRepository
import com.example.presentation.blocker.BlockerActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AppTracker(
    private val controlledAppRepository: ControlledAppRepository,
    private val creditRepository: CreditRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null
    private var currentApp: String? = null

    fun onAppForegrounded(packageName: String, context: Context) {
        // Ignore system UI and our own app
        if (packageName == "com.android.systemui" || packageName == context.packageName) return
        
        if (currentApp == packageName) return
        currentApp = packageName
        currentJob?.cancel()

        scope.launch {
            val apps = controlledAppRepository.selectedApps.first()
            val controlledApp = apps.find { it.packageName == packageName }

            if (controlledApp != null) {
                if (controlledApp.mode == "ALWAYS_BLOCKED") {
                    blockApp(context, controlledApp.appName)
                } else if (controlledApp.mode == "REWARD") {
                    startTracking(context, controlledApp)
                }
            }
        }
    }

    private fun startTracking(context: Context, app: ControlledAppEntity) {
        currentJob = scope.launch {
            while (isActive) {
                val balance = creditRepository.currentBalance.first()
                if (balance <= 0) {
                    blockApp(context, app.appName)
                    break
                }
                
                delay(60_000) // Wait 1 minute
                
                if (isActive) {
                    // Deduct 1 credit after 1 minute of usage
                    creditRepository.addTransaction(
                        amount = -1,
                        type = "APP_USAGE",
                        reason = "Used ${app.appName} for 1 min"
                    )
                }
            }
        }
    }

    private fun blockApp(context: Context, appName: String) {
        val intent = Intent(context, BlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("APP_NAME", appName)
        }
        context.startActivity(intent)
    }
}
