package com.example.volume_lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and restarts the volume services
 * that were active before the device rebooted.
 *
 * Flutter's shared_preferences plugin stores values under the
 * "FlutterSharedPreferences" preference file with a "flutter." key prefix.
 * We only need to read boolean flags here (simple to decode).
 *
 * Tracked app lists are re-supplied by the Flutter side the next time the
 * user opens the app, so services are started with an empty list on boot and
 * fill in the packages once the UI is loaded.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            "FlutterSharedPreferences", Context.MODE_PRIVATE
        )

        if (prefs.getBoolean("flutter.volume_lock_enabled", false)) {
            val svc = Intent(context, VolumeLockService::class.java)
            startServiceCompat(context, svc)
        }

        if (prefs.getBoolean("flutter.app_volume_lock_enabled", false)) {
            // Start without tracked packages — they will be re-supplied when
            // the user opens the app and Flutter calls updateTrackedApps.
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
