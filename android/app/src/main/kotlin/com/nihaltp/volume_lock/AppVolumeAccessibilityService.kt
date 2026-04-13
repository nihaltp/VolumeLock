package com.nihaltp.volume_lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that detects foreground app changes and broadcasts
 * them to [AppVolumeLockService].
 *
 * Only TYPE_WINDOW_STATE_CHANGED events from activities are forwarded (not
 * dialogs, menus, etc.) so the broadcasts are minimal.
 */
class AppVolumeAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own package and repeated events for the same package.
        if (pkg == packageName || pkg == lastPackage) return
        lastPackage = pkg

        // Notify the AppVolumeLockService.
        val broadcast = Intent(AppVolumeLockService.ACTION_APP_CHANGED).apply {
            `package` = packageName
            putExtra(AppVolumeLockService.EXTRA_PACKAGE_NAME, pkg)
        }
        sendBroadcast(broadcast)
    }

    override fun onInterrupt() {}
}
