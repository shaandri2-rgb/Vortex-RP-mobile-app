/*
 * VORTEX RP - SA:MP Mobile CEF integration example
 *
 * Merge these publics and the stock function into your existing gamemode.
 * Do not paste duplicate OnPlayerConnect/OnPlayerSpawn/etc. publics.
 *
 * The packet ID must match the ID_CUSTOM_CEF used by the mobile client
 * and must not conflict with another packet used by your server.
 */
#include <open.mp> // For classic SA:MP, use: #include <a_samp>
#include <Pawn.RakNet>
#include <json>
#include <gvar>
#include <SAMPMobileCef>

#define CEF_PACKET_ID 252
#define VORTEX_CEF_URL "file:///android_asset/cef/index.html"

forward Vortex_OnMenuAction(playerid, event_data[]);

public OnGameModeInit()
{
    CefSetPacketId(CEF_PACKET_ID);
    CefRegisterEventCallback("vortex_menu_action", "Vortex_OnMenuAction");
    return 1;
}

public OnCefBrowserInit(playerid, is_init, error_code)
{
    if (!is_init)
    {
        printf("[VORTEX CEF] Browser failed for player %d (error %d)", playerid, error_code);
        SendClientMessage(playerid, 0xFF5577FF, "[VORTEX] Your client does not have working CEF support.");
        return 1;
    }

    CefShowBrowser(playerid);
    CefChangeBrowserFocus(playerid, false);
    CefSendEvent(playerid, "vortex_notify", "VORTEX interface connected.");
    Vortex_SendHudStats(playerid);
    return 1;
}

public OnPlayerConnect(playerid)
{
    CefInitBrowser(playerid, VORTEX_CEF_URL);
    CefChangeBrowserFocus(playerid, false);
    return 1;
}

public OnPlayerDisconnect(playerid, reason)
{
    CefDestroyBrowser(playerid);
    return 1;
}

public OnPlayerSpawn(playerid)
{
    Vortex_SendHudStats(playerid);
    return 1;
}

public Vortex_OnMenuAction(playerid, event_data[])
{
    // A button click returns the matching action string from vortex.js.
    // Connect each case to your real character, inventory, and phone systems.
    CefChangeBrowserFocus(playerid, false);

    if (!strcmp(event_data, "character", true))
    {
        SendClientMessage(playerid, 0xB84BFFFF, "[VORTEX] Character menu requested.");
    }
    else if (!strcmp(event_data, "inventory", true))
    {
        SendClientMessage(playerid, 0xB84BFFFF, "[VORTEX] Inventory menu requested.");
    }
    else if (!strcmp(event_data, "phone", true))
    {
        SendClientMessage(playerid, 0xB84BFFFF, "[VORTEX] Phone menu requested.");
    }
    else if (!strcmp(event_data, "close", true))
    {
        CefHideBrowser(playerid);
    }
    return 1;
}

public OnPlayerCommandText(playerid, cmdtext[])
{
    if (!strcmp(cmdtext, "/vortexui", true))
    {
        if (!CefIsPlayerHasLibrary(playerid))
        {
            SendClientMessage(playerid, 0xFF5577FF, "[VORTEX] Mobile CEF is not available in your game client.");
            return 1;
        }

        CefShowBrowser(playerid);
        CefChangeBrowserFocus(playerid, true);
        Vortex_SendHudStats(playerid);
        return 1;
    }
    return 0;
}

stock Vortex_SendHudStats(playerid)
{
    new playerName[MAX_PLAYER_NAME];
    new moneyText[24];
    new healthText[12];
    new armourText[12];
    new Float:health, Float:armour;

    GetPlayerName(playerid, playerName, sizeof playerName);
    GetPlayerMoney(playerid);
    GetPlayerHealth(playerid, health);
    GetPlayerArmour(playerid, armour);

    format(moneyText, sizeof moneyText, "%d", GetPlayerMoney(playerid));
    format(healthText, sizeof healthText, "%.0f", health);
    format(armourText, sizeof armourText, "%.0f", armour);

    CefSendEvent(playerid, "vortex_player_name", playerName);
    CefSendEvent(playerid, "vortex_money", moneyText);
    CefSendEvent(playerid, "vortex_health", healthText);
    CefSendEvent(playerid, "vortex_armour", armourText);
    return 1;
}
