# VORTEX RP — Android SA-MP Launcher

Native Android launcher project for the VORTEX RP community. The launcher opens a separately installed GTA: San Andreas game/client; it is not a GTA game, emulator, or bundled multiplayer client.

## Features in this repository

- Native Android UI with VORTEX red/purple/black styling and responsive layouts.
- Android 15 target (API 35), Java 17, minimum Android 8.0.
- Persistent VORTEX server host and port settings.
- Real SA-MP/open.mp UDP information query for online players, hostname, mode, password flag and response time.
- Installed GTA package and multiplayer-client selection, package/version reporting and launch checks.
- Actual external app launch. If no client URI integration is configured, the server address is copied and the client opens for manual connection.
- Optional direct-connect URI template for a client that documents such an interface.
- Optional HTTPS JSON announcements feed.
- CEFS ZIP downloader with HTTPS-only transport, archive SHA-256 validation, per-file SHA-256 manifest validation, path traversal protection, progress, private backups and restore.
- User-selected Android Storage Access Framework folders; no broad storage permission and no QUERY_ALL_PACKAGES.
- GitHub Actions builds and verifies a debug APK and uploads it as a downloadable workflow artifact.

## Important compatibility boundaries

1. **A mobile multiplayer client is still required.** The launcher does not include GTA:SA files, OBBs, game executables, proprietary native libraries or third-party APKs.
2. **There is no universal Android SA-MP direct-connect intent.** The default Play action launches the selected installed client and copies host:port; it does not claim the player is connected. A direct-connect URI is only sent when you configure a URI template documented by that particular client.
3. **open.mp Android support:** open.mp's published position does not endorse the existing unofficial SA-MP Android port. The official open.mp launcher is for desktop GTA:SA. Check the official project before relying on any third-party Android client.
4. **CEF requires client-side native support.** CEFS manager can place a verified web-resource package into a user-selected writable folder. It cannot inject CEF into an unrelated client or create missing native CEF libraries.
5. **Game files are app-private.** Android normally prevents one app from inspecting another app's private data. The launcher can report the selected app version and whether the user granted access to a game-data folder, but client-specific game-resource compatibility must still be tested by the client.
6. **Server status can be unverified.** The live count uses the SA-MP information-query protocol. If queries are disabled or UDP is filtered, the launcher reports an unverified response rather than inventing a player count.

See docs/client-integration.md and docs/cefs-package-format.md.

## Build

GitHub Actions runs on each relevant push to main and can also be started manually from the repository's Actions tab.

To get the APK, open the repository's Actions tab, select the latest successful "Build VORTEX RP Android APK" run, and download the "VORTEX-RP-Android-APK" artifact. The workflow artifact is retained for 30 days.

The generated artifact is a debug-signed APK for testing. A production release should be signed with a private release key stored in GitHub Actions secrets; never commit a signing key.

## Configure the launcher

Open Server and enter the VORTEX server hostname/IP and port. Open Settings to choose the installed GTA package and SA-MP mobile client. Open CEFS to configure the trusted HTTPS package URL and expected archive SHA-256, then select a writable destination folder.

No VORTEX server IP, news-feed URL, CEFS download URL or checksum was supplied with this repository, so none is hardcoded or fabricated.
