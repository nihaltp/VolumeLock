# Volume Lock SSOT

This document is the single source of truth (SSOT) for how the project works.
If code and docs ever conflict, update this file in the same change that updates behavior.

## 1. Product Purpose

Volume Lock is a native Android application written in Kotlin and Jetpack Compose with two independent features:

- **Volume Lock**: lock media/ring/notification/alarm behavior around screen off/lock state.
- **App Volume Lock**: remember and restore media volume per selected app.
- **Background-aware App Volume Lock**: remember and restore media volume per selected app based on active background music player.

## 2. In-Scope Platforms

- Android only (native Kotlin and Jetpack Compose UI).

## 3. Feature Contracts

### 3.1 Volume Lock contract

When enabled:

1. Service captures current stream volumes on `ACTION_SCREEN_OFF`.
2. While device is considered locked (`screen off` OR `keyguard locked`), volume drifts are reverted.
3. Lock is cleared only on `ACTION_USER_PRESENT` (actual unlock), not on `ACTION_SCREEN_ON`.
4. Service runs in foreground with persistent notification.

Managed streams:

- `STREAM_MUSIC`
- `STREAM_RING`
- `STREAM_NOTIFICATION`
- `STREAM_ALARM`

### 3.2 App Volume Lock contract

When enabled:

1. Accessibility service reports foreground app changes.
2. If tracked app moves to background, current media volume is saved.
3. If tracked app comes to foreground and has remembered value, media volume is restored.
4. Only media volume is managed for this feature.
5. Service runs in foreground with persistent notification.

#### Background-aware App Volume Lock (Optional)

If enabled:

1. Notification Listener Service tracks active Media Sessions to identify background media apps.
2. If a tracked app comes to the foreground while a background music player is active, the app's specific volume pairing for that background player is restored.
3. If no pairing exists or same app, the app's default volume is restored.

Tracked apps are chosen via the built-in app selector (searchable list with checkboxes).

## 4. Permissions and Why

Declared in `app/src/main/AndroidManifest.xml`.

- `CHANGE_AUDIO_SETTINGS`: read/write stream volumes.
- `MODIFY_AUDIO_SETTINGS`: allow audio setting changes.
- `FOREGROUND_SERVICE`: run monitor services reliably.
- `FOREGROUND_SERVICE_SPECIAL_USE`: special-use foreground service declaration.
- `RECEIVE_BOOT_COMPLETED`: restart enabled features after reboot.
- `POST_NOTIFICATIONS`: show foreground notifications (Android 13+).
- `ACCESS_NOTIFICATION_POLICY`: allow app to appear in DND access settings.
- `BIND_ACCESSIBILITY_SERVICE`: required by accessibility service declaration.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: monitor active Media Sessions for background player detection.

## 5. Runtime Architecture

### UI & ViewModel Layer

- `MainActivity.kt`: Main activity hosting Compose UI, sets up theme and requests permissions.
- `VolumeLockViewModel.kt`: Central state management, handles preference persistence, app querying, log management, and service triggers.
- `ui/screens/HomeScreen.kt`: Dashboard screen.
- `ui/screens/VolumeLockScreen.kt`: Volume Lock controls and live volume display.
- `ui/screens/AppVolumeLockScreen.kt`: App selector list and volume lock settings.
- `ui/screens/SettingsScreen.kt`: Theme selection, Dynamic color configuration, and log links.
- `ui/screens/LogViewerScreen.kt`: Log scrolling and clearing controls.

### Android Services Layer

- `VolumeLockService.kt`: Volume Lock foreground service.
- `AppVolumeLockService.kt`: App Volume Lock foreground service.
- `AppVolumeAccessibilityService.kt`: Foreground app detection service.
- `VolumeLockNotificationListenerService.kt`: Notification listener to query active media sessions.
- `BootReceiver.kt`: Restart enabled services on boot.
- `MediaSessionHelper.kt`: Queries active media sessions and identifies the background music player.
- `VolumeConfig.kt`: Data class storing the default volume and custom background-player pairings for a foreground package.

## 6. Persistence Model

Persisted via native `SharedPreferences` under the file name `"volume_lock_prefs"`:

- `volume_lock_enabled` (bool)
- `app_volume_lock_enabled` (bool)
- `background_aware_enabled` (bool)
- `logging_enabled` (bool)
- `theme_mode` (string: "system", "dark", "light")
- `material_you_enabled` (bool)
- `tracked_apps` (JSON string array of package names)
- `cached_installed_apps` (JSON string array of cached `AppVolumeEntry` details)
- `app_volume_config_<package>` (JSON string representing a `VolumeConfig` object containing `defaultVolume` and `pairings` map)
- `app_logs` (JSON string array of logs, capped at 500 lines)

Boot receiver reads flags directly from `volume_lock_prefs`:

- `volume_lock_enabled`
- `app_volume_lock_enabled`

## 7. Known Operational Constraints

- App Volume Lock depends on Accessibility permission and OEM background processing policies.
- Background-aware player detection requires Notification Listener permission.
- DND policy access behavior can vary by OEM and Android version.
- Aggressive battery optimization might stop foreground services; the ongoing notification mitigates this but does not override all OEM restrictions.

## 8. Change Rules (SSOT maintenance)

Any change to these components must update this file:

- permission requirements
- volume lock timing semantics
- boot restore semantics
- persisted key names
- data storage schemas

Minimum checklist before committing:

1. Update `docs/ssot.md` when behavior contracts change.
2. Run `./gradlew test` and verify compile state.
3. Validate settings screen and service toggles.
