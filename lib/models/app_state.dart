import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:volume_lock/services/volume_service.dart';

/// Represents the volume levels for all stream types.
class VolumeSnapshot {
  const VolumeSnapshot({
    required this.media,
    required this.ring,
    required this.notification,
    required this.alarm,
  });

  factory VolumeSnapshot.fromMap(Map<String, int> map) => VolumeSnapshot(
        media: map['media'] ?? 0,
        ring: map['ring'] ?? 0,
        notification: map['notification'] ?? 0,
        alarm: map['alarm'] ?? 0,
      );
  final int media;
  final int ring;
  final int notification;
  final int alarm;

  Map<String, int> toMap() => {
        'media': media,
        'ring': ring,
        'notification': notification,
        'alarm': alarm,
      };
}

/// Per-app volume memory entry.
class AppVolumeEntry {
  AppVolumeEntry({
    required this.packageName,
    required this.appName,
    this.iconBytes,
    this.rememberedMediaVolume,
    this.isTracked = false,
  });
  final String packageName;
  final String appName;
  final Uint8List? iconBytes;
  int? rememberedMediaVolume;
  bool isTracked;
}

/// Central application state managed via ChangeNotifier / Provider.
class AppState extends ChangeNotifier {
  AppState() {
    _loadPrefs();
  }
  bool _volumeLockEnabled = false;
  bool _appVolumeLockEnabled = false;
  bool _loggingEnabled = false;

  VolumeSnapshot? _lockedVolumes;
  final Map<String, AppVolumeEntry> _appEntries = {};
  List<AppVolumeEntry> _installedApps = [];

  bool get volumeLockEnabled => _volumeLockEnabled;
  bool get appVolumeLockEnabled => _appVolumeLockEnabled;
  bool get loggingEnabled => _loggingEnabled;
  VolumeSnapshot? get lockedVolumes => _lockedVolumes;
  List<AppVolumeEntry> get installedApps => List.unmodifiable(_installedApps);
  Map<String, AppVolumeEntry> get appEntries => Map.unmodifiable(_appEntries);

  // ─── Persistence ──────────────────────────────────────────────────────────

  Future<void> _loadPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    _volumeLockEnabled = prefs.getBool('volume_lock_enabled') ?? false;
    _appVolumeLockEnabled = prefs.getBool('app_volume_lock_enabled') ?? false;
    _loggingEnabled = prefs.getBool('logging_enabled') ?? false;

    final tracked = prefs.getStringList('tracked_apps') ?? [];
    for (final pkg in tracked) {
      _appEntries[pkg] = AppVolumeEntry(
        packageName: pkg,
        appName: prefs.getString('app_name_$pkg') ?? pkg,
        rememberedMediaVolume: prefs.getInt('app_vol_$pkg'),
        isTracked: true,
      );
    }

