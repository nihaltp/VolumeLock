package com.nihaltp.volumelock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
        const val ACTION_APP_CHANGED = "com.nihaltp.volumelock.ACTION_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "packageName"

        /** Broadcast action sent by [MainActivity] to update tracked packages. */
        const val ACTION_UPDATE_PACKAGES = "com.nihaltp.volumelock.ACTION_UPDATE_PACKAGES"
        const val EXTRA_TRACKED_PACKAGES = "trackedPackages"
    }

    private lateinit var audioManager: AudioManager

    private val trackedPackages = mutableSetOf<String>()
    private var currentForegroundApp: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastRestoredVolume = -1

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val foreground = currentForegroundApp ?: return
            if (!trackedPackages.contains(foreground)) return

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume != lastRestoredVolume) {
                logEvent("Volume changed explicitly by user: $lastRestoredVolume -> $currentVolume (Foreground: $foreground)")
                lastRestoredVolume = currentVolume
                saveVolumeForApp(foreground, currentVolume)
            }
        }
    }

    // ── Helper methods for SharedPreferences & Logging ─────────────────────

    private fun getStringListFromPrefs(sharedPrefs: SharedPreferences, key: String): List<String> {
        val list = mutableListOf<String>()
        val jsonStr = sharedPrefs.getString(key, null) ?: return list
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Failed to parse list JSON for key $key: ${e.message}")
        }
        return list
    }

    private fun saveStringListToPrefs(sharedPrefs: SharedPreferences, key: String, list: List<String>) {
        val jsonArray = org.json.JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        sharedPrefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun logEvent(message: String) {
        android.util.Log.i("VolumeLock", "AppVolumeLockService: $message")
        val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("logging_enabled", false)) return

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val logLine = "$timestamp | AppVolumeLockService: $message"

        synchronized(this) {
            val currentLogs = getStringListFromPrefs(sharedPrefs, "app_logs").toMutableList()
            currentLogs.add(logLine)

            if (currentLogs.size > 500) {
                currentLogs.subList(0, currentLogs.size - 500).clear()
            }

            saveStringListToPrefs(sharedPrefs, "app_logs", currentLogs)
        }
    }

    private fun getVolumeConfig(packageName: String): VolumeConfig {
        val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("app_volume_config_$packageName", null)
        if (jsonStr != null) {
            try {
                val json = org.json.JSONObject(jsonStr)
                val defaultVolume = json.optInt("defaultVolume", 8)
                val pairingsJson = json.optJSONObject("pairings")
                val pairings = mutableMapOf<String, Int>()
                if (pairingsJson != null) {
                    val keys = pairingsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        pairings[key] = pairingsJson.getInt(key)
                    }
                }
                return VolumeConfig(defaultVolume, pairings)
            } catch (e: Exception) {
                android.util.Log.e("VolumeLock", "Error parsing VolumeConfig: ${e.message}")
            }
        }
        return VolumeConfig(8, emptyMap())
    }

    private fun saveVolumeConfig(packageName: String, config: VolumeConfig) {
        try {
            val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
            val json = org.json.JSONObject()
            json.put("defaultVolume", config.defaultVolume)
            val pairingsJson = org.json.JSONObject()
            for ((pkg, vol) in config.pairings) {
                pairingsJson.put(pkg, vol)
            }
            json.put("pairings", pairingsJson)
            sharedPrefs.edit().putString("app_volume_config_$packageName", json.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Error saving VolumeConfig: ${e.message}")
        }
    }

    private fun saveVolumeForApp(foreground: String, volume: Int) {
        val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
        val bgEnabled = sharedPrefs.getBoolean("background_aware_enabled", false)
        val currentConfig = getVolumeConfig(foreground)

        val newConfig = if (bgEnabled) {
            val bgPlayer = MediaSessionHelper.getActiveBackgroundPlayer(this, foreground)
            if (bgPlayer != null && bgPlayer != foreground) {
                val newPairings = currentConfig.pairings.toMutableMap()
                newPairings[bgPlayer] = volume
                currentConfig.copy(pairings = newPairings)
            } else {
                currentConfig.copy(defaultVolume = volume)
            }
        } else {
            currentConfig.copy(defaultVolume = volume)
        }

        saveVolumeConfig(foreground, newConfig)
        logEvent("Saved VolumeConfig for $foreground: $newConfig")
    }

    private fun loadTrackedPackagesFromPrefs() {
        val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
        val trackedList = getStringListFromPrefs(sharedPrefs, "tracked_apps")
        trackedPackages.clear()
        trackedPackages.addAll(trackedList)
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

        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )

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
        contentResolver.unregisterContentObserver(volumeObserver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── App-change logic ──────────────────────────────────────────────────

    private fun onAppChanged(newPackageName: String) {
        val previous = currentForegroundApp
        logEvent("onAppChanged: $previous -> $newPackageName")

        currentForegroundApp = newPackageName

        // Restore volume for the newly-focused app, if we have a memory for it.
        if (trackedPackages.contains(newPackageName)) {
            val sharedPrefs = getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)
            val bgEnabled = sharedPrefs.getBoolean("background_aware_enabled", false)
            val config = getVolumeConfig(newPackageName)

            val volumeToRestore = if (bgEnabled) {
                val bgPlayer = MediaSessionHelper.getActiveBackgroundPlayer(this, newPackageName)
                if (bgPlayer != null && bgPlayer != newPackageName && config.pairings.containsKey(bgPlayer)) {
                    logEvent("Found background pairing volume for $newPackageName with bg $bgPlayer")
                    config.pairings[bgPlayer] ?: config.defaultVolume
                } else {
                    logEvent("No background pairing or same app, restoring default volume for $newPackageName")
                    config.defaultVolume
                }
            } else {
                config.defaultVolume
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0)
            lastRestoredVolume = volumeToRestore
            logEvent("Restored volume for $newPackageName: $volumeToRestore")
        } else {
            logEvent("New app $newPackageName is not tracked")
        }

        updateNotification()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
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
