# Test launcher game-resource installer

The **DATA** tab imports a player-supplied ZIP/OBB archive or downloads a hosted Lite/Full ZIP package.

## Local import

1. Open **DATA → SELECT LOCAL GAME DATA ZIP / OBB**.
2. Choose an archive containing game resource files from a GTA: San Andreas copy you are legally entitled to use.
3. Confirm installation.
4. The launcher extracts the archive into its app-specific `game-data` directory.

## Remote packages

For each edition, enter a direct HTTPS ZIP URL and the archive's 64-character SHA-256 checksum from a trusted package host. The launcher verifies the complete archive before extracting it. Redirects away from HTTPS are rejected.

Lite and Full are package labels, not automatically generated versions of GTA data. The package host must supply the correct content for the target game/client version. This repository does not host game data and does not contain a default download URL.

## Safety and recovery

- ZIP paths are checked against absolute paths, traversal, duplicate paths and invalid segments.
- The installer rejects APK, DEX, JAR, SO, EXE, DLL, SH and BAT payloads.
- Archive size is limited to 3 GiB, one extracted file to 2 GiB, and the full expanded set to 6 GiB.
- Extraction happens in a staging directory. The previous game-data directory is kept as one rollback backup until the next package install.
- **RESTORE PREVIOUS GAME DATA** switches back to that prior directory.
- Data is stored in Android app-specific storage owned by Test launcher. Android 15 does not let this app write into another app's private files.

## Important client limitation

The installer prepares data for a client integration; it does not itself contain the native GTA/SA-MP engine. The Th1nk SA-MP Android project's public README says that its client loads `libGTASA.so` at runtime and does not include game files. This repository does not include that client's native engine source or a supported embedding API. Therefore, this installer does **not** make the current external-client launch flow an integrated one-APK client. That requires an embeddable, redistributable client implementation that can read the install directory above.

No Rockstar game assets or proprietary client binaries are bundled.
