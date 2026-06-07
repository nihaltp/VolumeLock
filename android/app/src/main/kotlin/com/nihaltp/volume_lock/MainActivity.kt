package com.nihaltp.volume_lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {

    private val volumeChannel = "com.nihaltp.volume_lock/volume"
    private val appsChannel  = "com.nihaltp.volume_lock/apps"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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
                "getInstalledApps" -> {
                    executor.execute {
                        try {
                            val apps = getInstalledApps()
                            mainHandler.post {
                                result.success(apps)
                            }
                        } catch (e: Exception) {
                            mainHandler.post {
                                result.error("UNEXPECTED_ERROR", e.message, null)
                            }
                        }
                    }
                }
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

        // Prefer launcher-intent resolution because it aligns with what users
        // can actually open and is more reliable across OEM package visibility
        // behavior.
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val launcherMatches: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }
        val launcherPackages = launcherMatches
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName }
            .toMutableSet()

        @Suppress("DEPRECATION")
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

        // Keep fallback union to capture apps some OEMs may not return through
        // launcher query, while still filtering to launchable targets.
        val launchableFromInstalled = installedApps.filter { app ->
            app.packageName != packageName && pm.getLaunchIntentForPackage(app.packageName) != null
        }.map { it.packageName }

        val candidatePackages = launcherPackages
            .apply { addAll(launchableFromInstalled) }

        return candidatePackages
            .asSequence()
            .map { pkg ->
                val label: String = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(
                            pkg,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                }

                // Icon: try BitmapDrawable direct, else draw to bitmap, use 64px
                val iconBase64: String = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(
                            pkg,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    val drawable = pm.getApplicationIcon(appInfo)
                    val size = 64
                    val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, size, size)
                        drawable.draw(canvas)
                        bmp
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    val bytes = stream.toByteArray()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (_: Exception) {
                    ""
                }

                mapOf(
                    "packageName" to pkg,
                    "appName" to label,
                    "icon" to iconBase64
                )
            }
            .sortedBy { it["appName"]?.lowercase() }
            .toList()
    }
}
