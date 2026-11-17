# VORTEX RP — Android SA-MP Launcher

A native Android launcher for the VORTEX RP community, styled around the server-selection flow familiar from SA-MP mobile launchers.

## Launcher interface

- Premium dark VORTEX red/purple/black design.
- Home screen with player nickname, selected server, online status and a large PLAY ONLINE action.
- Server browser with My Servers, Favorites and a configurable Hosted directory.
- Server details dialog with current player count, ping, game mode and address when the UDP query is available.
- Add server by hostname/IP and port, locally saved favorites, and persistent selected server.
- Settings divided into General, Chat and Mods categories.
- Downloads / CEFS manager with HTTPS package download, SHA-256 verification, per-file validation, backup and restore.
- Optional HTTPS announcement feed.
- Native Android 15 target (API 35), Java 17, min SDK 26.

## Functional boundaries

This is an original VORTEX launcher UI and external-client adapter, not a rebranded copy of Alyn and not a full SA-MP game engine.

- GTA San Andreas and a compatible Android multiplayer client must already be installed legally.
- Play validates both packages, opens the real selected multiplayer client and copies the selected server address.
- Automatic joining only works when the selected client has a documented direct-connect URI. No universal Android SA-MP intent is assumed.
- The launcher cannot access another app's private game files or inject native CEF into it.
- CEFS installation handles web files only. A client with native CEF support is required for in-game rendering.
- Query timeouts are reported as unverified; player counts are never fabricated.
- No proprietary GTA assets, game executables or third-party client binaries are included.

## Hosted directory format

Settings → General → Live Server + News Feeds accepts an HTTPS directory returning either a JSON array or an object with a servers array:

    {
      "servers": [
        {"name": "VORTEX RP", "host": "rp.example.net", "port": 7777}
      ]
    }

At most 100 rows are loaded. See docs/alyn-style-flow.md and docs/cefs-package-format.md.

## Build and download

GitHub Actions builds the debug APK, verifies its signature, and publishes a release after every relevant push to main.

- Download latest APK from Releases: https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/releases
- Build workflow: https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/actions

The debug APK is for testing. A production release should use a private signing key stored in GitHub Actions secrets; never commit signing keys.

No VORTEX server IP, hosted directory URL, news URL, CEFS URL or package checksum was supplied, so those are configured inside the app rather than guessed or hardcoded.
