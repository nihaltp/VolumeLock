package com.nihaltp.volume_lock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * Foreground service that implements the "Volume Lock" feature.
 *
 * Behaviour:
 *  - When the screen turns OFF the current media, ring, notification and
 *    alarm volumes are captured as the "locked" snapshot.
 *  - While the screen remains off any change to those streams is immediately
 *    reverted to the snapshot values.
 *  - When the screen turns ON (user present / unlock) the snapshot is cleared
 *    and changes are no longer reverted.
 */
class VolumeLockService : Service() {

    companion object {
        const val CHANNEL_ID = "volume_lock_service"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    // Volumes captured at screen-off time.
    private var lockedMedia: Int = -1
    private var lockedRing: Int = -1
    private var lockedNotification: Int = -1
    private var lockedAlarm: Int = -1

    private var isScreenOff = false

    // ── Content observers for each stream ─────────────────────────────────

    private val mediaObserver = volumeObserver { revertIfLocked(AudioManager.STREAM_MUSIC) }
    private val ringObserver = volumeObserver { revertIfLocked(AudioManager.STREAM_RING) }
    private val notificationObserver = volumeObserver { revertIfLocked(AudioManager.STREAM_NOTIFICATION) }
    private val alarmObserver = volumeObserver { revertIfLocked(AudioManager.STREAM_ALARM) }

    // ── Screen state receiver ─────────────────────────────────────────────

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    captureVolumes()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Keep lock values until the user actually unlocks.
                    // Some devices allow volume changes around wake events.
                    isScreenOff = false
                    updateNotification()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isScreenOff = false
                    restoreAllLockedVolumes()
                    clearLock()
                }
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        registerScreenReceiver()
        registerVolumeObservers()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the notification stays up to date if called again.
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        unregisterVolumeObservers()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Volume management ─────────────────────────────────────────────────

    private fun captureVolumes() {
        lockedMedia        = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        lockedRing         = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        lockedNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        lockedAlarm        = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        updateNotification()
    }

    private fun clearLock() {
        lockedMedia = -1
        lockedRing = -1
        lockedNotification = -1
        lockedAlarm = -1
        updateNotification()
    }

    private fun restoreAllLockedVolumes() {
        restoreStreamIfNeeded(AudioManager.STREAM_MUSIC, lockedMedia)
        restoreStreamIfNeeded(AudioManager.STREAM_RING, lockedRing)
        restoreStreamIfNeeded(AudioManager.STREAM_NOTIFICATION, lockedNotification)
        restoreStreamIfNeeded(AudioManager.STREAM_ALARM, lockedAlarm)
    }

    private fun restoreStreamIfNeeded(stream: Int, target: Int) {
        if (target < 0) return
        val current = audioManager.getStreamVolume(stream)
        if (current != target) {
            audioManager.setStreamVolume(stream, target, 0)
        }
    }

    private fun revertIfLocked(stream: Int) {
        if (!shouldEnforceLock()) return
        val target = when (stream) {
            AudioManager.STREAM_MUSIC        -> lockedMedia
            AudioManager.STREAM_RING         -> lockedRing
            AudioManager.STREAM_NOTIFICATION -> lockedNotification
            AudioManager.STREAM_ALARM        -> lockedAlarm
            else                             -> -1
        }
        if (target < 0) return
        val current = audioManager.getStreamVolume(stream)
        if (current != target) {
            // Post on main thread to avoid ContentObserver re-entrancy issues.
            handler.post {
                audioManager.setStreamVolume(stream, target, 0)
            }
        }
    }

    private fun shouldEnforceLock(): Boolean {
        if (lockedMedia < 0 && lockedRing < 0 && lockedNotification < 0 && lockedAlarm < 0) {
            return false
        }
        // Enforce while the display is off OR while keyguard is still locked.
        return isScreenOff || isDeviceLocked()
    }

    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceLocked
        } else {
            km.isKeyguardLocked
        }
    }

    // ── Registration helpers ──────────────────────────────────────────────

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun registerVolumeObservers() {
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, mediaObserver)
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, ringObserver)
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, notificationObserver)
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, alarmObserver)
    }

    private fun unregisterVolumeObservers() {
        contentResolver.unregisterContentObserver(mediaObserver)
        contentResolver.unregisterContentObserver(ringObserver)
        contentResolver.unregisterContentObserver(notificationObserver)
        contentResolver.unregisterContentObserver(alarmObserver)
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (shouldEnforceLock()) {
            "Locked — Media: $lockedMedia | Ring: $lockedRing | " +
                    "Notification: $lockedNotification | Alarm: $lockedAlarm"
        } else {
            "Monitoring volume — screen is on"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume Lock active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    // ── ContentObserver factory ───────────────────────────────────────────

    private fun volumeObserver(onChange: () -> Unit): ContentObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
        }
}
