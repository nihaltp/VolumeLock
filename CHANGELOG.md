# Changelog

## [2.0.1] - 2026-06-24

### Added

- 7c32540  feat: minimize foreground service notification visibility

### Maintenance

- b0c4932  chore: create dependabot.yml
- 6bd9e0d  chore: update mergify to merge dependabot pr
- 071df85  chore: bump fastlane from 2.235.0 to 2.236.1
- 931b744  chore: use html in fastlane files
- 869b4a2  chore: update gradle.properties
- 71e733e  chore: remove apk splits
- 70486d2  chore: remove proguard
- cf76b64  chore: bump androidx.test.ext:junit from 1.1.5 to 1.3.0

# Changelog

## [2.0.0] - 2026-06-08

### Added

- 1c351ef  feat: Add background-aware App Volume Lock feature
- 8e782b0  feat: Cache Installed Apps and Load in Background

### Style

- b1515a1  style: change the windowBackground to ic launcher background color
- 5e42988  style: add dark theme and material you support

### Refactor

- 53ac655  refactor: migrate app to native Android

### Documentation

- 46c52ad  docs: Update documentation and comments to reflect native Jetpack Compose architecture

### Performance

- 6cfbdaf  perf: Progressive App Loading (One-by-One)

### Maintenance

- 95d13cb  chore: update pre-commit config
- 2e863a5  chore: Add GitHub PR and Issue templates
- 30f93bb  chore: remove log screen from screenshots
- 5bc489b  chore: make builds faster
- 2026709  chore: add fastlane screenshot
- a512800  chore: add fastlane
- a08eb73  chore: remove unwanted files
- 9ba99a0  chore: add pre-commit config

### Other

- e582c49  change app name to volumelock
- 689f0ca  fixup

# Changelog

## [1.2.0] - 2026-06-07

### Fixed

- 9fd9ec3  fix: offload the tasks to background to fix the ui freeze
- 7a7936b  fix: universal APK versionCode generation

### Added

- ee69c6f  feat: add settings screen with logging

### Other

- 91c63cc  Add ABI splits configuration for APK builds in Gradle files

# Changelog

All notable changes to this project are documented in this file.


## [1.1.0] - 2026-04-15

### Changed

- Show app icon/logo for all apps in App Volume Lock list, including apps with adaptive icons.
- Improved icon extraction for all Android versions and icon types.
- Bugfix: fallback to letter if icon missing or error.

## [1.0.0] - 2026-04-14

### Added

- Initial public release of Volume Lock with Volume Lock and App Volume Lock features.
- Added release documentation in README with GitHub and F-Droid release flow.
- Added this changelog file to track release notes.
