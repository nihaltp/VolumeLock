# Volume Lock

A Flutter Android application that gives you fine-grained control over your device's audio volumes through two complementary features: **Volume Lock** and **App Volume Lock**.

---

## Features

### 🔒 Volume Lock

Lock all audio stream volumes (media, ring, notification and alarm) when the device screen turns off.

| Behaviour | Detail |
|-----------|--------|
| Screen turns **OFF** | Current volumes for all four streams are recorded as a *locked snapshot*. |
| Volume changed **while screen is off** | The change is immediately reverted to the locked snapshot value. |
| Screen turns **ON** (unlock / user-present) | The lock is cleared; volumes can be changed freely again. |

> **Permission required:** Do Not Disturb / Notification Policy access is needed to lock ring and notification volumes on Android 6+.

---

### 📱 App Volume Lock

Automatically restore the media volume for selected apps whenever you return to them.

| Behaviour | Detail |
|-----------|--------|
| App moves to **background** | Its current media volume is saved. |
| App comes to **foreground** | Its saved media volume is restored. |
| **Only media volume** is managed per the feature specification. |  |

You can choose exactly which apps are tracked via the built-in app selector (searchable list with checkboxes).

> **Permission required:** The *Accessibility Service* (`Volume Lock`) must be enabled in Android Accessibility Settings to detect foreground app changes.

---

## Architecture

```
volume_lock/
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml          # Permissions, services, receivers
│       ├── kotlin/com/nihaltp/volume_lock/
│       │   ├── MainActivity.kt          # Flutter <-> Android MethodChannel bridge
│       │   ├── VolumeLockService.kt     # Foreground service — Volume Lock
│       │   ├── AppVolumeLockService.kt  # Foreground service — App Volume Lock
│       │   ├── AppVolumeAccessibilityService.kt  # Detects foreground app changes
│       │   └── BootReceiver.kt          # Re-starts services after device reboot
│       └── res/
│           ├── xml/accessibility_service_config.xml
│           └── values/strings.xml
└── lib/
    ├── main.dart                        # App entry point, theme, Provider setup
    ├── models/
    │   └── app_state.dart               # Central ChangeNotifier state
    ├── services/
    │   └── volume_service.dart          # MethodChannel wrappers (Dart side)
    └── screens/
        ├── home_screen.dart             # Dashboard with both feature cards
        ├── volume_lock_screen.dart      # Volume Lock toggle + live volume display
        └── app_volume_lock_screen.dart  # App Volume Lock toggle + app selector
```

### Communication flow

```
Flutter UI  --MethodChannel-->  MainActivity
                                    |
                                    +--> VolumeLockService  (ContentObserver + BroadcastReceiver)
                                    |
                                    +--> AppVolumeLockService  <--  AppVolumeAccessibilityService
```

---

## Getting Started

### Prerequisites

* Flutter SDK >= 3.3.0
* Android SDK with `compileSdk 34`, `minSdk 21`
* A physical Android device or emulator running Android 5.0+

### Setup

```bash
# Clone the repository
git clone https://github.com/nihaltp/volume_lock.git
cd volume_lock

# Install Flutter dependencies
flutter pub get

# Run on a connected device
flutter run
```

### Granting Permissions

After first launch:

1. **Volume Lock** — when you enable the toggle, the app will ask you to grant *Do Not Disturb* access (Android Settings → Sounds → Do Not Disturb → App access).
2. **App Volume Lock** — enable the *Volume Lock* entry in Android Settings → Accessibility → Downloaded apps.

---

## Running Tests

```bash
flutter test
```

---

## Dependencies

| Package | Purpose |
|---------|---------|
| [`provider`](https://pub.dev/packages/provider) | State management |
| [`shared_preferences`](https://pub.dev/packages/shared_preferences) | Persist lock settings across restarts |

---

## Permissions Summary

| Permission | Used for |
|------------|----------|
| `CHANGE_AUDIO_SETTINGS` | Read/write stream volumes |
| `MODIFY_AUDIO_SETTINGS` | Modify audio routing |
| `FOREGROUND_SERVICE` | Keep monitoring services alive |
| `RECEIVE_BOOT_COMPLETED` | Re-start services after reboot |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |
| `BIND_ACCESSIBILITY_SERVICE` | Detect foreground app changes |

---

## License

This project is licensed under the terms of the [LICENSE](LICENSE) file.
