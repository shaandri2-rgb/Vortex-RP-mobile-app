package com.vortexrp.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 8, 16);
    private static final int PANEL = Color.rgb(24, 19, 32);
    private static final int PANEL_2 = Color.rgb(34, 26, 44);
    private static final int PURPLE = Color.rgb(178, 75, 255);
    private static final int RED = Color.rgb(255, 55, 91);
    private static final int MUTED = Color.rgb(164, 154, 177);

    private SharedPreferences prefs;
    private LinearLayout page;
    private String selectedPackage = "";
    private String currentTab = "HOME";

    private static class Server {
        String name;
        String host;
        int port;

        Server(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        String address() { return host + ":" + port; }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vortex_launcher", MODE_PRIVATE);
        selectedPackage = prefs.getString("game_package", "");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout brandBar = new LinearLayout(this);
        brandBar.setGravity(Gravity.CENTER_VERTICAL);
        brandBar.setPadding(dp(18), dp(15), dp(18), dp(13));
        TextView mark = text("V", 24, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(PURPLE, 0, 14));
        brandBar.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(11), 0, 0, 0);
        brandText.addView(text("VORTEX RP", 19, Color.WHITE, true));
        brandText.addView(text("SAN ANDREAS MULTIPLAYER", 9, MUTED, true));
        brandBar.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("MOBILE", 9, PURPLE, true);
        version.setPadding(dp(9), dp(6), dp(9), dp(6));
        version.setBackground(shape(PANEL_2, PURPLE, 8));
        brandBar.addView(version);
        root.addView(brandBar);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(53, 40, 65));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(22));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(7), dp(7), dp(7), dp(7));
        bottom.setBackgroundColor(Color.rgb(17, 13, 24));
        String[] tabs = {"HOME", "SERVERS", "CEF", "SETTINGS"};
        for (String tab : tabs) {
            Button nav = new Button(this);
            nav.setText(tab);
            nav.setAllCaps(false);
            nav.setTextSize(10);
            nav.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            nav.setTextColor(tab.equals(currentTab) ? Color.WHITE : MUTED);
            nav.setBackground(shape(tab.equals(currentTab) ? PANEL_2 : Color.TRANSPARENT,
                    tab.equals(currentTab) ? PURPLE : 0, 10));
            nav.setOnClickListener(v -> showTab(tab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bottom.addView(nav, lp);
        }
        root.addView(bottom);
        renderHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    private LinearLayout row() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }

    private void gap(int height) {
        View v = new View(this);
        page.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void showTab(String tab) {
        currentTab = tab;
        if ("HOME".equals(tab)) renderHome();
        else if ("SERVERS".equals(tab)) renderServers();
        else if ("CEF".equals(tab)) renderCef();
        else renderSettings();
    }

    private void heading(String title, String subtitle) {
        page.addView(text(title, 23, Color.WHITE, true));
        TextView sub = text(subtitle, 11, MUTED, false);
        sub.setPadding(0, dp(5), 0, dp(14));
        page.addView(sub);
    }

    private LinearLayout card(String title, String description) {
        LinearLayout box = column();
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(shape(PANEL, Color.rgb(62, 45, 77), 15));
        TextView titleView = text(title, 14, Color.WHITE, true);
        box.addView(titleView);
        if (description != null && !description.isEmpty()) {
            TextView body = text(description, 11, MUTED, false);
            body.setPadding(0, dp(6), 0, 0);
            box.addView(body);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        page.addView(box, lp);
        return box;
    }

    private Button button(String label, boolean secondary, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setBackground(shape(secondary ? PANEL_2 : PURPLE,
                secondary ? Color.rgb(88, 60, 111) : 0, 11));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(5), 0, dp(5));
        page.addView(b, lp);
        return b;
    }

    private EditText edit(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(13);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setText(value == null ? "" : value);
        e.setPadding(dp(13), dp(10), dp(13), dp(10));
        e.setBackground(shape(PANEL_2, Color.rgb(89, 62, 111), 10));
        return e;
    }

    private void renderHome() {
        page.removeAllViews();
        heading("WELCOME TO VORTEX", "YOUR SAN ANDREAS ROLEPLAY STARTS HERE");

        LinearLayout hero = card("VORTEX ROLEPLAY",
                "A mobile multiplayer launcher with server profiles, client selection and a dedicated CEF interface workspace.");
        TextView status = text("●  LAUNCHER READY", 10, Color.rgb(88, 225, 154), true);
        status.setPadding(0, dp(12), 0, 0);
        hero.addView(status);

        String nickname = prefs.getString("nickname", "");
        String picked = nickname.isEmpty() ? "Set your nickname in Settings" : nickname;
        card("PLAYER PROFILE", "PLAYER  •  " + picked + "\nSAVED SERVERS  •  " + loadServers().size()
                + "\nGAME CLIENT  •  " + selectedGameName());

        button("PLAY VORTEX RP", false, this::launchGame);
        button("BROWSE SERVERS", true, () -> showTab("SERVERS"));

        LinearLayout serverCard = card("QUICK CONNECT", "Choose a saved server from the Servers tab. The address is copied before the selected SA-MP client opens.");
        Server selected = selectedServer();
        if (selected == null) {
            serverCard.addView(text("NO SERVER SELECTED", 11, RED, true));
            TextView hint = text("Add your VORTEX server address to get started.", 10, MUTED, false);
            hint.setPadding(0, dp(6), 0, 0);
            serverCard.addView(hint);
        } else {
            serverCard.addView(text(selected.name, 15, Color.WHITE, true));
            serverCard.addView(text(selected.address(), 11, PURPLE, true));
            Button connect = new Button(this);
            connect.setText("CONNECT / OPEN GAME");
            connect.setAllCaps(false);
            connect.setTextColor(Color.WHITE);
            connect.setBackground(shape(RED, 0, 10));
            connect.setOnClickListener(v -> launchGame());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
            lp.setMargins(0, dp(10), 0, 0);
            serverCard.addView(connect, lp);
        }

        card("CLIENT REQUIREMENT",
                "VORTEX opens an installed SA-MP mobile client. Automatic joining and in-game CEF require compatible client-side support; the launcher cannot inject CEF into another app.");
    }

    private void renderServers() {
        page.removeAllViews();
        heading("SERVER BROWSER", "SAVE YOUR FAVOURITE SA-MP / OPEN.MP SERVERS");
        button("+ ADD SERVER", false, this::showAddServerDialog);

        List<Server> servers = loadServers();
        if (servers.isEmpty()) {
            card("NO SAVED SERVERS YET",
                    "Tap ADD SERVER to save a server name, IP address and port. Your server list is stored on this phone.");
            return;
        }

        String selectedAddress = prefs.getString("selected_server", "");
        for (Server s : servers) {
            LinearLayout item = card(s.name, s.address());
            TextView selected = text(s.address().equals(selectedAddress) ? "★  SELECTED SERVER" : "TAP CONNECT TO SELECT",
                    9, s.address().equals(selectedAddress) ? PURPLE : MUTED, true);
            selected.setPadding(0, dp(7), 0, dp(4));
            item.addView(selected);

            LinearLayout actions = row();
            Button connect = new Button(this);
            connect.setText("CONNECT");
            connect.setAllCaps(false);
            connect.setTextColor(Color.WHITE);
            connect.setTextSize(11);
            connect.setBackground(shape(PURPLE, 0, 9));
            connect.setOnClickListener(v -> {
                prefs.edit().putString("selected_server", s.address()).apply();
                copyText(s.address(), "Server address copied");
                Toast.makeText(this, "Selected. Opening your game client…", Toast.LENGTH_LONG).show();
                launchGame();
            });
            Button remove = new Button(this);
            remove.setText("REMOVE");
            remove.setAllCaps(false);
            remove.setTextColor(Color.WHITE);
            remove.setTextSize(11);
            remove.setBackground(shape(PANEL_2, RED, 9));
            remove.setOnClickListener(v -> removeServer(s));
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(42), 1);
            p1.setMargins(0, dp(8), dp(5), 0);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(42), 1);
            p2.setMargins(dp(5), dp(8), 0, 0);
            actions.addView(connect, p1);
            actions.addView(remove, p2);
            item.addView(actions);
        }
    }

    private void renderCef() {
        page.removeAllViews();
        heading("VORTEX CEF", "IN-GAME WEB INTERFACE MODULE");
        LinearLayout ready = card("CEF HUD FILES PREPARED",
                "The VORTEX HTML, CSS and JavaScript interface is in the repository under cef/client-assets. The Pawn sample is under cef/server.");
        ready.addView(text("●  WEB UI + EVENT HANDLERS", 10, Color.rgb(88, 225, 154), true));
        card("IN-GAME STATUS: NOT INTEGRATED",
                "A real SA-MP CEF client needs the upstream Java AAR, native C++ library, RakNet packet handling and hooks inside the game client. This launcher cannot add those to a separately installed SA-MP app.");
        button("OPEN CEF CLIENT INSTRUCTIONS", false, () ->
                openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/tree/main/cef"));
        button("OPEN UPSTREAM CEF DOCUMENTATION", true, () ->
                openUrl("https://github.com/denis-akazuki/samp-mobile-cef"));
        TextView note = text("The HUD preview is separate from the live in-game CEF renderer. To finish that integration, the exact game-client source is required.", 10, MUTED, false);
        note.setPadding(dp(3), dp(3), dp(3), 0);
        page.addView(note);
    }

    private void renderSettings() {
        page.removeAllViews();
        heading("SETTINGS", "PERSONALISE YOUR VORTEX LAUNCHER");

        LinearLayout profile = card("PLAYER NICKNAME", "Saved locally and used as your launcher profile name.");
        EditText nick = edit("Your in-game nickname", prefs.getString("nickname", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        profile.addView(nick);
        Button saveNick = new Button(this);
        saveNick.setText("SAVE NICKNAME");
        saveNick.setAllCaps(false);
        saveNick.setTextColor(Color.WHITE);
        saveNick.setBackground(shape(PURPLE, 0, 10));
        saveNick.setOnClickListener(v -> {
            String value = nick.getText().toString().trim();
            if (value.length() > 24) {
                Toast.makeText(this, "Nickname must be 24 characters or fewer.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("nickname", value).apply();
            Toast.makeText(this, "Nickname saved.", Toast.LENGTH_SHORT).show();
        });
        profile.addView(saveNick, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout client = card("GAME CLIENT", "Select the SA-MP mobile app already installed on your device.");
        client.addView(text("SELECTED  •  " + selectedGameName(), 11, PURPLE, true));
        Button choose = new Button(this);
        choose.setText("CHOOSE INSTALLED CLIENT");
        choose.setAllCaps(false);
        choose.setTextColor(Color.WHITE);
        choose.setBackground(shape(PANEL_2, PURPLE, 10));
        choose.setOnClickListener(v -> chooseGame());
        client.addView(choose, new LinearLayout.LayoutParams(-1, dp(44)));

        card("CEF SETTINGS",
                "CEF is a game-client feature, not a launcher permission. In-game rendering will become available after the native CEF client module is integrated.");
        card("ABOUT", "VORTEX RP Mobile Launcher • Version 2.0\nIndependent community project. Not affiliated with Rockstar Games or Take-Two Interactive.");
    }

    private String selectedGameName() {
        if (selectedPackage == null || selectedPackage.isEmpty()) return "NOT SELECTED";
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(selectedPackage, 0)));
        } catch (Exception e) {
            return "NOT INSTALLED";
        }
    }

    private List<Server> loadServers() {
        List<Server> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("servers", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "Server");
                String host = o.optString("host", "");
                int port = o.optInt("port", 7777);
                if (!host.isEmpty() && port > 0 && port <= 65535) list.add(new Server(name, host, port));
            }
        } catch (Exception ignored) { }
        return list;
    }

    private void saveServers(List<Server> servers) {
        JSONArray arr = new JSONArray();
        for (Server s : servers) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", s.name);
                o.put("host", s.host);
                o.put("port", s.port);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("servers", arr.toString()).apply();
    }

    private Server selectedServer() {
        String selected = prefs.getString("selected_server", "");
        for (Server s : loadServers()) if (s.address().equals(selected)) return s;
        return null;
    }

    private void showAddServerDialog() {
        LinearLayout form = column();
        form.setPadding(dp(18), dp(6), dp(18), 0);
        EditText name = edit("Server name", "VORTEX RP", InputType.TYPE_CLASS_TEXT);
        EditText host = edit("Server IP / hostname", "", InputType.TYPE_CLASS_TEXT);
        EditText port = edit("Port", "7777", InputType.TYPE_CLASS_NUMBER);
        form.addView(name);
        gapIn(form, 8);
        form.addView(host);
        gapIn(form, 8);
        form.addView(port);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ADD SERVER")
                .setView(form)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String serverName = name.getText().toString().trim();
            String serverHost = host.getText().toString().trim();
            int serverPort;
            try { serverPort = Integer.parseInt(port.getText().toString().trim()); }
            catch (Exception e) { serverPort = -1; }

            if (serverName.isEmpty() || serverHost.isEmpty() || serverPort < 1 || serverPort > 65535) {
                Toast.makeText(this, "Enter a server name, host and valid port.", Toast.LENGTH_SHORT).show();
                return;
            }
            List<Server> list = loadServers();
            list.add(new Server(serverName, serverHost, serverPort));
            saveServers(list);
            prefs.edit().putString("selected_server", serverHost + ":" + serverPort).apply();
            dialog.dismiss();
            renderServers();
        }));
        dialog.show();
    }

    private void gapIn(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void removeServer(Server server) {
        List<Server> list = loadServers();
        List<Server> kept = new ArrayList<>();
        for (Server s : list) if (!s.address().equals(server.address())) kept.add(s);
        saveServers(kept);
        if (server.address().equals(prefs.getString("selected_server", ""))) {
            prefs.edit().remove("selected_server").apply();
        }
        renderServers();
    }

    private void copyText(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("VORTEX RP", value));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseGame() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(query, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(
                        String.valueOf(b.loadLabel(getPackageManager())));
            }
        });
        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo info : apps) {
            if (!info.activityInfo.packageName.equals(getPackageName())) filtered.add(info);
        }
        if (filtered.isEmpty()) {
            Toast.makeText(this, "No launchable apps found.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            ResolveInfo info = filtered.get(i);
            labels[i] = info.loadLabel(getPackageManager()) + "  •  " + info.activityInfo.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("SELECT SA-MP CLIENT")
                .setItems(labels, (dialog, which) -> {
                    selectedPackage = filtered.get(which).activityInfo.packageName;
                    prefs.edit().putString("game_package", selectedPackage).apply();
                    Toast.makeText(this, "Game client saved.", Toast.LENGTH_SHORT).show();
                    renderSettings();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void launchGame() {
        if (selectedPackage == null || selectedPackage.isEmpty()) {
            Toast.makeText(this, "Choose your installed SA-MP client first.", Toast.LENGTH_LONG).show();
            chooseGame();
            return;
        }
        Server server = selectedServer();
        if (server != null) {
            copyText(server.address(), "Copied " + server.address() + " — opening game");
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch == null) {
            Toast.makeText(this, "The selected client is no longer installed. Choose it again.", Toast.LENGTH_LONG).show();
            selectedPackage = "";
            prefs.edit().remove("game_package").apply();
            renderHome();
            return;
        }
        try {
            startActivity(launch);
            if (server != null) {
                Toast.makeText(this, "Open the server browser in the game and use the copied address.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the selected game client.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!"HOME".equals(currentTab)) {
            showTab("HOME");
            return;
        }
        super.onBackPressed();
    }
}
