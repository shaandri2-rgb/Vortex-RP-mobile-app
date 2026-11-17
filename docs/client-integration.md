# Multiplayer client integration

## What Play does today

The launcher requires a selected, installed GTA: San Andreas app package and a selected, installed multiplayer-client package. It validates that both packages are present before launching. It then:

- Opens the real installed multiplayer app using Android's package launch intent.
- Copies the configured host:port to the clipboard.
- Clearly tells the player that a direct connection has **not** been made when the client has no configured bridge.

This is intentional. A launcher cannot safely assume an undocumented intent format or silently report a successful connection.

## Configure direct connection

Some client builds may expose a URI or Android intent contract. If the chosen client publishes one, enter its documented URI template in Settings → Optional direct-connect bridge.

Available substitutions:

- {host} — configured server hostname/IP
- {port} — configured server port
- {nickname} — URL-encoded player nickname

Example syntax only (not a universal SA-MP standard):

    samp://{host}:{port}

The template is sent as ACTION_VIEW with the selected client package as the target. The launcher first checks that the selected client resolves the URI. If the client does not resolve it, the launcher does not fall back to pretending it connected.

Do not use a URI copied from an unrelated launcher unless the actual client documents it.

## Client compatibility

SA-MP and open.mp desktop clients are not interchangeable with Android apps. The official open.mp project has stated that it does not support the existing unofficial SA-MP Android port. This project therefore does not bundle, rebrand, decompile or modify a third-party multiplayer client.

The launcher reports package name and version but cannot inspect another app's private files or prove its GTA engine/data version. Android folder access is granted by the user through the system folder picker. A visible package alone is not proof that its game assets are valid.

To add a native adapter for a specific client, the implementation needs that client's documented connection API, configuration-file format, package ID, supported GTA data version, and any required permission model. Without those facts, the generic launcher remains in the manual-connect mode.

## Android task behavior

Android launches the multiplayer client as another application. When that activity finishes, Android normally returns to the task that launched it. The launcher does not force-close the game or attempt to intercept its exit.
