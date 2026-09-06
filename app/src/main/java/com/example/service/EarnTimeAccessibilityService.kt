package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.EarnTimeApplication

class EarnTimeAccessibilityService : AccessibilityService() {
    private lateinit var appTracker: AppTracker

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as EarnTimeApplication
        appTracker = app.container.appTracker
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            appTracker.onAppForegrounded(packageName, this)
        }
    }

    override fun onInterrupt() {}
}
