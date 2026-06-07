package com.nihaltp.volume_lock.ui.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.volume_lock.AppVolumeAccessibilityService
import com.nihaltp.volume_lock.AppVolumeLockService
import com.nihaltp.volume_lock.VolumeLockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppVolumeEntry(
    val packageName: String,
    val appName: String,
    val isTracked: Boolean,
    val rememberedMediaVolume: Int?
)

data class VolumeState(
    val media: Int = 0,
    val mediaMax: Int = 15,
    val ring: Int = 0,
    val ringMax: Int = 15,
    val notification: Int = 0,
    val notificationMax: Int = 15,
    val alarm: Int = 0,
    val alarmMax: Int = 15
)

class VolumeLockViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences("volume_lock_prefs", Context.MODE_PRIVATE)

    // UI States
    private val _volumeLockEnabled = MutableStateFlow(false)
    val volumeLockEnabled: StateFlow<Boolean> = _volumeLockEnabled.asStateFlow()

    private val _appVolumeLockEnabled = MutableStateFlow(false)
    val appVolumeLockEnabled: StateFlow<Boolean> = _appVolumeLockEnabled.asStateFlow()

    private val _loggingEnabled = MutableStateFlow(false)
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled.asStateFlow()

    private val _currentVolumes = MutableStateFlow(VolumeState())
    val currentVolumes: StateFlow<VolumeState> = _currentVolumes.asStateFlow()

    private val _lockedVolumes = MutableStateFlow<VolumeState?>(null)
    val lockedVolumes: StateFlow<VolumeState?> = _lockedVolumes.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppVolumeEntry>>(emptyList())
    val installedApps: StateFlow<List<AppVolumeEntry>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _accessibilityGranted = MutableStateFlow(false)
    val accessibilityGranted: StateFlow<Boolean> = _accessibilityGranted.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isLoadingLogs = MutableStateFlow(false)
    val isLoadingLogs: StateFlow<Boolean> = _isLoadingLogs.asStateFlow()

    init {
        // Load initial state
        _volumeLockEnabled.value = prefs.getBoolean("volume_lock_enabled", false)
        _appVolumeLockEnabled.value = prefs.getBoolean("app_volume_lock_enabled", false)
        _loggingEnabled.value = prefs.getBoolean("logging_enabled", false)

        updateCurrentVolumes()
        checkAccessibilityPermission()

        // Start volume polling
        viewModelScope.launch {
            while (true) {
                updateCurrentVolumes()
                delay(2000)
            }
        }
    }

    // ─── Volume queries ────────────────────────────────────────────────────────

    fun updateCurrentVolumes() {
        _currentVolumes.value = VolumeState(
            media = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            ring = audioManager.getStreamVolume(AudioManager.STREAM_RING),
            ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),
            notification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
            notificationMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            alarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
            alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        )
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    fun setLoggingEnabled(value: Boolean) {
        _loggingEnabled.value = value
        prefs.edit().putBoolean("logging_enabled", value).apply()
        logEvent("Logging ${if (value) "enabled" else "disabled"}")
    }

    fun logEvent(message: String) {
        android.util.Log.i("VolumeLock", "ViewModel: $message")
        if (!_loggingEnabled.value) return

        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val logLine = "$timestamp | ViewModel: $message"

            synchronized(this) {
                val currentLogs = getStringListFromPrefs("app_logs").toMutableList()
                currentLogs.add(logLine)

                if (currentLogs.size > 500) {
                    currentLogs.subList(0, currentLogs.size - 500).clear()
                }

                saveStringListToPrefs("app_logs", currentLogs)
            }
            loadLogs()
        }
    }

    fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingLogs.value = true
            val rawLogs = getStringListFromPrefs("app_logs").sorted()
            _logs.value = rawLogs
            _isLoadingLogs.value = false
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().remove("app_logs").apply()
            logEvent("Logs cleared")
            loadLogs()
        }
    }

    // ─── Volume Lock service control ─────────────────────────────────────────

    fun setVolumeLockEnabled(enabled: Boolean) {
        _volumeLockEnabled.value = enabled
        prefs.edit().putBoolean("volume_lock_enabled", enabled).apply()

        val intent = Intent(context, VolumeLockService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            logEvent("Volume Lock service start requested")
        } else {
            context.stopService(intent)
            _lockedVolumes.value = null
            logEvent("Volume Lock service stop requested")
        }
    }

    fun hasNotificationPolicyAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    // ─── App Volume Lock service control ─────────────────────────────────────

    fun setAppVolumeLockEnabled(enabled: Boolean) {
        _appVolumeLockEnabled.value = enabled
        prefs.edit().putBoolean("app_volume_lock_enabled", enabled).apply()

        val intent = Intent(context, AppVolumeLockService::class.java).apply {
            val tracked = getTrackedPackages()
            putStringArrayListExtra(
                AppVolumeLockService.EXTRA_TRACKED_PACKAGES,
                ArrayList(tracked)
            )
        }

        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            logEvent("App Volume Lock service start requested")
        } else {
            context.stopService(intent)
            logEvent("App Volume Lock service stopped")
        }
    }

    fun checkAccessibilityPermission() {
        val componentName = ComponentName(context, AppVolumeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        var enabled = false
        if (enabledServices != null) {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (ComponentName.unflattenFromString(splitter.next()) == componentName) {
                    enabled = true
                    break
                }
            }
        }
        _accessibilityGranted.value = enabled
    }

    // ─── Installed apps & tracking ───────────────────────────────────────────

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) { getInstalledAppsFromSystem() }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleAppTracking(packageName: String, tracked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val trackedList = getTrackedPackages().toMutableSet()
            if (tracked) {
                trackedList.add(packageName)
            } else {
                trackedList.remove(packageName)
            }
            saveStringListToPrefs("tracked_apps", trackedList.toList())
            logEvent("Toggle app tracking: $packageName -> $tracked")

            // Update running service
            if (_appVolumeLockEnabled.value) {
                val intent = Intent(AppVolumeLockService.ACTION_UPDATE_PACKAGES).apply {
                    `package` = context.packageName
                    putStringArrayListExtra(
                        AppVolumeLockService.EXTRA_TRACKED_PACKAGES,
                        ArrayList(trackedList.toList())
                    )
                }
                context.sendBroadcast(intent)
            }

            // Reload list to update UI
            val updatedList = _installedApps.value.map {
                if (it.packageName == packageName) {
                    it.copy(isTracked = tracked)
                } else {
                    it
                }
            }
            _installedApps.value = updatedList
        }
    }

    fun setAppTrackingForPackages(packageNames: List<String>, tracked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val trackedList = getTrackedPackages().toMutableSet()
            if (tracked) {
                trackedList.addAll(packageNames)
            } else {
                trackedList.removeAll(packageNames.toSet())
            }
            saveStringListToPrefs("tracked_apps", trackedList.toList())
            logEvent("Set app tracking for batch: tracked -> $tracked")

            // Update running service
            if (_appVolumeLockEnabled.value) {
                val intent = Intent(AppVolumeLockService.ACTION_UPDATE_PACKAGES).apply {
                    `package` = context.packageName
                    putStringArrayListExtra(
                        AppVolumeLockService.EXTRA_TRACKED_PACKAGES,
                        ArrayList(trackedList.toList())
                    )
                }
                context.sendBroadcast(intent)
            }

            // Reload list to update UI
            val updatedList = _installedApps.value.map {
                if (packageNames.contains(it.packageName)) {
                    it.copy(isTracked = tracked)
                } else {
                    it
                }
            }
            _installedApps.value = updatedList
        }
    }

    fun updateAppVolume(packageName: String, volume: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putInt("app_vol_$packageName", volume).apply()
            logEvent("Update app volume: $packageName -> $volume")

            val updatedList = _installedApps.value.map {
                if (it.packageName == packageName) {
                    it.copy(rememberedMediaVolume = volume)
                } else {
                    it
                }
            }
            _installedApps.value = updatedList
        }
    }

    // ─── Helper methods for SharedPreferences & App Queries ──────────────────

    private fun getTrackedPackages(): List<String> {
        return getStringListFromPrefs("tracked_apps")
    }

    private fun getStringListFromPrefs(key: String): List<String> {
        val list = mutableListOf<String>()
        val jsonStr = prefs.getString(key, null) ?: return list
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Failed to parse list JSON for key $key: ${e.message}")
        }
        return list
    }

    private fun saveStringListToPrefs(key: String, list: List<String>) {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun getRememberedVolume(packageName: String): Int? {
        val key = "app_vol_$packageName"
        if (!prefs.contains(key)) return null
        val vol = prefs.getInt(key, -1)
        return if (vol >= 0) vol else null
    }

    private fun getInstalledAppsFromSystem(): List<AppVolumeEntry> {
        val pm = context.packageManager
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
            .filter { it != context.packageName }
            .toMutableSet()

        @Suppress("DEPRECATION")
        val installedApplications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

        val launchableFromInstalled = installedApplications.filter { app ->
            app.packageName != context.packageName && pm.getLaunchIntentForPackage(app.packageName) != null
        }.map { it.packageName }

        val candidatePackages = launcherPackages.apply { addAll(launchableFromInstalled) }
        val trackedSet = getTrackedPackages().toSet()

        return candidatePackages
            .asSequence()
            .map { pkg ->
                val label: String = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg
                }

                AppVolumeEntry(
                    packageName = pkg,
                    appName = label,
                    isTracked = trackedSet.contains(pkg),
                    rememberedMediaVolume = getRememberedVolume(pkg)
                )
            }
            .sortedBy { it.appName.lowercase(Locale.getDefault()) }
            .toList()
    }
}
