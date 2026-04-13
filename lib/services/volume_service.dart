import 'package:flutter/services.dart';

/// Flutter-side bridge to the Android native volume services via MethodChannel.
class VolumeService {
  static const _channel = MethodChannel('com.example.volume_lock/volume');
  static const _appsChannel = MethodChannel('com.example.volume_lock/apps');

  // ─── Volume Lock service ───────────────────────────────────────────────────

  /// Start the foreground Volume Lock service on Android.
  static Future<bool> startVolumeLockService() async {
    try {
      final result = await _channel.invokeMethod<bool>('startVolumeLockService');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Stop the foreground Volume Lock service.
  static Future<bool> stopVolumeLockService() async {
    try {
      final result = await _channel.invokeMethod<bool>('stopVolumeLockService');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  // ─── App Volume Lock service ───────────────────────────────────────────────

  /// Start the App Volume Lock accessibility service with [trackedPackages].
  static Future<bool> startAppVolumeLockService(List<String> trackedPackages) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'startAppVolumeLockService',
        {'trackedPackages': trackedPackages},
      );
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Stop the App Volume Lock service.
  static Future<bool> stopAppVolumeLockService() async {
    try {
      final result = await _channel.invokeMethod<bool>('stopAppVolumeLockService');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Push an updated list of tracked packages to the running service.
  static Future<void> updateTrackedApps(List<String> trackedPackages) async {
    try {
      await _channel.invokeMethod<void>(
        'updateTrackedApps',
        {'trackedPackages': trackedPackages},
      );
    } on PlatformException {
      // Service may not be running — ignore.
    }
  }

  // ─── Volume queries ────────────────────────────────────────────────────────

  /// Return current volumes as a map with keys:
  /// media, ring, notification, alarm (and their max counterparts).
  static Future<Map<String, int>> getCurrentVolumes() async {
    try {
      final raw = await _channel.invokeMapMethod<String, int>('getCurrentVolumes');
      return raw ?? {};
    } on PlatformException {
      return {};
    }
  }

  // ─── Installed apps ────────────────────────────────────────────────────────

  /// Return a list of user-installed apps as maps with 'packageName' and
  /// 'appName' keys.
  static Future<List<Map<String, String>>> getInstalledApps() async {
    try {
      final raw =
          await _appsChannel.invokeListMethod<Map>('getInstalledApps');
      if (raw == null) return [];
      return raw
          .map((m) => {
                'packageName': m['packageName'] as String,
                'appName': m['appName'] as String,
              })
          .toList();
    } on PlatformException {
      return [];
    }
  }

  // ─── Permission helpers ────────────────────────────────────────────────────

  /// Check whether the Accessibility Service is enabled for this app.
  static Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final result =
          await _channel.invokeMethod<bool>('isAccessibilityServiceEnabled');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Open the Accessibility Settings page so the user can enable the service.
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod<void>('openAccessibilitySettings');
    } on PlatformException {
      // Ignore
    }
  }

  /// Check whether the app has notification-listener / Do Not Disturb access
  /// (required for notification volume on some devices).
  static Future<bool> hasNotificationPolicyAccess() async {
    try {
      final result =
          await _channel.invokeMethod<bool>('hasNotificationPolicyAccess');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Open the Notification Policy / DND access settings.
  static Future<void> openNotificationPolicySettings() async {
    try {
      await _channel.invokeMethod<void>('openNotificationPolicySettings');
    } on PlatformException {
      // Ignore
    }
  }
}
