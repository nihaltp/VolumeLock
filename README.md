# Volume Lock

A native Android application written in Kotlin and Jetpack Compose that gives you fine-grained control over your device's audio volumes through two complementary features: **Volume Lock** and **App Volume Lock**.

---

## Features

### 🔒 Volume Lock

Lock all audio stream volumes (media, ring, notification and alarm) when the device screen turns off.

| Behaviour | Detail |
| ----------- | -------- |
| Screen turns **OFF** | Current volumes for all four streams are recorded as a *locked snapshot*. |
| Volume changed **while screen is off** | The change is immediately reverted to the locked snapshot value. |
| Screen turns **ON** (unlock / user-present) | The lock is cleared; volumes can be changed freely again. |

> **Permission required:** Do Not Disturb / Notification Policy access is needed to lock ring and notification volumes on Android 6+.

---

### 📱 App Volume Lock

Automatically restore the media volume for selected apps whenever you return to them.

| Behaviour | Detail |
| --------- | ------ |
| App moves to **background** | Its current media volume is saved. |
| App comes to **foreground** | Its saved media volume is restored. |

> **Only media volume** is managed per the feature specification.

You can choose exactly which apps are tracked via the built-in app selector (searchable list with checkboxes).

#### 🎵 Background-aware App Volume Lock (Optional)

If enabled, you can lock different volumes for a foreground app based on which background player is active (e.g. YouTube at volume 10 when Spotify is playing in the background vs. volume 6 when VLC is in the background).

> **Permissions required:**

> - The *Accessibility Service* (`Volume Lock`) must be enabled in Android Accessibility Settings to detect foreground app changes.
> - The *Notification Listener Service* (`Volume Lock`) must be granted access if you wish to use the background-aware pairing features.

---

## Architecture

```text
volumelock/
├── app/src/main/
│   ├── AndroidManifest.xml          # Permissions, services, receivers, activity
│   ├── kotlin/com/nihaltp/volumelock/
│   │   ├── MainActivity.kt          # Main Activity (Jetpack Compose Host)
│   │   ├── VolumeLockService.kt     # Foreground service — Volume Lock
│   │   ├── AppVolumeLockService.kt  # Foreground service — App Volume Lock
│   │   ├── AppVolumeAccessibilityService.kt  # Detects foreground app changes
│   │   ├── VolumeLockNotificationListenerService.kt # Media Session background player detection
│   │   ├── BootReceiver.kt          # Re-starts services after device reboot
│   │   ├── MediaSessionHelper.kt    # Helper for checking background media players
│   │   ├── VolumeConfig.kt          # Configuration data structure for per-app volume and pairings
│   │   └── ui/                      # Jetpack Compose UI
│   │       ├── screens/             # Home, Volume Lock, App Volume Lock, Settings, Log Viewer
│   │       ├── theme/               # Theme styling (dynamic color, dark/light)
│   │       └── viewmodel/           # VolumeLockViewModel (logic and state management)
│   └── res/
│       ├── xml/accessibility_service_config.xml
│       └── values/strings.xml
```

### Communication flow

```text
MainActivity (Jetpack Compose UI)
      |
      +--> VolumeLockViewModel (State & Logic)
                |
                +--> VolumeLockService  (ContentObserver + BroadcastReceiver)
                |
                +--> AppVolumeLockService  <--  AppVolumeAccessibilityService
```

---

## Getting Started

### Prerequisites

- Android SDK with `compileSdk 34`, `minSdk 26`
- A physical Android device or emulator running Android 8.0+

### Setup

```bash
# Clone the repository
git clone https://github.com/nihaltp/volumelock.git
cd volumelock

# Build the debug APK
./gradlew assembleDebug
```

### Granting Permissions

After first launch:

1. **Volume Lock** — when you enable the toggle, the app will ask you to grant *Do Not Disturb* access (Android Settings → Sounds → Do Not Disturb → App access).
2. **App Volume Lock** — enable the *Volume Lock* entry in Android Settings → Accessibility → Downloaded apps.
3. **Background-aware App Volume** — enable notification listener permission in Settings to allow media session detection.

---

## Running Tests

```bash
./gradlew test
```

---

## Changelog

Release history is tracked in [CHANGELOG.md](CHANGELOG.md).

---

## Dependencies

- **Jetpack Compose** — Modern native UI toolkit
- **Jetpack Navigation** — Screen-to-screen navigation
- **Jetpack Lifecycle & ViewModel** — Architectural state and event management
- **Kotlin Coroutines & Flow** — Structured background tasks and reactive state streams
- **Screengrab** — Instrumented screenshot tests for Google Play / fastlane integration

---

## Permissions Summary

| Permission | Used for |
| ------------ | ---------- |
| `CHANGE_AUDIO_SETTINGS` | Read/write stream volumes |
| `MODIFY_AUDIO_SETTINGS` | Modify audio routing |
| `FOREGROUND_SERVICE` | Keep monitoring services alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Native special-use foreground service permission |
| `RECEIVE_BOOT_COMPLETED` | Re-start services after reboot |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |
| `ACCESS_NOTIFICATION_POLICY` | Required to appear in Do Not Disturb access settings |
| `BIND_ACCESSIBILITY_SERVICE` | Detect foreground app changes |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Monitor active Media Sessions for background player detection |

---

## License

This project is licensed under the terms of the [LICENSE](LICENSE) file.
