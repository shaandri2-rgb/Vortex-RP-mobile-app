# VORTEX RP launcher flow

This project takes inspiration from common SA-MP mobile launcher workflows: a current-server home screen, player nickname, a prominent Play Online action, saved/favourite servers, a hosted directory, a connection details panel, settings categories and a separate downloads/mods area.

It uses original VORTEX RP branding, layouts and Android code. It does not copy Alyn's artwork, branding, game assets or source files.

## Server browser

The browser has three filters:

- My Servers: locally saved endpoints.
- Favorites: saved endpoints marked as favorites.
- Hosted: entries read from the configured HTTPS JSON directory.

Add Server IP saves a name, hostname/IP, and UDP port locally. The launcher makes a SA-MP information query to each saved endpoint. A timeout is shown as unverified because servers may disable information queries.

## Hosted directory JSON

Settings → General → Live Server + News Feeds accepts an HTTPS hosted-directory URL. The response must be either a JSON array or an object containing a servers array.

Example:

    {
      "servers": [
        {
          "name": "VORTEX RP",
          "host": "rp.example.net",
          "port": 7777
        }
      ]
    }

The launcher accepts up to 100 entries and ignores invalid host/port rows. It does not upload player data to the directory.

## Connection flow

The Connect dialog shows the latest available server query details. Play opens the real installed multiplayer client. A client-specific direct-connect URI may be configured in Settings only when that client documents it. Otherwise the endpoint is copied to the clipboard and the launcher explains that manual selection is required.

## Settings categories

- General: nickname, installed GTA package, multiplayer client, default server and client URI.
- Chat: locally saved future-bridge preferences; they do not pretend to change a third-party client's chat UI.
- Mods: CEFS package manager and a warning that native mod loaders must be built into the game client.

## Client requirement

This is not a full SA-MP game engine. It does not bundle GTA San Andreas assets or an Android multiplayer executable. A working game and compatible multiplayer client are still required on the device. The VORTEX CEFS manager installs web resource files only; a compatible native CEF runtime must already be present in the chosen client.
