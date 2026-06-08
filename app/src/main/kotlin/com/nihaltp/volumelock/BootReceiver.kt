package com.nihaltp.volumelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and restarts the volume services
 * that were active before the device rebooted.
 *
 * This receiver reads flags directly from the native SharedPreferences
 * ("volume_lock_prefs"). The services are restarted with their previous
 * settings, and the AppVolumeLockService will load the tracked package list
 * from SharedPreferences on startup.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            "volume_lock_prefs",
            Context.MODE_PRIVATE
        )

        if (prefs.getBoolean("volume_lock_enabled", false)) {
            val svc = Intent(context, VolumeLockService::class.java)
            startServiceCompat(context, svc)
        }

        if (prefs.getBoolean("app_volume_lock_enabled", false)) {
            // Start without tracked packages — they will be re-supplied when
            // the user opens the app.
            val svc = Intent(context, AppVolumeLockService::class.java)
            startServiceCompat(context, svc)
        }
    }

    private fun startServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
