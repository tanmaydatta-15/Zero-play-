# Contributing to Zero Play

Thank you for your interest in contributing to **Zero Play**!

## Ways to Contribute

- **Bug Reports:** Open an issue in [nilimadas567345-sudo/Zero-play-](https://github.com/nilimadas567345-sudo/Zero-play-/issues) with your Android version, device model, and steps to reproduce.
- **Feature Suggestions:** Propose improvements to audio/video playback, media conversion presets, downloader workflows, or UI polish.
- **Pull Requests:**
  1. Fork the repository and create a descriptive branch (`feat/...` or `fix/...`).
  2. Follow Material 3 & Jetpack Compose conventions and avoid hardcoding colors outside `Theme.kt`.
  3. Decode the debug keystore before building locally:
     ```bash
     base64 -d debug.keystore.base64 > debug.keystore
     gradle assembleDebug
     ```
  4. Verify compilation passes cleanly before opening a PR.

## Release Workflow

Updating `versionCode` and `versionName` in `app/build.gradle.kts` and pushing to `main` triggers [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) to build and publish a new release APK automatically.
