# VORTEX RP — Mobile CEF files

This folder contains an original VORTEX RP CEF interface (HTML/CSS/JavaScript) and a Pawn integration example for the **SA:MP Mobile CEF** library.

## Important: what this does

CEF is the in-game browser bridge used to display HTML/CSS/JavaScript interfaces inside a compatible SA:MP mobile client. These files create the VORTEX HUD and the matching server-side event example.

**These files do not add CEF support to the VORTEX launcher APK.** The CEF library must be integrated into the actual SA:MP mobile game client. The upstream mobile CEF instructions require client-side Android Java and native C++ integration. A launcher cannot inject that library into a separately installed Play Store game. The game-client maintainer (or someone with its source code) must add the library first.

## Files

- `client-assets/index.html` — VORTEX in-game HUD.
- `client-assets/styles.css` — dark red/purple styling.
- `client-assets/vortex.js` — event bridge and UI actions.
- `server/vortex_cef_example.pwn` — Pawn example for SA:MP/open.mp servers.

## Client integration

Use the upstream **SA:MP Mobile CEF** project and follow its client-side instructions:

https://github.com/denis-akazuki/samp-mobile-cef

The upstream client documentation explains that the Android client needs its CEF Java AAR and native C++ library wired into the game client. For the local page path used by this example, copy the contents of `client-assets/` into the game client's Android assets directory:

`app/src/main/assets/cef/`

The final client assets should include:

- `app/src/main/assets/cef/index.html`
- `app/src/main/assets/cef/styles.css`
- `app/src/main/assets/cef/vortex.js`

The SA:MP mobile client must initialize CEF networking with the same custom packet ID used on the server. This example uses `252`; choose another free ID if 252 is already taken.

## Server integration

1. Download the official `SAMPMobileCef.inc` from the upstream project into your server's `pawno/include` folder.
2. Install the upstream dependencies: Pawn.RakNet, pawn-json, and the SA-MP GVar plugin.
3. Open `server/vortex_cef_example.pwn` and merge its includes, defines, callbacks, and `Vortex_SendHudStats` stock into your existing gamemode. Do not add duplicate public callbacks already present in your gamemode.
4. Make sure the client and server both use the same free CEF packet ID.
5. Compile and test with a client that actually has the mobile CEF library integrated.

Use `/vortexui` in game to make the HUD buttons interactive. The example connects the buttons to chat notices only; replace those cases with your existing character, inventory, and phone systems. Call `Vortex_SendHudStats(playerid)` after your gamemode changes the player's money, health, armour, or display name.

## Official references

- Mobile CEF project: https://github.com/denis-akazuki/samp-mobile-cef
- Client setup: https://github.com/denis-akazuki/samp-mobile-cef/blob/master/docs/en/client.md
- Server setup and API: https://github.com/denis-akazuki/samp-mobile-cef/blob/master/docs/en/server.md
- Web API: https://github.com/denis-akazuki/samp-mobile-cef/blob/master/docs/en/web.md

The CEF library and its include file are third-party work; retain their original copyright notices and follow the upstream project's terms.
