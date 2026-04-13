package com.nihaltp.volume_lock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

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

        val filter = IntentFilter().apply {
            addAction(ACTION_APP_CHANGED)
            addAction(ACTION_UPDATE_PACKAGES)
        }
        registerReceiver(appChangeReceiver, filter)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pkgs = intent?.getStringArrayListExtra(EXTRA_TRACKED_PACKAGES)
        if (pkgs != null) {
            trackedPackages.clear()
            trackedPackages.addAll(pkgs)
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(appChangeReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── App-change logic ──────────────────────────────────────────────────

    private fun onAppChanged(newPackageName: String) {
        val previous = currentForegroundApp

        // Save volume of the app that just moved to background.
        if (previous != null && trackedPackages.contains(previous)) {
            rememberedVolumes[previous] =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        currentForegroundApp = newPackageName

        // Restore volume for the newly-focused app, if we have a memory for it.
        if (trackedPackages.contains(newPackageName)) {
            val savedVolume = rememberedVolumes[newPackageName]
            if (savedVolume != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            }
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
