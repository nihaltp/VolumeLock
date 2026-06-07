package com.nihaltp.volume_lock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that implements the "App Volume Lock" feature.
 *
 * This service is started with a list of tracked package names. The
 * [AppVolumeAccessibilityService] broadcasts foreground-app-change events to
 * this service via [ACTION_APP_CHANGED].
 *
 * Behaviour:
 *  - When a tracked app moves to the background, its current media volume is
 *    saved.
 *  - When a tracked app comes to the foreground, its saved media volume is
 *    restored.
 *  - Only media volume (STREAM_MUSIC) is managed.
 */
class AppVolumeLockService : Service() {

    companion object {
        const val CHANNEL_ID = "app_volume_lock_service"
        private const val NOTIFICATION_ID = 1002

        /** Broadcast action sent by [AppVolumeAccessibilityService]. */
        const val ACTION_APP_CHANGED = "com.nihaltp.volume_lock.ACTION_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "packageName"

        /** Broadcast action sent by [MainActivity] to update tracked packages. */
        const val ACTION_UPDATE_PACKAGES = "com.nihaltp.volume_lock.ACTION_UPDATE_PACKAGES"
        const val EXTRA_TRACKED_PACKAGES = "trackedPackages"
    }

    private lateinit var audioManager: AudioManager

    // packageName → remembered media volume
    private val rememberedVolumes = mutableMapOf<String, Int>()
    private val trackedPackages   = mutableSetOf<String>()
    private var currentForegroundApp: String? = null

    // ── Helper methods for SharedPreferences & Logging ─────────────────────

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
        android.util.Log.i("VolumeLock", "AppVolumeLockService: $message")
        val sharedPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("flutter.logging_enabled", false)) return

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val logLine = "$timestamp | AppVolumeLockService: $message"

        synchronized(this) {
            val currentLogs = getStringListFromPrefs(sharedPrefs, "flutter.app_logs").toMutableList()
            currentLogs.add(logLine)

            if (currentLogs.size > 500) {
                currentLogs.subList(0, currentLogs.size - 500).clear()
            }

            saveStringListToPrefs(sharedPrefs, "flutter.app_logs", currentLogs)
        }
    }

    private fun getRememberedVolume(packageName: String): Int? {
        val sharedPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val key = "flutter.app_vol_$packageName"
        if (!sharedPrefs.contains(key)) return null
        return try {
            sharedPrefs.getLong(key, -1L).toInt().takeIf { it >= 0 }
        } catch (e: ClassCastException) {
            sharedPrefs.getInt(key, -1).takeIf { it >= 0 }
        }
    }

    private fun loadTrackedPackagesFromPrefs() {
        val sharedPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val trackedList = getStringListFromPrefs(sharedPrefs, "flutter.tracked_apps")
        trackedPackages.clear()
        rememberedVolumes.clear()
        trackedPackages.addAll(trackedList)
        for (pkg in trackedList) {
            val vol = getRememberedVolume(pkg)
            if (vol != null) {
                rememberedVolumes[pkg] = vol
            }
        }
        logEvent("Loaded ${trackedPackages.size} tracked packages from prefs: $trackedPackages")
    }

    // ── BroadcastReceiver ─────────────────────────────────────────────────

    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_APP_CHANGED -> {
                    val newApp = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
                    onAppChanged(newApp)
                }
                ACTION_UPDATE_PACKAGES -> {
                    val pkgs = intent.getStringArrayListExtra(EXTRA_TRACKED_PACKAGES)
                        ?: return
                    logEvent("ACTION_UPDATE_PACKAGES: New list count: ${pkgs.size}")
                    trackedPackages.clear()
                    trackedPackages.addAll(pkgs)
                    // Sync rememberedVolumes with SharedPreferences for newly tracked packages
                    for (pkg in trackedPackages) {
                        if (!rememberedVolumes.containsKey(pkg)) {
                            val vol = getRememberedVolume(pkg)
                            if (vol != null) {
                                rememberedVolumes[pkg] = vol
                            }
                        }
                    }
                    // Clean up packages that are no longer tracked
                    rememberedVolumes.keys.retainAll(trackedPackages)
                }
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadTrackedPackagesFromPrefs()
        logEvent("Service onCreate")

        val filter = IntentFilter().apply {
            addAction(ACTION_APP_CHANGED)
            addAction(ACTION_UPDATE_PACKAGES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                appChangeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(appChangeReceiver, filter)
        }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pkgs = intent?.getStringArrayListExtra(EXTRA_TRACKED_PACKAGES)
        if (pkgs != null) {
            logEvent("onStartCommand: Tracked packages received: ${pkgs.size}")
            trackedPackages.clear()
            trackedPackages.addAll(pkgs)
            for (pkg in pkgs) {
                val vol = getRememberedVolume(pkg)
                if (vol != null) {
                    rememberedVolumes[pkg] = vol
                }
            }
        } else {
            logEvent("onStartCommand: Intent is null, loading packages from prefs")
            loadTrackedPackagesFromPrefs()
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        logEvent("Service onDestroy")
        unregisterReceiver(appChangeReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── App-change logic ──────────────────────────────────────────────────

    private fun onAppChanged(newPackageName: String) {
        val previous = currentForegroundApp
        logEvent("onAppChanged: $previous -> $newPackageName")

        // Save volume of the app that just moved to background.
        if (previous != null && trackedPackages.contains(previous)) {
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            rememberedVolumes[previous] = volume
            val sharedPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            sharedPrefs.edit().putLong("flutter.app_vol_$previous", volume.toLong()).apply()
            logEvent("Saved volume for $previous: $volume")
        }

        currentForegroundApp = newPackageName

        // Restore volume for the newly-focused app, if we have a memory for it.
        if (trackedPackages.contains(newPackageName)) {
            val savedVolume = rememberedVolumes[newPackageName] ?: getRememberedVolume(newPackageName)
            if (savedVolume != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                rememberedVolumes[newPackageName] = savedVolume
                logEvent("Restored volume for $newPackageName: $savedVolume")
            } else {
                logEvent("No saved volume found for tracked app $newPackageName")
            }
        } else {
            logEvent("New app $newPackageName is not tracked")
        }

        updateNotification()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (currentForegroundApp != null) {
            "Tracking — ${trackedPackages.size} app(s)"
        } else {
            "Monitoring — ${trackedPackages.size} app(s) tracked"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Volume Lock active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
