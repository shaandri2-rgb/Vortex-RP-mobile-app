# VORTEX CEFS package format

The launcher downloads no package by default. An administrator must configure an HTTPS URL and the expected 64-character SHA-256 archive digest in the app.

## ZIP layout

The archive must contain:

- vortex-cef-manifest.json
- Every resource listed by the manifest, at its exact relative path.

Example manifest:

    {
      "schemaVersion": 1,
      "files": [
        {
          "path": "cef/index.html",
          "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        },
        {
          "path": "cef/styles.css",
          "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        }
      ]
    }

Replace the example hashes with the real SHA-256 values of the files. The archive SHA-256 is separate and is entered in launcher settings.

## Installation safety

- HTTPS is required for package downloads and redirects.
- The full ZIP is checked against the configured SHA-256 before it can be installed.
- Each listed file is checked against the per-file SHA-256 in the manifest.
- The package path must be relative; absolute paths, .., empty segments, backslashes and duplicate paths are rejected.
- The user must choose the destination folder with Android's folder picker.
- Before a listed destination file is replaced, its previous contents are copied into the launcher's private backup area.
- A confirmation dialog is shown before installation.
- Restore removes package-added files and restores pre-existing originals.
- A second install is blocked while a rollback backup is active. Restore first.

The installer does not follow package paths outside the selected folder. It does not ask for all-files access and does not bypass Android's protected Android/data or another app's private storage restrictions.

## Native CEF is separate

These are web assets only. A client must already include a compatible native CEF runtime and a client-side bridge. Installing HTML/CSS/JS files does not add those native libraries or make CEF appear inside a third-party game.
