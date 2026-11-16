# VORTEX RP CEF module

The app's CEF tab is a browser preview of the same HTML/CSS/JS HUD shipped here. It is intentionally labelled as a preview: Android WebView preview is not the SA:MP Mobile CEF runtime.

## HUD files
- `app/src/main/assets/cef/index.html` — preview copy bundled inside the launcher.
- `client-assets/index.html`, `styles.css`, `vortex.js` — files to copy into the game client's Android assets directory.
- `server/vortex_cef_example.pwn` — Pawn event bridge example.

## What is still needed for real in-game CEF

The game client must be rebuilt with the upstream CEF client library:
https://github.com/denis-akazuki/samp-mobile-cef

Its client instructions require:
- `client/cpp/libSAMPMobileCef.a` and `SAMPMobileCef.h` linked into the native SA:MP client;
- `cef::setGamePath(...)`, `cef::initNetwork(...)`, packet dispatch and server-connection handling in the client C++;
- the supplied CEF Java AAR added to the client app;
- `CefJavaManager` and `CefClientManager` initialized by the game's `NvEventQueueActivity`.

This repository currently contains a launcher, not the source of the selected SA:MP game client. It cannot patch another installed app or create a working CEF-enabled game client by itself. A real client build needs the exact compatible game-client source and the correctly licensed GTA:SA 2.10 files. Do not package proprietary GTA data without rights.

## Server side
Follow the upstream setup for Pawn.RakNet, pawn-json and GVar:
https://github.com/denis-akazuki/samp-mobile-cef/blob/master/docs/en/server.md

The server's CEF packet ID must match the ID compiled into the mobile client. The example uses 252; confirm that this is free in your client before using it.
