<div align="center">

<img src="./.github/assets/kavern-logo.png" alt="Kavern logo" title="Kavern logo" width="80"/>

# Kavern

 Unofficial Android reader for your self-hosted **Kavita** library.

![Platform](https://img.shields.io/badge/platform-Android-blue)
![Kavita](https://img.shields.io/badge/Kavita-nightly_0.9.1.20-orange)

</div>

---

## Overview

Kavern is a Kotlin/Jetpack Compose Android app to browse and read from your Kavita server. It aims for fast navigation, a clean reader, and tight API integration (collections, progress, metadata).

Kavern is a fork of [Inkita](https://github.com/dom-53/Inkita) by [dom-53](https://github.com/dom-53). This project would not exist without their work. Thank you!

**Tech:** Kotlin, Jetpack Compose (Material 3), Retrofit/OkHttp, WorkManager, Room/DataStore.
**Formats:** EPUB (primary), comics/images and PDF.
**Status:** Preview (0.x); APIs and UI may change.

---

## Features

- Browse libraries, collections, tags, genres with filters.
- Home screen showing your series sorted by last read, with reading progress on every cover.
- Series detail with covers, metadata, tags, related items, specials.
- EPUB reader with paged reading, word lookup (Define), progress sync, offline mode, downloaded page handling, “delete after reading” depth.
- E-ink mode that turns off page-turn and screen animations.
- Optional hardware page buttons and volume keys for turning pages.
- Downloads: queue, per-volume/page downloads; foreground notifications via a shared manager.
- Network-aware: connectivity monitor, offline toggle, WorkManager constraints.

---

## Connecting to your Kavita server

Kavern ships without any preset server configuration. After the first launch:

1. Open **Settings → Kavita**.
2. Fill in your server URL credentials and your Kavita API key.
3. Save the configuration and restart app; the app will then authenticate and sync your libraries.

Without these details the app cannot reach your server, so make sure to keep them up to date (especially when rotating API keys).

---

## Getting Started (dev)

1. **Prereqs:** Android Studio Giraffe+, JDK 17.
2. **Configure Kavita:** In-app settings → set server URL and API key.
3. **Run:** `./gradlew assembleDebug` or launch from Android Studio (preview/release buildTypes available).

> Requires your own Kavita server; no content is bundled.

Kavern has no built-in updater and no prebuilt releases; build the APK yourself.

---

## Contributing

Contributions are welcome (bugfixes, UX polish, docs). Please:
- Keep changes Compose-friendly and minimal.
- Align with Kavita API contracts.
- Add concise comments only where needed.
- Before opening an issue, check if it already exists.
- Before opening a PR, run `./gradlew detekt spotlessApply`.

## Logs / Debug
- You can export or save anonymized logs from **Settings → Advanced → Logs**; saved zips are written to `Documents/Kavern/logs`.
- Sensitive data (host/IP/API key) is anonymized in log files and exports.
- For bug reports: enable **Verbose logging**, reproduce the issue, then attach the exported ZIP to your issue/report.

---

## Disclaimer

Kavern is an unofficial community project, not affiliated with the Kavita team or the original Inkita project. Use at your own risk; APIs and behavior may change in 0.x releases.