    notifyListeners();
  }

  Future<void> _savePrefs() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('volume_lock_enabled', _volumeLockEnabled);
    await prefs.setBool('app_volume_lock_enabled', _appVolumeLockEnabled);
    await prefs.setBool('logging_enabled', _loggingEnabled);

    final tracked = _appEntries.values
        .where((e) => e.isTracked)
        .map((e) => e.packageName)
        .toList();
    await prefs.setStringList('tracked_apps', tracked);
    for (final entry in _appEntries.values) {
      await prefs.setString('app_name_${entry.packageName}', entry.appName);
      if (entry.rememberedMediaVolume != null) {
        await prefs.setInt(
          'app_vol_${entry.packageName}',
          entry.rememberedMediaVolume!,
        );
      }
    }
  }

  // ─── Logging helpers ──────────────────────────────────────────────────────

  Future<void> setLoggingEnabled(bool value) async {
    _loggingEnabled = value;
    await _savePrefs();
    notifyListeners();
    await logEvent('Logging ${value ? "enabled" : "disabled"}');
  }

  Future<void> logEvent(String message) async {
    debugPrint('VolumeLock: $message');
    if (!_loggingEnabled) return;

    final prefs = await SharedPreferences.getInstance();
    final timestamp = DateTime.now().toIso8601String().replaceAll('T', ' ').substring(0, 23);
    final logLine = "$timestamp | $message";

    final currentLogs = prefs.getStringList('app_logs') ?? [];
    currentLogs.add(logLine);

    if (currentLogs.length > 500) {
      currentLogs.removeRange(0, currentLogs.length - 500);
    }

    await prefs.setStringList('app_logs', currentLogs);
  }

  Future<List<String>> getLogs() async {
    final prefs = await SharedPreferences.getInstance();
    final rawLogs = prefs.getStringList('app_logs') ?? [];
    rawLogs.sort();
    return rawLogs;
  }

  Future<void> clearLogs() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('app_logs');
    await logEvent('Logs cleared');
    notifyListeners();
  }

  // ─── Volume Lock ──────────────────────────────────────────────────────────

  Future<void> setVolumeLockEnabled(bool value) async {
    _volumeLockEnabled = value;
    if (value) {
      await VolumeService.startVolumeLockService();
      await logEvent('Volume Lock service start requested');
    } else {
      await VolumeService.stopVolumeLockService();
      _lockedVolumes = null;
      await logEvent('Volume Lock service stop requested');
    }
    await _savePrefs();
    notifyListeners();
  }

  void updateLockedVolumes(VolumeSnapshot snapshot) {
    _lockedVolumes = snapshot;
    notifyListeners();
  }

  // ─── App Volume Lock ──────────────────────────────────────────────────────

  Future<void> setAppVolumeLockEnabled(bool value) async {
    _appVolumeLockEnabled = value;
    if (value) {
      await VolumeService.startAppVolumeLockService(
        _appEntries.values
            .where((e) => e.isTracked)
            .map((e) => e.packageName)
            .toList(),
      );
    } else {
      await VolumeService.stopAppVolumeLockService();
      await logEvent('App Volume Lock service stopped');
    }
    await _savePrefs();
    notifyListeners();
  }

  Future<void> loadInstalledApps() async {
    final apps = await VolumeService.getInstalledApps();
    _installedApps = apps.map((a) {
      final pkg = a['packageName'] as String;
      final existing = _appEntries[pkg];
      return existing ??
          AppVolumeEntry(
            packageName: pkg,
            appName: a['appName'] as String,
            iconBytes: a['icon'] != null && (a['icon'] as String).isNotEmpty
                ? base64Decode(a['icon'] as String)
                : null,
          );
    }).toList();

    // Merge remembered entries that may not be installed (keep them)
    for (final entry in _appEntries.values) {
      if (!_installedApps.any((e) => e.packageName == entry.packageName)) {
        _installedApps.add(entry);
      }
    }

    notifyListeners();
  }

  Future<void> toggleAppTracking(String packageName, bool tracked) async {
    final entry = _installedApps.firstWhere(
      (e) => e.packageName == packageName,
    );
    entry.isTracked = tracked;
    _appEntries[packageName] = entry;

    await logEvent('Toggle app tracking: $packageName -> $tracked');

    if (_appVolumeLockEnabled) {
      final trackedPkgs = _appEntries.values
          .where((e) => e.isTracked)
          .map((e) => e.packageName)
          .toList();
      await VolumeService.updateTrackedApps(trackedPkgs);
    }

    await _savePrefs();
    notifyListeners();
  }

  Future<void> setAppTrackingForPackages(
    Iterable<String> packageNames,
    bool tracked,
  ) async {
    final packageSet = packageNames.toSet();
    for (final entry in _installedApps) {
      if (packageSet.contains(entry.packageName)) {
        entry.isTracked = tracked;
        _appEntries[entry.packageName] = entry;
      }
    }

    await logEvent('Set app tracking for batch: tracked -> $tracked');

    if (_appVolumeLockEnabled) {
      final trackedPkgs = _appEntries.values
          .where((e) => e.isTracked)
          .map((e) => e.packageName)
          .toList();
      await VolumeService.updateTrackedApps(trackedPkgs);
    }

    await _savePrefs();
    notifyListeners();
  }

  void updateAppVolume(String packageName, int volume) {
    if (_appEntries.containsKey(packageName)) {
      _appEntries[packageName]!.rememberedMediaVolume = volume;
      logEvent('Update app volume: $packageName -> $volume');
      _savePrefs();
      notifyListeners();
    }
  }
}
