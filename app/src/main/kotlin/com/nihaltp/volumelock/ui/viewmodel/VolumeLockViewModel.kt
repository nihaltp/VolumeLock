package com.nihaltp.volumelock.ui.viewmodel

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
import com.nihaltp.volumelock.AppVolumeAccessibilityService
import com.nihaltp.volumelock.AppVolumeLockService
import com.nihaltp.volumelock.MediaSessionHelper
import com.nihaltp.volumelock.VolumeConfig
import com.nihaltp.volumelock.VolumeLockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
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

    internal var isTesting = false

    // UI States
    internal val _volumeLockEnabled = MutableStateFlow(false)
    val volumeLockEnabled: StateFlow<Boolean> = _volumeLockEnabled.asStateFlow()

    internal val _appVolumeLockEnabled = MutableStateFlow(false)
    val appVolumeLockEnabled: StateFlow<Boolean> = _appVolumeLockEnabled.asStateFlow()

    internal val _backgroundAwareEnabled = MutableStateFlow(false)
    val backgroundAwareEnabled: StateFlow<Boolean> = _backgroundAwareEnabled.asStateFlow()

    internal val _loggingEnabled = MutableStateFlow(false)
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled.asStateFlow()

    internal val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    internal val _materialYouEnabled = MutableStateFlow(true)
    val materialYouEnabled: StateFlow<Boolean> = _materialYouEnabled.asStateFlow()

    internal val _currentVolumes = MutableStateFlow(VolumeState())
    val currentVolumes: StateFlow<VolumeState> = _currentVolumes.asStateFlow()

    internal val _lockedVolumes = MutableStateFlow<VolumeState?>(null)
    val lockedVolumes: StateFlow<VolumeState?> = _lockedVolumes.asStateFlow()

    internal val _installedApps = MutableStateFlow<List<AppVolumeEntry>>(emptyList())
    val installedApps: StateFlow<List<AppVolumeEntry>> = _installedApps.asStateFlow()

    internal val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    internal val _accessibilityGranted = MutableStateFlow(false)
    val accessibilityGranted: StateFlow<Boolean> = _accessibilityGranted.asStateFlow()

    internal val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    internal val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    internal val _isLoadingLogs = MutableStateFlow(false)
    val isLoadingLogs: StateFlow<Boolean> = _isLoadingLogs.asStateFlow()

    init {
        // Load initial state
        _volumeLockEnabled.value = prefs.getBoolean("volume_lock_enabled", false)
        _appVolumeLockEnabled.value = prefs.getBoolean("app_volume_lock_enabled", false)
        _backgroundAwareEnabled.value = prefs.getBoolean("background_aware_enabled", false)
        _loggingEnabled.value = prefs.getBoolean("logging_enabled", false)
        _themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
        _materialYouEnabled.value = prefs.getBoolean("material_you_enabled", true)

        if (!isTesting) {
            val cachedApps = getInstalledAppsFromPrefs()
            if (cachedApps.isNotEmpty()) {
                _installedApps.value = cachedApps
            }
        }

        updateCurrentVolumes()
        checkAccessibilityPermission()
        checkNotificationAccessPermission()

        // Start volume polling
        viewModelScope.launch {
            while (true) {
                if (!isTesting) {
                    updateCurrentVolumes()
                }
                delay(2000)
            }
        }
    }

    // ─── Volume queries ────────────────────────────────────────────────────────

    fun updateCurrentVolumes() {
        if (isTesting) return
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

    // ─── Theme helper ────────────────────────────────────────────────────────

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
        logEvent("Theme changed to $mode")
    }

    fun setMaterialYouEnabled(enabled: Boolean) {
        _materialYouEnabled.value = enabled
        prefs.edit().putBoolean("material_you_enabled", enabled).apply()
        logEvent("Material You toggled: $enabled")
    }

    // ─── Logging helpers ──────────────────────────────────────────────────────

    fun setBackgroundAwareEnabled(enabled: Boolean) {
        _backgroundAwareEnabled.value = enabled
        prefs.edit().putBoolean("background_aware_enabled", enabled).apply()
        logEvent("Background Aware App Volume toggled: $enabled")
    }

    fun checkNotificationAccessPermission() {
        if (isTesting) return
        _notificationAccessGranted.value = MediaSessionHelper.isNotificationAccessGranted(context)
    }

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
        if (isTesting) return
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
        if (isTesting) return
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
        if (isTesting) return
        viewModelScope.launch {
            _isLoadingApps.value = true

            // 1. Get candidate package names first (fast)
            val candidatePackages = withContext(Dispatchers.IO) {
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

                launcherPackages.apply { addAll(launchableFromInstalled) }.toList()
            }

            val trackedSet = withContext(Dispatchers.IO) { getTrackedPackages().toSet() }

            // 2. Progressively resolve details for each candidate package
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val currentList = _installedApps.value.toMutableList()
                val resolvedPackages = mutableSetOf<String>()

                for (pkg in candidatePackages) {
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

                    val entry = AppVolumeEntry(
                        packageName = pkg,
                        appName = label,
                        isTracked = trackedSet.contains(pkg),
                        rememberedMediaVolume = getVolumeConfig(pkg).defaultVolume
                    )

                    resolvedPackages.add(pkg)

                    // Find and update, or add
                    val index = currentList.indexOfFirst { it.packageName == pkg }
                    if (index >= 0) {
                        currentList[index] = entry
                    } else {
                        currentList.add(entry)
                    }

                    // Keep sorted for clean progressive rendering
                    val sortedList = currentList.sortedBy { it.appName.lowercase(Locale.getDefault()) }
                    _installedApps.value = sortedList
                    yield()
                }

                // Remove any apps that are no longer installed
                val finalFilteredList = _installedApps.value.filter { resolvedPackages.contains(it.packageName) }
                _installedApps.value = finalFilteredList

                // Save final updated list to preferences
                saveInstalledAppsToPrefs(finalFilteredList)
            }

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
            saveInstalledAppsToPrefs(updatedList)
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
            saveInstalledAppsToPrefs(updatedList)
        }
    }

    fun updateAppVolume(packageName: String, volume: Int) {
        updateVolumeForAppPair(packageName, null, volume)
    }

    fun getVolumeConfig(foregroundPackage: String): VolumeConfig {
        val jsonStr = prefs.getString("app_volume_config_$foregroundPackage", null)
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

    fun saveVolumeConfig(foregroundPackage: String, config: VolumeConfig) {
        try {
            val json = org.json.JSONObject()
            json.put("defaultVolume", config.defaultVolume)
            val pairingsJson = org.json.JSONObject()
            for ((pkg, vol) in config.pairings) {
                pairingsJson.put(pkg, vol)
            }
            json.put("pairings", pairingsJson)
            prefs.edit().putString("app_volume_config_$foregroundPackage", json.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Error saving VolumeConfig: ${e.message}")
        }
    }

    fun updateVolumeForAppPair(foregroundPackage: String, backgroundPackage: String?, volume: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = getVolumeConfig(foregroundPackage)
            val newConfig = if (backgroundPackage == null || backgroundPackage == foregroundPackage) {
                currentConfig.copy(defaultVolume = volume)
            } else {
                val newPairings = currentConfig.pairings.toMutableMap()
                newPairings[backgroundPackage] = volume
                currentConfig.copy(pairings = newPairings)
            }
            saveVolumeConfig(foregroundPackage, newConfig)
            logEvent("Updated pair volume: fg=$foregroundPackage, bg=$backgroundPackage, vol=$volume")

            // If we changed defaultVolume, we also need to update _installedApps list
            if (backgroundPackage == null || backgroundPackage == foregroundPackage) {
                val updatedList = _installedApps.value.map {
                    if (it.packageName == foregroundPackage) {
                        it.copy(rememberedMediaVolume = volume)
                    } else {
                        it
                    }
                }
                _installedApps.value = updatedList
                saveInstalledAppsToPrefs(updatedList)
            }
        }
    }

    fun deleteVolumeForAppPair(foregroundPackage: String, backgroundPackage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = getVolumeConfig(foregroundPackage)
            val newPairings = currentConfig.pairings.toMutableMap()
            newPairings.remove(backgroundPackage)
            val newConfig = currentConfig.copy(pairings = newPairings)
            saveVolumeConfig(foregroundPackage, newConfig)
            logEvent("Deleted pair volume: fg=$foregroundPackage, bg=$backgroundPackage")
        }
    }

    // ─── Helper methods for SharedPreferences & App Queries ──────────────────

    private fun saveInstalledAppsToPrefs(apps: List<AppVolumeEntry>) {
        val jsonArray = JSONArray()
        for (app in apps) {
            val jsonObject = JSONObject()
            jsonObject.put("packageName", app.packageName)
            jsonObject.put("appName", app.appName)
            jsonObject.put("isTracked", app.isTracked)
            if (app.rememberedMediaVolume != null) {
                jsonObject.put("rememberedMediaVolume", app.rememberedMediaVolume)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString("cached_installed_apps", jsonArray.toString()).apply()
    }

    private fun getInstalledAppsFromPrefs(): List<AppVolumeEntry> {
        val list = mutableListOf<AppVolumeEntry>()
        val jsonStr = prefs.getString("cached_installed_apps", null) ?: return list
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val packageName = jsonObject.getString("packageName")
                val appName = jsonObject.getString("appName")
                val isTracked = jsonObject.getBoolean("isTracked")
                val rememberedMediaVolume = if (jsonObject.has("rememberedMediaVolume")) {
                    jsonObject.getInt("rememberedMediaVolume")
                } else {
                    null
                }
                list.add(AppVolumeEntry(packageName, appName, isTracked, rememberedMediaVolume))
            }
        } catch (e: Exception) {
            android.util.Log.e("VolumeLock", "Failed to parse cached apps JSON: ${e.message}")
        }
        return list
    }

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
}
