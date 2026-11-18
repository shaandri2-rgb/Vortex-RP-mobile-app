# Test launcher — Android SA-MP launcher project

A native Android launcher project for the VORTEX RP community. App label: **Test launcher**.

## Target

- Android 15 / API 35
- ARM64 devices supported by Android (the APK is not ABI-restricted; native client libraries, if later licensed and integrated, must include `arm64-v8a`)
- Java 17, Android Gradle Plugin 8.6.1
- Minimum Android API 26

## Launcher features

- Native dark launcher interface with Home, Servers, CEFS and Settings sections.
- Server browser with saved servers, favorites, manual IP/port entry and optional HTTPS hosted-server directory.
- Server status querying; unknown or timed-out values are shown as unverified, never invented.
- Persistent nickname and selected-server preferences.
- CEFS/web-file download manager with SHA-256 validation, backup and restore.
- Optional HTTPS announcements feed.
- Launch adapter for an already-installed compatible Android multiplayer client. It validates installed packages and opens the real client; automatic joining depends on a documented URI supported by that client.

## Important game-client limitation

**This repository does not contain an integrated SA-MP game engine or a bundled GTA San Andreas client.** The Android launcher UI and supporting features are native, but the current Play flow hands off to a separately installed compatible client.

A genuinely integrated client requires the legally redistributable native client source/SDK, Android build instructions, supported ABI libraries (including `arm64-v8a`), and the client developer's permission to redistribute or link it. None of those proprietary components are included here. The launcher cannot invent an undocumented SA-MP protocol API or embed another app's private game process.

CEF is only available when the selected game client itself supplies a compatible native CEF runtime and bridge. The CEFS manager in this project manages web files; it does not install or inject a CEF engine into a third-party app.

No Rockstar game assets, proprietary client binaries, credentials, or signing keys are included.

## Build

Pushes to `main` and manual workflow runs build a debug APK and upload it as a GitHub Actions artifact.

1. Open the repository's **Actions** tab.
2. Select **Android APK build**.
3. Run the workflow (or wait for a push build).
4. Download the `test-launcher-debug-apk` artifact from the completed run.

The debug APK is for testing. A production APK must be signed with a private release key stored in GitHub Actions secrets. Never commit signing keys.

## VORTEX RP configuration

The server browser supports manual server entry and an optional HTTPS server directory. Configure the real VORTEX RP host/port and any hosted-directory/news/CEFS URLs in the app. No unverified server address is hardcoded.
