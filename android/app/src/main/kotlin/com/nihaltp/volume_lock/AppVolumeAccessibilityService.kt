package com.nihaltp.volume_lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private val FLUTTER_LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    private fun getStringListFromPrefs(sharedPrefs: SharedPreferences, key: String): List<String> {
        val list = mutableListOf<String>()
        val value = sharedPrefs.all[key] ?: return list

        if (value is Set<*>) {
            for (item in value) {
                if (item is String) {
                    list.add(item)
                }
            }
        } else if (value is String) {
            val jsonStr = if (value.startsWith(FLUTTER_LIST_PREFIX)) {
                value.substring(FLUTTER_LIST_PREFIX.length)
            } else {
                value
            }
            try {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                android.util.Log.e("VolumeLock", "Failed to parse list JSON for key $key: ${e.message}")
            }
        }
        return list
    }

    private fun saveStringListToPrefs(sharedPrefs: SharedPreferences, key: String, list: List<String>) {
        val value = sharedPrefs.all[key]
        val editor = sharedPrefs.edit()

        if (value is Set<*>) {
            editor.putStringSet(key, HashSet(list))
        } else {
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            val serialized = FLUTTER_LIST_PREFIX + jsonArray.toString()
            editor.putString(key, serialized)
        }
        editor.apply()
    }

    private fun logEvent(message: String) {
        android.util.Log.i("VolumeLock", "AppVolumeAccessibilityService: $message")
        val sharedPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("flutter.logging_enabled", false)) return

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val logLine = "$timestamp | AppVolumeAccessibilityService: $message"

        synchronized(this) {
            val currentLogs = getStringListFromPrefs(sharedPrefs, "flutter.app_logs").toMutableList()
            currentLogs.add(logLine)

            if (currentLogs.size > 500) {
                currentLogs.subList(0, currentLogs.size - 500).clear()
            }

            saveStringListToPrefs(sharedPrefs, "flutter.app_logs", currentLogs)
        }
    }

    override fun onServiceConnected() {
        logEvent("onServiceConnected")
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

        logEvent("onAccessibilityEvent: Window state changed to $pkg")

        // Ignore our own package and repeated events for the same package.
        if (pkg == packageName) {
            logEvent("Ignoring own package event: $pkg")
            return
        }
        if (pkg == lastPackage) {
            return
        }
        lastPackage = pkg

        logEvent("Forwarding ACTION_APP_CHANGED for package: $pkg")

        // Notify the AppVolumeLockService.
        val broadcast = Intent(AppVolumeLockService.ACTION_APP_CHANGED).apply {
            `package` = packageName
            putExtra(AppVolumeLockService.EXTRA_PACKAGE_NAME, pkg)
        }
        sendBroadcast(broadcast)
    }

    override fun onInterrupt() {
        logEvent("onInterrupt called")
    }
}
