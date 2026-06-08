package com.nihaltp.volumelock

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

object MediaSessionHelper {

    /**
     * Checks if the Notification Listener permission has been granted to this app.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packages.contains(context.packageName)
    }

    /**
     * Finds the package name of the active background player that is currently playing audio.
     * Searches through active sessions in OS-defined priority order and selects the first session
     * that is actively playing (state == STATE_PLAYING) and is not the foreground app.
     */
    fun getActiveBackgroundPlayer(context: Context, foregroundPackage: String): String? {
        if (!isNotificationAccessGranted(context)) return null
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
        val listenerComponent = ComponentName(context, VolumeLockNotificationListenerService::class.java)

        return try {
            val controllers = manager.getActiveSessions(listenerComponent)
            for (controller in controllers) {
                val pkg = controller.packageName
                if (pkg == foregroundPackage) continue

                val state = controller.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    return pkg
                }
            }
            null
        } catch (e: SecurityException) {
            android.util.Log.w("VolumeLock", "SecurityException reading active sessions: ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Error reading active sessions: ${e.message}")
            null
        }
    }
}
