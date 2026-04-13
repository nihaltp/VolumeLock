package com.example.volume_lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val volumeChannel = "com.example.volume_lock/volume"
    private val appsChannel  = "com.example.volume_lock/apps"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        createNotificationChannels()
        setupVolumeChannel(flutterEngine)
        setupAppsChannel(flutterEngine)
    }

    // ── Notification channels (required for foreground services on API 26+) ──

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    VolumeLockService.CHANNEL_ID,
                    "Volume Lock",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Volume Lock foreground service" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    AppVolumeLockService.CHANNEL_ID,
                    "App Volume Lock",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "App Volume Lock foreground service" }
            )
        }
    }

    // ── Volume / service channel ───────────────────────────────────────────

    private fun setupVolumeChannel(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            volumeChannel
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "startVolumeLockService" -> {
                    startForegroundServiceCompat<VolumeLockService>()
                    result.success(true)
                }
                "stopVolumeLockService" -> {
                    stopService(Intent(this, VolumeLockService::class.java))
                    result.success(true)
                }
                "startAppVolumeLockService" -> {
                    @Suppress("UNCHECKED_CAST")
                    val pkgs = call.argument<List<String>>("trackedPackages") ?: emptyList()
                    val intent = Intent(this, AppVolumeLockService::class.java).apply {
                        putStringArrayListExtra(
                            AppVolumeLockService.EXTRA_TRACKED_PACKAGES,
                            ArrayList(pkgs)
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success(true)
                }
                "stopAppVolumeLockService" -> {
                    stopService(Intent(this, AppVolumeLockService::class.java))
                    result.success(true)
                }
                "updateTrackedApps" -> {
                    @Suppress("UNCHECKED_CAST")
                    val pkgs = call.argument<List<String>>("trackedPackages") ?: emptyList()
                    val intent = Intent(AppVolumeLockService.ACTION_UPDATE_PACKAGES).apply {
                        `package` = packageName
                        putStringArrayListExtra(
                            AppVolumeLockService.EXTRA_TRACKED_PACKAGES,
                            ArrayList(pkgs)
                        )
                    }
                    sendBroadcast(intent)
                    result.success(null)
                }
                "getCurrentVolumes" -> {
                    result.success(getCurrentVolumes())
                }
                "isAccessibilityServiceEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                "hasNotificationPolicyAccess" -> {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    result.success(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            nm.isNotificationPolicyAccessGranted
                        } else {
                            true
                        }
                    )
                }
                "openNotificationPolicySettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    // ── Apps channel ──────────────────────────────────────────────────────

    private fun setupAppsChannel(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            appsChannel
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getInstalledApps" -> result.success(getInstalledApps())
                else               -> result.notImplemented()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private inline fun <reified T : android.app.Service> startForegroundServiceCompat() {
        val intent = Intent(this, T::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun getCurrentVolumes(): Map<String, Int> {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mapOf(
            "media"           to am.getStreamVolume(AudioManager.STREAM_MUSIC),
            "mediaMax"        to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            "ring"            to am.getStreamVolume(AudioManager.STREAM_RING),
            "ringMax"         to am.getStreamMaxVolume(AudioManager.STREAM_RING),
            "notification"    to am.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
            "notificationMax" to am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            "alarm"           to am.getStreamVolume(AudioManager.STREAM_ALARM),
            "alarmMax"        to am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val componentName = ComponentName(this, AppVolumeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == componentName) return true
        }
        return false
    }

    private fun getInstalledApps(): List<Map<String, String>> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return resolveInfoList
            .asSequence()
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val label = try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationLabel(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            pm.getApplicationInfo(pkg, 0)
                        }
                    ).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                }
                mapOf("packageName" to pkg, "appName" to label)
            }
            .distinctBy { it["packageName"] }
            .sortedBy { it["appName"] }
            .toList()
    }
}
