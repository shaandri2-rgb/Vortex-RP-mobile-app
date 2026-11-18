package com.vortexrp.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 7, 14);
    private static final int PANEL = Color.rgb(20, 19, 31);
    private static final int PANEL_2 = Color.rgb(31, 29, 45);
    private static final int RED = Color.rgb(246, 49, 76);
    private static final int PURPLE = Color.rgb(162, 66, 255);
    private static final int MUTED = Color.rgb(155, 157, 177);
    private static final int GREEN = Color.rgb(74, 220, 143);
    private static final int AMBER = Color.rgb(255, 185, 76);
    private static final int REQUEST_CEF_FOLDER = 1102;
    private static final int REQUEST_MOD_FOLDER = 1103;
    private static final int REQUEST_GAME_ZIP = 1104;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout page;
    private LinearLayout bottomBar;
    private TextView taskStatus;
    private ProgressBar taskProgress;
    private String currentTab = "HOME";
    private String serverFilter = "ALL";
    private JSONArray newsItems = new JSONArray();
    private String taskMessage = "Ready.";
    private int taskPercent = 0;
    private boolean taskBusy = false;
    private boolean newsLoading = false;
    private boolean queryingServers = false;
    private String selectedSettingsTab = "GENERAL";
    private String downloadsSubTab = "GAME DATA";
    private final List<ServerEntry> servers = new ArrayList<>();
    private final List<ServerEntry> hostedServers = new ArrayList<>();
    private boolean hostedLoading = false;

    private static final class ServerEntry {
        String name;
        String host;
        int port;
        boolean favorite;
        ServerQuery.Info info;
        String error = "Not queried";

        ServerEntry(String name, String host, int port, boolean favorite) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.favorite = favorite;
        }

        String address() { return host + ":" + port; }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vortex_launcher_v3", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        loadServers();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }
        setContentView(root);
        buildHeader();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15), dp(15), dp(15), dp(24));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(7), dp(6), dp(7));
        bottomBar.setBackgroundColor(Color.rgb(13, 12, 20));
        root.addView(bottomBar);
        updateNavigation();
        renderHome();
        refreshServers(false);
        loadHostedServers(false);
        loadNews(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradient(int first, int second, int radius) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{first, second});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, int size, int color, boolean bold) {
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

    private void buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(17), dp(10), dp(17), dp(10));

        TextView mark = label("V", 24, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(gradient(RED, PURPLE, 11));
        bar.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout brand = column();
        brand.setPadding(dp(11), 0, 0, 0);
        brand.addView(label("VORTEX RP", 16, Color.WHITE, true));
        brand.addView(label("SAN ANDREAS MULTIPLAYER", 8, MUTED, true));
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView status = label(lastOnlineText(), 8, lastOnlineColor(), true);
        status.setSingleLine(true);
        status.setPadding(dp(9), dp(7), dp(9), dp(7));
        status.setBackground(shape(PANEL_2, Color.rgb(67, 54, 84), 20));
        bar.addView(status);
        root.addView(bar);
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(43, 35, 55));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void updateNavigation() {
        bottomBar.removeAllViews();
        String[] tabs = {"HOME", "SERVERS", "DOWNLOADS", "SETTINGS"};
        String[] captions = {"⌂", "▤", "⇩", "⚙"};
        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];
            boolean active = currentTab.equals(tab);
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            TextView icon = label(captions[i], 19, active ? Color.WHITE : MUTED, true);
            icon.setGravity(Gravity.CENTER);
            TextView name = label(tab, 8, active ? Color.WHITE : MUTED, true);
            name.setGravity(Gravity.CENTER);
            item.addView(icon);
            item.addView(name);
            item.setPadding(0, dp(4), 0, dp(4));
            item.setBackground(shape(active ? PANEL_2 : Color.TRANSPARENT, active ? PURPLE : 0, 9));
            item.setOnClickListener(v -> showTab(tab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            bottomBar.addView(item, lp);
        }
    }

    private void showTab(String tab) {
        currentTab = tab;
        updateNavigation();
        if ("HOME".equals(tab)) renderHome();
        else if ("SERVERS".equals(tab)) renderServers();
        else if ("DOWNLOADS".equals(tab)) {
            if ("CEF FILES".equals(downloadsSubTab)) renderCefs();
            else renderGameData();
        } else renderSettings();
    }

    private void heading(String title, String subtitle) {
        page.addView(label(title, 21, Color.WHITE, true));
        TextView sub = label(subtitle, 9, MUTED, true);
        sub.setPadding(0, dp(4), 0, dp(14));
        page.addView(sub);
    }

    private LinearLayout card(String title, String description) {
        LinearLayout box = column();
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(shape(PANEL, Color.rgb(49, 43, 65), 14));
        box.addView(label(title, 12, Color.WHITE, true));
        if (description != null && !description.isEmpty()) {
            TextView body = label(description, 10, MUTED, false);
            body.setPadding(0, dp(6), 0, 0);
            box.addView(body);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        page.addView(box, lp);
        return box;
    }

    private Button button(String title, boolean secondary, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setBackground(secondary ? shape(PANEL_2, Color.rgb(83, 66, 105), 10)
                : gradient(RED, PURPLE, 10));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(45));
        lp.setMargins(0, dp(4), 0, dp(4));
        page.addView(b, lp);
        return b;
    }

    private Button compactButton(String title, boolean secondary, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(9);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(36));
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(shape(secondary ? PANEL_2 : RED, secondary ? Color.rgb(77, 61, 100) : 0, 9));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private EditText edit(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(12);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setText(value == null ? "" : value);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(shape(PANEL_2, Color.rgb(82, 66, 101), 9));
        return e;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private String selectedAddress() {
        return prefs.getString("selected_server", "");
    }

    private ServerEntry selectedServer() {
        String address = selectedAddress();
        for (ServerEntry server : servers) if (server.address().equals(address)) return server;
        return servers.isEmpty() ? null : servers.get(0);
    }

    private String nickname() {
        String n = prefs.getString("nickname", "");
        return n.isEmpty() ? "Guest" : n;
    }

    private void renderHome() {
        page.removeAllViews();
        ServerEntry selected = selectedServer();

        gap(page, 12);
        TextView eyebrow = label("WELCOME TO", 10, Color.rgb(191, 166, 210), true);
        eyebrow.setGravity(Gravity.CENTER);
        page.addView(eyebrow);

        TextView title = label("VORTEX RP", 35, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.08f);
        title.setPadding(0, dp(3), 0, 0);
        page.addView(title);

        TextView subtitle = label("GEORGIAN ROLEPLAY COMMUNITY", 9, Color.rgb(187, 174, 202), true);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(3), 0, dp(16));
        page.addView(subtitle);

        View accent = new View(this);
        accent.setBackground(gradient(RED, PURPLE, 4));
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(76), dp(3));
        accentLp.gravity = Gravity.CENTER;
        accentLp.setMargins(0, 0, 0, dp(23));
        page.addView(accent, accentLp);

        LinearLayout serverPanel = column();
        serverPanel.setPadding(dp(15), dp(13), dp(15), dp(13));
        serverPanel.setBackground(gradient(Color.rgb(33, 23, 48), Color.rgb(17, 16, 28), 13));
        LinearLayout serverTop = row();
        TextView serverCaption = label("SELECTED SERVER", 8, MUTED, true);
        serverTop.addView(serverCaption, new LinearLayout.LayoutParams(0, -2, 1));
        TextView serverStatus = label(lastOnlineText(), 8, lastOnlineColor(), true);
        serverTop.addView(serverStatus);
        serverPanel.addView(serverTop);

        LinearLayout serverMain = row();
        TextView serverIcon = label("▣", 21, Color.rgb(117, 194, 255), true);
        serverIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(43), dp(43));
        iconLp.setMargins(0, dp(10), dp(11), 0);
        serverMain.addView(serverIcon, iconLp);

        LinearLayout serverInfo = column();
        serverInfo.setGravity(Gravity.CENTER_VERTICAL);
        serverInfo.addView(label(selected == null ? "No server selected" : selected.name, 13, Color.WHITE, true));
        serverInfo.addView(label(selected == null ? "Add the VORTEX RP address" : selected.address(), 9, MUTED, false));
        serverMain.addView(serverInfo, new LinearLayout.LayoutParams(0, dp(47), 1));

        LinearLayout count = column();
        count.setGravity(Gravity.CENTER);
        count.addView(label(selected == null || selected.info == null ? "—/—" :
                selected.info.players + "/" + selected.info.maxPlayers, 13, Color.WHITE, true));
        count.addView(label("PLAYERS", 7, MUTED, true));
        serverMain.addView(count);
        serverPanel.addView(serverMain);

        LinearLayout.LayoutParams serverLp = new LinearLayout.LayoutParams(-1, -2);
        serverLp.setMargins(0, 0, 0, dp(15));
        page.addView(serverPanel, serverLp);

        Button play = new Button(this);
        play.setText("▶     PLAY");
        play.setAllCaps(false);
        play.setTextSize(17);
        play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        play.setTextColor(Color.WHITE);
        play.setElevation(dp(5));
        play.setBackground(gradient(Color.rgb(235, 49, 77), Color.rgb(139, 48, 194), 13));
        play.setOnClickListener(v -> {
            ServerEntry current = selectedServer();
            if (current == null) {
                showTab("SERVERS");
                toast("Add or select the VORTEX RP server first.");
            } else {
                showConnectDialog(current);
            }
        });
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, dp(62));
        playLp.setMargins(0, 0, 0, dp(8));
        page.addView(play, playLp);

        Button serverBrowser = new Button(this);
        serverBrowser.setText("SERVER BROWSER");
        serverBrowser.setAllCaps(false);
        serverBrowser.setTextSize(11);
        serverBrowser.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        serverBrowser.setTextColor(Color.rgb(223, 215, 236));
        serverBrowser.setBackground(shape(Color.rgb(28, 26, 39), Color.rgb(71, 59, 88), 11));
        serverBrowser.setOnClickListener(v -> showTab("SERVERS"));
        LinearLayout.LayoutParams browserLp = new LinearLayout.LayoutParams(-1, dp(44));
        browserLp.setMargins(0, 0, 0, dp(22));
        page.addView(serverBrowser, browserLp);

        LinearLayout playerLine = row();
        playerLine.setPadding(dp(2), dp(5), dp(2), dp(5));
        TextView playerLabel = label("PLAYER", 9, MUTED, true);
        playerLine.addView(playerLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView playerName = label(nickname(), 11, Color.WHITE, true);
        playerLine.addView(playerName);
        Button edit = compactButton("EDIT", true, this::showNicknameDialog);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(67), dp(33));
        editLp.setMargins(dp(9), 0, 0, 0);
        playerLine.addView(edit, editLp);
        page.addView(playerLine);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(49, 42, 61));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(0, dp(7), 0, dp(11));
        page.addView(divider, dividerLp);

        boolean resourcesReady = GameResourcesManager.hasInstalledData(this);
        LinearLayout statusLine = row();
        TextView resources = label(resourcesReady ? "● GAME DATA INSTALLED" : "● GAME DATA NOT INSTALLED",
                8, resourcesReady ? GREEN : AMBER, true);
        statusLine.addView(resources, new LinearLayout.LayoutParams(0, -2, 1));
        Button install = compactButton("INSTALL", true, () -> showTab("DOWNLOADS"));
        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(dp(82), dp(33));
        statusLine.addView(install, installLp);
        page.addView(statusLine);

        if (newsItems.length() > 0) {
            gap(page, 18);
            TextView newsHeading = label("LATEST VORTEX NEWS", 9, MUTED, true);
            page.addView(newsHeading);
            JSONObject first = newsItems.optJSONObject(0);
            if (first != null) {
                TextView newsTitle = label(first.optString("title", "VORTEX update"), 11, Color.WHITE, true);
                newsTitle.setPadding(0, dp(7), 0, dp(2));
                page.addView(newsTitle);
                TextView newsBody = label(first.optString("body", first.optString("description", "")),
                        9, MUTED, false);
                page.addView(newsBody);
            }
        }

        gap(page, 16);
        TextView refresh = label("↻  REFRESH SERVER STATUS", 9, Color.rgb(168, 143, 190), true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(0, dp(7), 0, dp(7));
        refresh.setOnClickListener(v -> {
            refreshServers(true);
            loadNews(true);
        });
        page.addView(refresh);
    }

    private int lastOnlineColor() {
        ServerEntry s = selectedServer();
        return s != null && s.info != null ? GREEN : AMBER;
    }

    private String lastOnlineText() {
        ServerEntry s = selectedServer();
        if (s == null) return "●  NO SERVER";
        if (s.info == null) return "●  NOT VERIFIED";
        return "●  " + s.info.players + " ONLINE";
    }

    private void renderServers() {
        page.removeAllViews();
        heading("SERVER BROWSER", "CHOOSE A SERVER AND CONNECT TO VORTEX RP");

        LinearLayout toolbar = row();
        String[] filters = {"ALL", "FAVORITES", "HOSTED"};
        String[] titles = {"MY SERVERS", "★ FAVORITES", "HOSTED"};
        for (int i = 0; i < filters.length; i++) {
            String filter = filters[i];
            Button tab = compactButton(titles[i], filter.equals(serverFilter), () -> {
                serverFilter = filter;
                renderServers();
                if ("HOSTED".equals(filter)) loadHostedServers(false);
            });
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(39), 1);
            tabLp.setMargins(dp(2), 0, dp(2), dp(7));
            toolbar.addView(tab, tabLp);
        }
        page.addView(toolbar);
        Button add = compactButton("+ ADD SERVER IP", false, this::showAddServerDialog);
        page.addView(add, new LinearLayout.LayoutParams(-1, dp(38)));

        List<ServerEntry> source = "HOSTED".equals(serverFilter) ? hostedServers : servers;
        int shown = 0;
        for (ServerEntry server : source) {
            if ("FAVORITES".equals(serverFilter) && !server.favorite) continue;
            shown++;
            addServerCard(server, "HOSTED".equals(serverFilter));
        }
        if (shown == 0) {
            if ("HOSTED".equals(serverFilter)) {
                card("HOSTED DIRECTORY", hostedLoading
                        ? "Loading the configured server directory…"
                        : "No hosted server feed is configured yet. Add a trusted HTTPS JSON directory URL in Settings.");
                button("CONFIGURE HOSTED DIRECTORY", true, () -> {
                    selectedSettingsTab = "GENERAL";
                    showTab("SETTINGS");
                });
            } else {
                card("NO SERVERS FOUND", "Add your VORTEX RP server IP and port. Saved servers stay on this device.");
                button("＋ ADD SERVER ADDRESS", false, this::showAddServerDialog);
            }
        }

        button(queryingServers ? "QUERYING SERVERS…" : "↻  REFRESH SERVER LIST", true, () -> refreshServers(true));
        if ("HOSTED".equals(serverFilter)) {
            button(hostedLoading ? "LOADING DIRECTORY…" : "↻  REFRESH HOSTED DIRECTORY", true,
                    () -> loadHostedServers(true));
        }

        card("SERVER QUERY", "Player count and ping are read from each server's SA-MP information endpoint. Servers that disable queries are shown as unverified, not falsely marked offline.");
    }

    private void addServerCard(ServerEntry server, boolean hosted) {
        LinearLayout item = column();
        item.setPadding(dp(13), dp(12), dp(13), dp(12));
        item.setBackground(shape(PANEL, Color.rgb(48, 43, 63), 13));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.setMargins(0, 0, 0, dp(9));
        page.addView(item, outer);

        LinearLayout titleRow = row();
        TextView dot = label(server.info == null ? "●" : "●", 14,
                server.info == null ? AMBER : GREEN, true);
        titleRow.addView(dot, new LinearLayout.LayoutParams(dp(22), -2));
        LinearLayout nameCol = column();
        nameCol.addView(label(server.name, 12, Color.WHITE, true));
        nameCol.addView(label(server.address(), 9, MUTED, false));
        titleRow.addView(nameCol, new LinearLayout.LayoutParams(0, -2, 1));
        Button star = compactButton(server.favorite ? "★" : "☆", true, () -> {
            if (hosted) {
                ServerEntry saved = findSavedServer(server.address());
                if (saved == null) {
                    ServerEntry copy = new ServerEntry(server.name, server.host, server.port, true);
                    copy.info = server.info;
                    servers.add(copy);
                    server.favorite = true;
                } else {
                    saved.favorite = !saved.favorite;
                    server.favorite = saved.favorite;
                }
            } else {
                server.favorite = !server.favorite;
            }
            saveServers();
            renderServers();
        });
        titleRow.addView(star, new LinearLayout.LayoutParams(dp(42), dp(35)));
        item.addView(titleRow);

        LinearLayout details = row();
        addServerMetric(details, server.info == null ? "—/—" :
                server.info.players + "/" + server.info.maxPlayers, "PLAYERS", Color.rgb(94, 188, 255));
        addServerMetric(details, server.info == null ? "—" : server.info.pingMs + "ms", "PING",
                server.info == null ? MUTED : GREEN);
        addServerMetric(details, server.info == null ? "UNKNOWN" : server.info.gameMode, "GAMEMODE", MUTED);
        addServerMetric(details, server.info == null ? "UNKNOWN" : server.info.language, "LANGUAGE", MUTED);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
        detailsLp.setMargins(0, dp(10), 0, dp(6));
        item.addView(details, detailsLp);

        LinearLayout actions = row();
        Button select = compactButton("SELECT", true, () -> {
            prefs.edit().putString("selected_server", server.address()).apply();
            renderServers();
            toast(server.name + " selected.");
        });
        Button connect = compactButton("CONNECT  ▶", false, () -> showConnectDialog(server));
        Button remove = compactButton("REMOVE", true, () -> {
            new AlertDialog.Builder(this)
                    .setTitle("REMOVE SERVER?")
                    .setMessage("Remove " + server.name + " from your saved server list?")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("REMOVE", (d, w) -> {
                        servers.remove(server);
                        saveServers();
                        if (server.address().equals(selectedAddress())) {
                            prefs.edit().remove("selected_server").apply();
                        }
                        renderServers();
                    }).show();
        });
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        actionLp.setMargins(0, dp(4), dp(3), 0);
        actions.addView(select, actionLp);
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        connectLp.setMargins(dp(3), dp(4), dp(3), 0);
        actions.addView(connect, connectLp);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        removeLp.setMargins(dp(3), dp(4), 0, 0);
        actions.addView(remove, removeLp);
        item.addView(actions);
    }

    private void addServerMetric(LinearLayout row, String value, String caption, int color) {
        LinearLayout metric = column();
        metric.setGravity(Gravity.CENTER);
        metric.addView(label(value, 9, color, true));
        metric.addView(label(caption, 7, MUTED, true));
        row.addView(metric, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void showConnectDialog(ServerEntry server) {
        prefs.edit().putString("selected_server", server.address()).apply();
        LinearLayout box = column();
        box.setPadding(dp(12), dp(7), dp(12), dp(3));
        LinearLayout stats = row();
        addDialogStat(stats, server.info == null ? "—/—" :
                server.info.players + "/" + server.info.maxPlayers, "PLAYERS", Color.rgb(94, 188, 255));
        addDialogStat(stats, server.info == null ? "—" : server.info.pingMs + " ms", "PING", GREEN);
        addDialogStat(stats, server.info == null ? "UNKNOWN" : server.info.gameMode, "GAMEMODE", MUTED);
        box.addView(stats);
        TextView address = label(server.address(), 10, MUTED, false);
        address.setPadding(0, dp(13), 0, 0);
        box.addView(address);
        if (server.info == null) {
            TextView warning = label("Server info has not been verified yet. You can still open your installed client.", 9, AMBER, false);
            warning.setPadding(0, dp(7), 0, 0);
            box.addView(warning);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(server.name)
                .setView(box)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CONNECT", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss();
            play(server);
        }));
        dialog.show();
    }

    private void addDialogStat(LinearLayout row, String value, String caption, int color) {
        LinearLayout item = column();
        item.setPadding(dp(5), dp(10), dp(5), dp(10));
        item.setGravity(Gravity.CENTER);
        item.setBackground(shape(PANEL_2, 0, 9));
        item.addView(label(value, 11, color, true));
        item.addView(label(caption, 7, MUTED, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(item, lp);
    }

    private void showAddServerDialog() {
        LinearLayout form = column();
        form.setPadding(dp(10), dp(5), dp(10), 0);
        EditText name = edit("Server name", "VORTEX RP", InputType.TYPE_CLASS_TEXT);
        EditText host = edit("IP address or hostname", "", InputType.TYPE_CLASS_TEXT);
        EditText port = edit("Port", "7777", InputType.TYPE_CLASS_NUMBER);
        form.addView(name);
        gap(form, 8);
        form.addView(host);
        gap(form, 8);
        form.addView(port);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ADD SERVER")
                .setView(form)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("ADD", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String h = host.getText().toString().trim();
            int p;
            try { p = Integer.parseInt(port.getText().toString().trim()); }
            catch (Exception e) { p = -1; }
            if (n.isEmpty() || h.isEmpty() || h.contains(" ") || p < 1 || p > 65535) {
                toast("Enter a server name, host and valid port.");
                return;
            }
            for (ServerEntry server : servers) {
                if (server.address().equalsIgnoreCase(h + ":" + p)) {
                    toast("This server is already in your list.");
                    return;
                }
            }
            ServerEntry added = new ServerEntry(n, h, p, true);
            servers.add(0, added);
            prefs.edit().putString("selected_server", added.address()).apply();
            saveServers();
            dialog.dismiss();
            renderServers();
            queryServer(added, true);
        }));
        dialog.show();
    }

    private void showNicknameDialog() {
        EditText input = edit("Enter your SA-MP nickname", nickname().equals("Guest") ? "" : nickname(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        new AlertDialog.Builder(this)
                .setTitle("PLAYER NICKNAME")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.length() < 1 || value.length() > 24 || value.contains(" ")) {
                        toast("Use 1–24 characters without spaces.");
                        return;
                    }
                    prefs.edit().putString("nickname", value).apply();
                    renderHome();
                    toast("Nickname saved.");
                }).show();
    }

    private void loadServers() {
        servers.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("servers_v1", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String host = o.optString("host", "").trim();
                int port = o.optInt("port", 7777);
                if (host.isEmpty() || port < 1 || port > 65535) continue;
                servers.add(new ServerEntry(o.optString("name", "Server"), host, port,
                        o.optBoolean("favorite", false)));
            }
        } catch (Exception ignored) { }
        if (servers.isEmpty()) {
            String legacyHost = prefs.getString("server_host", "").trim();
            int legacyPort = prefs.getInt("server_port", 7777);
            if (!legacyHost.isEmpty()) {
                servers.add(new ServerEntry("VORTEX RP", legacyHost, legacyPort, true));
                saveServers();
                prefs.edit().putString("selected_server", legacyHost + ":" + legacyPort).apply();
            }
        }
    }

    private void saveServers() {
        JSONArray arr = new JSONArray();
        for (ServerEntry s : servers) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", s.name);
                o.put("host", s.host);
                o.put("port", s.port);
                o.put("favorite", s.favorite);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString("servers_v1", arr.toString()).apply();
    }

    private void refreshServers(boolean showToast) {
        if (queryingServers || servers.isEmpty()) {
            if (showToast && servers.isEmpty()) toast("Add a server address first.");
            return;
        }
        queryingServers = true;
        io.execute(() -> {
            for (ServerEntry entry : new ArrayList<>(servers)) {
                try {
                    ServerQuery.Info info = ServerQuery.query(entry.host, entry.port, 2800);
                    main.post(() -> {
                        entry.info = info;
                        entry.error = "";
                        refreshVisibleServerViews();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        entry.info = null;
                        entry.error = e.getMessage() == null ? "Query unavailable" : e.getMessage();
                        refreshVisibleServerViews();
                    });
                }
            }
            main.post(() -> {
                queryingServers = false;
                refreshVisibleServerViews();
                if (showToast) toast("Server list query finished. Unavailable servers remain unverified.");
            });
        });
    }

    private void queryServer(ServerEntry entry, boolean showToast) {
        io.execute(() -> {
            try {
                ServerQuery.Info info = ServerQuery.query(entry.host, entry.port, 3500);
                main.post(() -> {
                    entry.info = info;
                    entry.error = "";
                    refreshVisibleServerViews();
                    if (showToast) toast("Server details loaded.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    entry.error = e.getMessage() == null ? "Query unavailable" : e.getMessage();
                    refreshVisibleServerViews();
                    if (showToast) toast("Server not verified: " + entry.error);
                });
            }
        });
    }

    private ServerEntry findSavedServer(String address) {
        for (ServerEntry s : servers) if (s.address().equalsIgnoreCase(address)) return s;
        return null;
    }

    private void loadHostedServers(boolean showToast) {
        String endpoint = prefs.getString("server_directory_url", "").trim();
        if (endpoint.isEmpty()) {
            hostedServers.clear();
            if ("SERVERS".equals(currentTab) && "HOSTED".equals(serverFilter)) renderServers();
            if (showToast) toast("Configure a hosted directory HTTPS JSON URL in Settings.");
            return;
        }
        if (!endpoint.toLowerCase(Locale.US).startsWith("https://")) {
            if (showToast) toast("Hosted directory must use HTTPS.");
            return;
        }
        if (hostedLoading) return;
        hostedLoading = true;
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "VORTEX-RP-Launcher/3.1");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("Directory returned HTTP " + status);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > 1024 * 1024) throw new IllegalStateException("Directory response is too large.");
                        out.write(buffer, 0, n);
                    }
                }
                String raw = new String(out.toByteArray(), StandardCharsets.UTF_8);
                Object parsed = raw.trim().startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
                JSONArray items = parsed instanceof JSONArray ? (JSONArray) parsed : ((JSONObject) parsed).optJSONArray("servers");
                if (items == null) throw new IllegalStateException("Directory JSON must be an array or contain a servers array.");
                List<ServerEntry> result = new ArrayList<>();
                for (int i = 0; i < Math.min(items.length(), 100); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String host = item.optString("host", item.optString("ip", "")).trim();
                    int port = item.optInt("port", 7777);
                    String name = item.optString("name", "Hosted SA-MP server");
                    if (host.isEmpty() || host.contains(" ") || port < 1 || port > 65535) continue;
                    ServerEntry entry = new ServerEntry(name, host, port, findSavedServer(host + ":" + port) != null);
                    result.add(entry);
                }
                main.post(() -> {
                    hostedLoading = false;
                    hostedServers.clear();
                    hostedServers.addAll(result);
                    for (int i = 0; i < Math.min(hostedServers.size(), 20); i++) {
                        queryServer(hostedServers.get(i), false);
                    }
                    if ("SERVERS".equals(currentTab) && "HOSTED".equals(serverFilter)) renderServers();
                    if (showToast) toast("Hosted directory loaded: " + result.size() + " servers.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    hostedLoading = false;
                    if ("SERVERS".equals(currentTab) && "HOSTED".equals(serverFilter)) renderServers();
                    if (showToast) toast("Hosted directory failed: " + e.getMessage());
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void refreshVisibleServerViews() {
        if ("SERVERS".equals(currentTab)) renderServers();
        else if ("HOME".equals(currentTab)) renderHome();
    }

    private void play(ServerEntry server) {
        prefs.edit().putString("selected_server", server.address()).apply();
        String game = prefs.getString("game_package", "");
        String client = prefs.getString("client_package", "");
        if (game.isEmpty()) {
            showTab("SETTINGS");
            toast("Select your installed GTA: San Andreas app first.");
            return;
        }
        if (!isInstalled(game)) {
            showTab("SETTINGS");
            toast("Your selected GTA package is not installed.");
            return;
        }
        if (client.isEmpty()) {
            showTab("SETTINGS");
            toast("Select your installed SA-MP client first.");
            return;
        }
        if (!isInstalled(client)) {
            showTab("SETTINGS");
            toast("Your selected multiplayer client is not installed.");
            return;
        }

        String template = prefs.getString("connect_uri_template", "").trim();
        String nick = prefs.getString("nickname", "");
        try {
            if (!template.isEmpty()) {
                String expanded = template.replace("{host}", server.host)
                        .replace("{port}", String.valueOf(server.port))
                        .replace("{nickname}", Uri.encode(nick));
                Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse(expanded));
                direct.setPackage(client);
                if (direct.resolveActivity(getPackageManager()) == null) {
                    toast("The selected client does not support the configured direct-connect URI.");
                    return;
                }
                startActivity(direct);
                return;
            }

            copyText(server.address(), "Server address copied");
            Intent launch = getPackageManager().getLaunchIntentForPackage(client);
            if (launch == null) {
                toast("The selected multiplayer client has no launch activity.");
                return;
            }
            startActivity(launch);
            new AlertDialog.Builder(this)
                    .setTitle("CLIENT OPENED")
                    .setMessage("The real installed multiplayer app has opened. " + server.address()
                            + " was copied to your clipboard. This client has no configured direct-connect bridge, so select the server in its own browser.\n\nNo connection is claimed until the game client confirms it.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            toast("Could not start the selected client: " + e.getMessage());
        }
    }


    private void renderGameData() {
        page.removeAllViews();
        heading("GAME RESOURCES", "INSTALL LITE OR FULL GTA: SAN ANDREAS DATA");
        addDownloadsSubNavigation();

        File installedDir = GameResourcesManager.getInstallDirectory(this);
        boolean ready = GameResourcesManager.hasInstalledData(this);
        LinearLayout status = card("LOCAL GAME DATA", ready
                ? "Game resources are installed in Test launcher storage."
                : "No game-data package is installed yet. Import a ZIP/OBB you own or configure a trusted HTTPS package source.");
        status.addView(label(ready
                ? "● INSTALLED • " + GameResourcesManager.getInstalledFileCount(this) + " files • " + formatBytes(GameResourcesManager.getInstalledSize(this))
                : "● NOT INSTALLED", 10, ready ? GREEN : AMBER, true));
        TextView path = label("Launcher storage: " + installedDir.getAbsolutePath(), 8, MUTED, false);
        path.setTextIsSelectable(true);
        path.setPadding(0, dp(7), 0, 0);
        status.addView(path);

        LinearLayout local = card("INSTALL FROM YOUR PHONE", "Select a ZIP or OBB made from your own legally obtained GTA: San Andreas data. The archive is extracted into this app's private game-data folder.");
        button("SELECT LOCAL GAME DATA ZIP / OBB", false, this::chooseLocalGameZip);
        card("REMOTE PACKAGES", "For downloads, use a direct HTTPS archive link and its SHA-256 checksum from a trusted source. Lite and Full are separate packages; this launcher does not host copyrighted GTA files.");
        addRemoteGamePackageCard("LITE RESOURCE PACK", "game_lite_url", "game_lite_sha256",
                "A smaller resource archive, when supplied by your own trusted host.");
        addRemoteGamePackageCard("FULL RESOURCE PACK", "game_full_url", "game_full_sha256",
                "A complete resource archive, when supplied by your own trusted host.");

        LinearLayout rollback = card("ROLLBACK", GameResourcesManager.hasBackup(this)
                ? "A previous game-data installation is available."
                : "No previous installation backup is available.");
        Button restore = compactButton("RESTORE PREVIOUS GAME DATA", true, this::confirmRestoreGameData);
        restore.setEnabled(GameResourcesManager.hasBackup(this));
        rollback.addView(restore, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout progress = card("INSTALLATION PROGRESS", "");
        taskStatus = label(taskMessage, 9, MUTED, false);
        progress.addView(taskStatus);
        taskProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        taskProgress.setMax(100);
        taskProgress.setIndeterminate(taskBusy);
        taskProgress.setProgress(taskPercent);
        progress.addView(taskProgress, new LinearLayout.LayoutParams(-1, dp(7)));

        card("IMPORTANT", "This installs data files inside Test launcher storage. The Th1nk SA-MP Android client still needs its own compatible GTA engine integration to run from this folder. Installing game data does not itself add the native SA-MP engine or CEF runtime.");
        card("PRIVACY + STORAGE", "The app uses its own Android app-specific storage. Android 15 does not allow this launcher to write into another app's private folder. Uninstalling Test launcher removes its private game-data copy.");
    }

    private void addRemoteGamePackageCard(String title, String urlKey, String hashKey, String description) {
        LinearLayout packageCard = card(title, description);
        EditText url = edit("Direct HTTPS ZIP download URL", prefs.getString(urlKey, ""),
                InputType.TYPE_TEXT_VARIATION_URI);
        EditText hash = edit("Archive SHA-256 (64 hex characters)", prefs.getString(hashKey, ""),
                InputType.TYPE_CLASS_TEXT);
        packageCard.addView(url);
        gap(packageCard, 7);
        packageCard.addView(hash);
        Button install = compactButton("SAVE + DOWNLOAD + INSTALL", false, () -> {
            String address = url.getText().toString().trim();
            String checksum = hash.getText().toString().trim().toLowerCase(Locale.US);
            if (!validHttps(address)) {
                toast("Enter a direct HTTPS ZIP link.");
                return;
            }
            if (!checksum.matches("[a-f0-9]{64}")) {
                toast("Enter the archive's 64-character SHA-256 checksum.");
                return;
            }
            prefs.edit().putString(urlKey, address).putString(hashKey, checksum).apply();
            confirmRemoteGameInstall(title, urlKey, hashKey);
        });
        packageCard.addView(install, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void chooseLocalGameZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_GAME_ZIP);
    }

    private void confirmInstallLocalGameZip(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("INSTALL LOCAL GAME DATA?")
                .setMessage("The selected ZIP/OBB will be checked and extracted into Test launcher storage. Any current installation will be kept as a rollback backup.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("INSTALL", (d, w) -> installLocalGameZip(uri))
                .show();
    }

    private void installLocalGameZip(Uri uri) {
        beginTask("Copying selected game-data archive…");
        io.execute(() -> {
            File archive = null;
            try {
                archive = GameResourcesManager.copyLocalZip(this, uri);
                main.post(() -> updateTask(1, 2, "Archive copied; validating and installing…"));
                int count = GameResourcesManager.install(this, archive, "LOCAL", 
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                main.post(() -> finishTask(true, "Installed " + count + " game resource files into Test launcher storage."));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown import error." : e.getMessage();
                main.post(() -> finishTask(false, "Game-data import failed: " + message));
            } finally {
                if (archive != null) archive.delete();
            }
        });
    }

    private void confirmRemoteGameInstall(String title, String urlKey, String hashKey) {
        new AlertDialog.Builder(this)
                .setTitle("INSTALL " + title + "?")
                .setMessage("The archive will be downloaded over HTTPS, checked against its SHA-256 checksum, and extracted into Test launcher storage. Existing game data will be kept as a rollback backup.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DOWNLOAD + INSTALL", (d, w) -> installRemoteGamePackage(
                        title, prefs.getString(urlKey, ""), prefs.getString(hashKey, "")))
                .show();
    }

    private void installRemoteGamePackage(String edition, String url, String sha256) {
        beginTask("Starting " + edition + " download…");
        io.execute(() -> {
            File archive = null;
            try {
                archive = GameResourcesManager.downloadVerified(this, url, sha256,
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                File verified = archive;
                main.post(() -> updateTask(1, 2, "Download verified; extracting package…"));
                int count = GameResourcesManager.install(this, verified, edition,
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                main.post(() -> finishTask(true, edition + ": installed " + count + " game resource files."));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown download/install error." : e.getMessage();
                main.post(() -> finishTask(false, edition + " install failed: " + message));
            } finally {
                if (archive != null) archive.delete();
            }
        });
    }

    private void confirmRestoreGameData() {
        if (!GameResourcesManager.hasBackup(this)) {
            toast("No previous game-data backup found.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("RESTORE PREVIOUS GAME DATA?")
                .setMessage("The current game-data folder will be replaced with the previous installation.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESTORE", (d, w) -> {
                    beginTask("Restoring previous game data…");
                    io.execute(() -> {
                        try {
                            GameResourcesManager.restoreBackup(this);
                            main.post(() -> finishTask(true, "Previous game data restored."));
                        } catch (Exception e) {
                            main.post(() -> finishTask(false, "Restore failed: " + e.getMessage()));
                        }
                    });
                }).show();
    }

    private void addDownloadsSubNavigation() {
        LinearLayout tabs = row();
        String[] choices = {"GAME DATA", "CEF FILES"};
        for (String choice : choices) {
            Button tab = compactButton(choice, !downloadsSubTab.equals(choice), () -> {
                downloadsSubTab = choice;
                if ("CEF FILES".equals(choice)) renderCefs();
                else renderGameData();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            lp.setMargins(0, 0, dp(5), dp(12));
            tabs.addView(tab, lp);
        }
        page.addView(tabs);
    }

    private void renderCefs() {
        page.removeAllViews();
        heading("DOWNLOADS & CEFS", "VORTEX GAME RESOURCES AND CLIENT ADD-ONS");
        addDownloadsSubNavigation();

        LinearLayout source = card("TRUSTED CEFS PACKAGE SOURCE",
                "Set an HTTPS ZIP location and the SHA-256 checksum given by the VORTEX administrator.");
        EditText url = edit("https://example.com/vortex-cefs.zip",
                prefs.getString("cef_url", ""), InputType.TYPE_TEXT_VARIATION_URI);
        EditText hash = edit("Package SHA-256 (64 hex characters)",
                prefs.getString("cef_sha256", ""), InputType.TYPE_CLASS_TEXT);
        source.addView(url);
        gap(source, 7);
        source.addView(hash);
        Button save = compactButton("SAVE PACKAGE SOURCE", false, () -> {
            String u = url.getText().toString().trim();
            String h = hash.getText().toString().trim().toLowerCase(Locale.US);
            if (!u.isEmpty() && !u.toLowerCase(Locale.US).startsWith("https://")) {
                toast("Only HTTPS package links are accepted.");
                return;
            }
            if (!h.isEmpty() && !h.matches("[a-f0-9]{64}")) {
                toast("SHA-256 must be 64 hexadecimal characters.");
                return;
            }
            prefs.edit().putString("cef_url", u).putString("cef_sha256", h).apply();
            toast("CEFS source saved.");
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(38));
        saveLp.setMargins(0, dp(8), 0, 0);
        source.addView(save, saveLp);

        LinearLayout folder = card("INSTALL LOCATION", "");
        String selected = prefs.getString("cef_tree_uri", "");
        folder.addView(label(selected.isEmpty() ? "No folder selected." :
                "A user-selected folder has been saved on this device.", 9,
                selected.isEmpty() ? AMBER : GREEN, false));
        Button chooseFolder = compactButton("CHOOSE GAME / CLIENT FOLDER", true, this::chooseCefsFolder);
        folder.addView(chooseFolder, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout state = card("PACKAGE STATUS", "");
        File archive = new File(getFilesDir(), "vortex-cefs-package.zip");
        state.addView(label(archive.isFile() ? "● VERIFIED PACKAGE DOWNLOADED" :
                "● NO VERIFIED PACKAGE", 10, archive.isFile() ? GREEN : AMBER, true));
        state.addView(label(CefsManager.hasBackup(this)
                ? "A backup is active. Restore it before installing another package."
                : "No active backup.", 9, MUTED, false));
        state.addView(label("The client must already contain native CEF support. Web files alone cannot add CEF to another app.", 9, MUTED, false));

        button("DOWNLOAD + VERIFY PACKAGE", false, this::downloadCefs);
        button("BACK UP + INSTALL CEFS", false, this::confirmInstallCefs);
        button("RESTORE ORIGINAL FILES", true, this::confirmRestoreCefs);

        LinearLayout progress = card("DOWNLOAD / INSTALL PROGRESS", "");
        taskStatus = label(taskMessage, 9, MUTED, false);
        progress.addView(taskStatus);
        taskProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        taskProgress.setMax(100);
        taskProgress.setIndeterminate(taskBusy);
        taskProgress.setProgress(taskPercent);
        progress.addView(taskProgress, new LinearLayout.LayoutParams(-1, dp(7)));

        card("SUPPORTED RESOURCES", "The manager handles files listed in a signed-by-hash VORTEX package manifest. It never downloads copyrighted GTA game data or unapproved executables.");
        button("OPEN CEFS PACKAGE FORMAT", true, () ->
                openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/blob/main/docs/cefs-package-format.md"));
    }

    private void renderSettings() {
        page.removeAllViews();
        heading("SETTINGS", "PERSONALISE YOUR VORTEX CLIENT");

        LinearLayout tabs = row();
        String[] choices = {"GENERAL", "CHAT", "MODS"};
        for (String choice : choices) {
            Button b = compactButton(choice, selectedSettingsTab.equals(choice), () -> {
                selectedSettingsTab = choice;
                renderSettings();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(39), 1);
            lp.setMargins(dp(3), 0, dp(3), dp(10));
            tabs.addView(b, lp);
        }
        page.addView(tabs);

        if ("GENERAL".equals(selectedSettingsTab)) {
            LinearLayout profile = card("PLAYER NICKNAME", "The nickname used by the client when the configured connection bridge accepts it.");
            EditText nick = edit("Nickname", prefs.getString("nickname", ""), InputType.TYPE_CLASS_TEXT);
            profile.addView(nick);
            Button saveNick = compactButton("SAVE NICKNAME", false, () -> {
                String value = nick.getText().toString().trim();
                if (value.isEmpty() || value.length() > 24 || value.contains(" ")) {
                    toast("Use 1–24 characters without spaces.");
                    return;
                }
                prefs.edit().putString("nickname", value).apply();
                toast("Nickname saved.");
            });
            profile.addView(saveNick, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout game = card("GTA: SAN ANDREAS", "Choose the legally installed GTA game package.");
            game.addView(label(packageSummary(prefs.getString("game_package", "")), 9, MUTED, false));
            Button chooseGame = compactButton("SELECT INSTALLED GTA APP", true, () -> chooseInstalledApp(true));
            game.addView(chooseGame, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout client = card("SA-MP MOBILE CLIENT", "Choose the multiplayer client that the Play button should open.");
            client.addView(label(packageSummary(prefs.getString("client_package", "")), 9, MUTED, false));
            Button chooseClient = compactButton("SELECT MULTIPLAYER CLIENT", true, () -> chooseInstalledApp(false));
            client.addView(chooseClient, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout clientDownload = card("GET AN ANDROID SA-MP CLIENT",
                    "Install a compatible third-party SA-MP Android client, then return here and select it above. The client APK does not include GTA: San Andreas game data.");
            Button getClient = compactButton("VIEW SA-MP ANDROID RELEASES", false,
                    () -> openUrl("https://github.com/SA-MP-Android/App/releases"));
            clientDownload.addView(getClient, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout server = card("DEFAULT SERVER", "The selected server is shown on the home screen.");
            server.addView(label(selectedServer() == null ? "No server selected." :
                    selectedServer().name + " • " + selectedServer().address(), 9, MUTED, false));
            Button openServers = compactButton("MANAGE SERVER LIST", true, () -> showTab("SERVERS"));
            server.addView(openServers, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout bridge = card("DIRECT CONNECT URI", "Only use a URI documented by your selected SA-MP client. Placeholders: {host}, {port}, {nickname}.");
            EditText template = edit("Client-documented URI template",
                    prefs.getString("connect_uri_template", ""), InputType.TYPE_TEXT_VARIATION_URI);
            bridge.addView(template);
            Button saveBridge = compactButton("SAVE CLIENT URI", true, () -> {
                String value = template.getText().toString().trim();
                if (value.toLowerCase(Locale.US).startsWith("javascript:") ||
                        value.toLowerCase(Locale.US).startsWith("file:")) {
                    toast("Unsupported URI scheme.");
                    return;
                }
                prefs.edit().putString("connect_uri_template", value).apply();
                toast(value.isEmpty() ? "Manual connect mode enabled." : "Direct-connect URI saved.");
            });
            bridge.addView(saveBridge, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout feeds = card("LIVE SERVER + NEWS FEEDS", "Optional HTTPS JSON feeds can populate the Hosted tab and VORTEX news panel.");
            EditText directory = edit("Hosted server directory HTTPS JSON URL",
                    prefs.getString("server_directory_url", ""), InputType.TYPE_TEXT_VARIATION_URI);
            EditText newsFeed = edit("News announcements HTTPS JSON URL",
                    prefs.getString("news_url", ""), InputType.TYPE_TEXT_VARIATION_URI);
            feeds.addView(directory);
            gap(feeds, 7);
            feeds.addView(newsFeed);
            Button saveFeeds = compactButton("SAVE FEED URLS", true, () -> {
                String dUrl = directory.getText().toString().trim();
                String nUrl = newsFeed.getText().toString().trim();
                if ((!dUrl.isEmpty() && !dUrl.toLowerCase(Locale.US).startsWith("https://")) ||
                    (!nUrl.isEmpty() && !nUrl.toLowerCase(Locale.US).startsWith("https://"))) {
                    toast("All feed URLs must use HTTPS.");
                    return;
                }
                prefs.edit().putString("server_directory_url", dUrl).putString("news_url", nUrl).apply();
                hostedServers.clear();
                newsItems = new JSONArray();
                toast("Feed URLs saved.");
            });
            feeds.addView(saveFeeds, new LinearLayout.LayoutParams(-1, dp(37)));
        } else if ("CHAT".equals(selectedSettingsTab)) {
            LinearLayout chat = card("CHAT CUSTOMISATION", "These preferences are stored locally for a future client bridge. They do not alter a third-party client's in-game UI unless that client supports the same settings.");
            addValueSetting(chat, "Chat font size", "chat_font_size", "15");
            addValueSetting(chat, "Chat lines", "chat_lines", "10");
            addValueSetting(chat, "Chat X position", "chat_x", "20");
            addValueSetting(chat, "Chat Y position", "chat_y", "100");
        } else {
            LinearLayout mods = card("LIBERTYCITY TXD + DFF INSTALLER",
                    "Paste the direct HTTPS download links for a matching TXD texture and DFF model from LibertyCity. A mod page URL is not a file download link.");
            EditText txd = edit("Direct .txd download URL", prefs.getString("mod_txd_url", ""),
                    InputType.TYPE_TEXT_VARIATION_URI);
            EditText dff = edit("Direct .dff download URL", prefs.getString("mod_dff_url", ""),
                    InputType.TYPE_TEXT_VARIATION_URI);
            mods.addView(txd);
            gap(mods, 7);
            mods.addView(dff);
            Button saveLinks = compactButton("SAVE TXD + DFF LINKS", true, () -> {
                String t = txd.getText().toString().trim();
                String d = dff.getText().toString().trim();
                if (!validHttps(t) || !validHttps(d)) {
                    toast("Enter both direct HTTPS download links.");
                    return;
                }
                prefs.edit().putString("mod_txd_url", t).putString("mod_dff_url", d).apply();
                toast("Mod download links saved.");
            });
            mods.addView(saveLinks, new LinearLayout.LayoutParams(-1, dp(38)));

            LinearLayout destination = card("GAME MOD FOLDER",
                    "Choose the mod folder that your specific Android GTA / SA-MP client supports. Android 15 does not let this launcher write into another app's private folder.");
            Uri modTree = parseUri(prefs.getString("mods_tree_uri", ""));
            destination.addView(label(modTree == null ? "No mod folder selected." :
                    "A mod folder has been selected on this device.", 9,
                    modTree == null ? AMBER : GREEN, false));
            Button chooseMods = compactButton("CHOOSE MOD FOLDER", true, this::chooseModsFolder);
            destination.addView(chooseMods, new LinearLayout.LayoutParams(-1, dp(38)));

            button("DOWNLOAD + INSTALL TXD AND DFF", false, this::confirmInstallMods);
            button("OPEN LIBERTYCITY GTA MODS", true,
                    () -> openUrl("https://www.libertycity.net/files/gta-san-andreas/"));

            card("COMPATIBILITY NOTE",
                    "The launcher can download and copy TXD/DFF files. Your installed GTA / SA-MP client must support loading mods from the selected folder. Some mobile clients require a specific mod-loader path or do not support external TXD/DFF mods at all.");
            card("SAFETY",
                    "Only install files you have permission to use. Backups are created before replacing files when the selected folder provider allows it. The installer does not install APKs, scripts or native libraries.");
        }
        card("ABOUT VORTEX", "VORTEX RP • Android launcher\nNot affiliated with Rockstar Games or Take-Two Interactive.");
    }

    private void addValueSetting(LinearLayout parent, String title, String key, String fallback) {
        EditText value = edit(title, prefs.getString(key, fallback), InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
        lp.setMargins(0, dp(7), 0, dp(3));
        parent.addView(value, lp);
        Button save = compactButton("SAVE " + title.toUpperCase(Locale.US), true, () -> {
            String v = value.getText().toString().trim();
            if (v.isEmpty()) {
                toast("Enter a value first.");
                return;
            }
            prefs.edit().putString(key, v).apply();
            toast(title + " saved locally.");
        });
        parent.addView(save, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private void chooseInstalledApp(boolean game) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = getPackageManager().queryIntentActivities(query, 0);
        List<ResolveInfo> matching = new ArrayList<>();
        for (ResolveInfo item : all) {
            String pkg = item.activityInfo.packageName;
            String name = String.valueOf(item.loadLabel(getPackageManager())).toLowerCase(Locale.US);
            String id = pkg.toLowerCase(Locale.US);
            boolean hit = game
                    ? name.contains("gta") || name.contains("san andreas") || id.contains("gtasa")
                    : name.contains("samp") || name.contains("sa-mp") || name.contains("multiplayer") || id.contains("samp");
            if (hit && !pkg.equals(getPackageName())) matching.add(item);
        }
        Collections.sort(matching, Comparator.comparing(a ->
                String.valueOf(a.loadLabel(getPackageManager())).toLowerCase(Locale.US)));
        List<String> labels = new ArrayList<>();
        for (ResolveInfo item : matching) {
            labels.add(item.loadLabel(getPackageManager()) + "\n" + item.activityInfo.packageName);
        }
        labels.add("Enter package name manually…");
        new AlertDialog.Builder(this)
                .setTitle(game ? "SELECT GTA: SAN ANDREAS" : "SELECT SA-MP CLIENT")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == matching.size()) {
                        showManualPackageDialog(game);
                        return;
                    }
                    String pkg = matching.get(which).activityInfo.packageName;
                    prefs.edit().putString(game ? "game_package" : "client_package", pkg).apply();
                    toast("Saved " + pkg);
                    renderSettings();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showManualPackageDialog(boolean game) {
        EditText input = edit("Android package name", "", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("ENTER PACKAGE NAME")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
                        toast("Invalid Android package name.");
                        return;
                    }
                    prefs.edit().putString(game ? "game_package" : "client_package", value).apply();
                    toast(isInstalled(value) ? "Package saved." : "Package saved, but not installed.");
                    renderSettings();
                }).show();
    }

    private String packageSummary(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "NOT SELECTED";
        try {
            PackageInfo info = getPackageInfo(packageName);
            return getPackageManager().getApplicationLabel(info.applicationInfo) + " • v" + info.versionName;
        } catch (Exception e) {
            return "NOT INSTALLED • " + packageName;
        }
    }

    private PackageInfo getPackageInfo(String packageName) throws Exception {
        if (Build.VERSION.SDK_INT >= 33) {
            return getPackageManager().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return getPackageManager().getPackageInfo(packageName, 0);
    }

    private boolean isInstalled(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        try { getPackageInfo(packageName); return true; }
        catch (Exception e) { return false; }
    }

    private void chooseCefsFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CEF_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_CEF_FOLDER || requestCode == REQUEST_MOD_FOLDER || requestCode == REQUEST_GAME_ZIP) &&
                resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception e) {
                toast("Could not keep folder permission. Select it again.");
                return;
            }
            if (requestCode == REQUEST_CEF_FOLDER) {
                prefs.edit().putString("cef_tree_uri", uri.toString()).apply();
                toast("CEFS folder access saved.");
                renderCefs();
            } else if (requestCode == REQUEST_MOD_FOLDER) {
                prefs.edit().putString("mods_tree_uri", uri.toString()).apply();
                toast("Game mod folder access saved.");
                selectedSettingsTab = "MODS";
                renderSettings();
            } else {
                confirmInstallLocalGameZip(uri);
            }
        }
    }

    private boolean validHttps(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            URL u = new URL(raw.trim());
            return "https".equalsIgnoreCase(u.getProtocol());
        } catch (Exception e) { return false; }
    }

    private void chooseModsFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MOD_FOLDER);
    }

    private void confirmInstallMods() {
        String txd = prefs.getString("mod_txd_url", "").trim();
        String dff = prefs.getString("mod_dff_url", "").trim();
        Uri tree = parseUri(prefs.getString("mods_tree_uri", ""));
        if (!validHttps(txd) || !validHttps(dff)) {
            toast("Save both direct HTTPS TXD and DFF download links first.");
            return;
        }
        if (tree == null) {
            toast("Choose the mod folder supported by your GTA / SA-MP client first.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("INSTALL TXD + DFF MOD?")
                .setMessage("Both files will be downloaded over HTTPS. Existing files with the same names will be backed up where possible before replacement. Continue?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DOWNLOAD + INSTALL", (d, w) -> installMods(tree, txd, dff))
                .show();
    }

    private String modFileName(HttpURLConnection connection, URL url, String extension) throws Exception {
        String disposition = connection.getHeaderField("Content-Disposition");
        String name = null;
        if (disposition != null) {
            for (String part : disposition.split(";")) {
                String lower = part.trim().toLowerCase(Locale.US);
                if (lower.startsWith("filename*=") || lower.startsWith("filename=")) {
                    int equals = part.indexOf('=');
                    if (equals >= 0) {
                        name = part.substring(equals + 1).trim().replace("\"", "");
                        if (name.toLowerCase(Locale.US).startsWith("utf-8''"))
                            name = name.substring(7);
                        break;
                    }
                }
            }
        }
        if (name == null || name.isEmpty()) {
            String path = url.getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                name = slash >= 0 ? path.substring(slash + 1) : path;
            }
        }
        if (name == null) throw new IllegalArgumentException("Download did not provide a filename.");
        name = Uri.decode(name).replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.toLowerCase(Locale.US).endsWith(extension))
            throw new IllegalArgumentException("Expected a " + extension + " file but received: " + name);
        return name;
    }

    private File downloadModFile(String link, String extension, String label) throws Exception {
        URL url = new URL(link);
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("Only HTTPS mod downloads are allowed.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "TestLauncher-Android/4.1");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300)
                throw new IllegalStateException(label + " download returned HTTP " + code +
                        ". Use the direct file link from LibertyCity, not its mod page.");
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.US).contains("text/html"))
                throw new IllegalStateException(label + " link returned a web page, not a file. Copy the direct download link.");
            String fileName = modFileName(connection, connection.getURL(), extension);
            File target = new File(getCacheDir(), fileName);
            long total = connection.getContentLengthLong();
            if (total > 512L * 1024L * 1024L)
                throw new IllegalStateException(label + " file exceeds the 512 MB safety limit.");
            long count = 0;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    count += n;
                    if (count > 512L * 1024L * 1024L)
                        throw new IllegalStateException(label + " file exceeds the 512 MB safety limit.");
                    out.write(buffer, 0, n);
                }
            }
            if (count < 16) {
                target.delete();
                throw new IllegalStateException(label + " download is empty or invalid.");
            }
            return target;
        } finally {
            connection.disconnect();
        }
    }

    private void backupAndWriteMod(Uri tree, File source) throws Exception {
        DocumentFile rootFolder = DocumentFile.fromTreeUri(this, tree);
        if (rootFolder == null || !rootFolder.canWrite())
            throw new IllegalStateException("Selected mod folder is not writable.");
        String fileName = source.getName();
        DocumentFile existing = rootFolder.findFile(fileName);
        if (existing != null && existing.isFile()) {
            DocumentFile backups = rootFolder.findFile("TestLauncherBackups");
            if (backups == null) backups = rootFolder.createDirectory("TestLauncherBackups");
            if (backups != null) {
                String backupName = fileName + "." + System.currentTimeMillis() + ".bak";
                DocumentFile backup = backups.createFile("application/octet-stream", backupName);
                if (backup != null) {
                    try (InputStream in = getContentResolver().openInputStream(existing.getUri());
                         OutputStream out = getContentResolver().openOutputStream(backup.getUri(), "w")) {
                        if (in != null && out != null) {
                            byte[] buffer = new byte[16384];
                            int n;
                            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        }
                    }
                }
            }
            existing.delete();
        }
        DocumentFile created = rootFolder.createFile("application/octet-stream", fileName);
        if (created == null) throw new IllegalStateException("Could not create " + fileName + " in the selected folder.");
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = getContentResolver().openOutputStream(created.getUri(), "w")) {
            if (out == null) throw new IllegalStateException("Could not open destination for " + fileName);
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private void installMods(Uri tree, String txdUrl, String dffUrl) {
        beginTask("Downloading TXD texture file…");
        io.execute(() -> {
            File txd = null;
            File dff = null;
            try {
                txd = downloadModFile(txdUrl, ".txd", "TXD");
                main.post(() -> updateTask(1, 2, "TXD downloaded; downloading DFF…"));
                dff = downloadModFile(dffUrl, ".dff", "DFF");
                File finalTxd = txd;
                File finalDff = dff;
                main.post(() -> updateTask(2, 2, "Both files downloaded; installing…"));
                backupAndWriteMod(tree, finalTxd);
                backupAndWriteMod(tree, finalDff);
                main.post(() -> finishTask(true, "Installed " + finalTxd.getName() + " and " + finalDff.getName() +
                        ". Check that your selected client supports this mod folder."));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown download/install error." : e.getMessage();
                main.post(() -> finishTask(false, "Mod install failed: " + message));
            } finally {
                if (txd != null) txd.delete();
                if (dff != null) dff.delete();
            }
        });
    }

    private void downloadCefs() {
        String url = prefs.getString("cef_url", "").trim();
        String sha = prefs.getString("cef_sha256", "").trim();
        if (url.isEmpty() || sha.isEmpty()) {
            toast("Configure an HTTPS URL and SHA-256 first.");
            return;
        }
        beginTask("Starting secure download…");
        io.execute(() -> {
            try {
                CefsManager.downloadVerified(this, url, sha,
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                main.post(() -> finishTask(true, "Package downloaded and SHA-256 verified."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, e.getMessage()));
            }
        });
    }

    private void confirmInstallCefs() {
        Uri tree = parseUri(prefs.getString("cef_tree_uri", ""));
        File archive = new File(getFilesDir(), "vortex-cefs-package.zip");
        if (tree == null) { toast("Choose an installation folder first."); return; }
        if (!archive.isFile()) { toast("Download and verify a package first."); return; }
        new AlertDialog.Builder(this)
                .setTitle("BACK UP AND INSTALL CEFS?")
                .setMessage("Existing files included in the manifest will be backed up before replacement. Files added by the package can be removed by Restore. Continue?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("INSTALL", (d, w) -> installCefs(tree, archive))
                .show();
    }

    private void installCefs(Uri tree, File archive) {
        beginTask("Validating package manifest…");
        io.execute(() -> {
            try {
                int count = CefsManager.install(this, tree, archive,
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                main.post(() -> finishTask(true, count + " CEFS files installed. Original files backed up."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, "Install stopped: " + e.getMessage()));
            }
        });
    }

    private void confirmRestoreCefs() {
        if (!CefsManager.hasBackup(this)) { toast("No backup found."); return; }
        Uri tree = parseUri(prefs.getString("cef_tree_uri", ""));
        if (tree == null) { toast("Select the same folder used during installation."); return; }
        new AlertDialog.Builder(this)
                .setTitle("RESTORE ORIGINAL FILES?")
                .setMessage("VORTEX will remove package-added files and restore the backed-up originals.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESTORE", (d, w) -> restoreCefs(tree))
                .show();
    }

    private void restoreCefs(Uri tree) {
        beginTask("Preparing restore…");
        io.execute(() -> {
            try {
                int count = CefsManager.restore(this, tree,
                        (current, total, text) -> main.post(() -> updateTask(current, total, text)));
                main.post(() -> finishTask(true, count + " file changes restored."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, "Restore stopped: " + e.getMessage()));
            }
        });
    }

    private void beginTask(String message) {
        taskBusy = true;
        taskMessage = message;
        taskPercent = 0;
        if (!"DOWNLOADS".equals(currentTab)) showTab("DOWNLOADS");
        if (taskStatus != null) taskStatus.setText(taskMessage);
        if (taskProgress != null) {
            taskProgress.setIndeterminate(true);
            taskProgress.setProgress(0);
        }
    }

    private void updateTask(long current, long total, String text) {
        taskMessage = text + " • " + current + (total > 0 ? " / " + total : "");
        if (total > 0) taskPercent = (int) Math.min(100, current * 100L / total);
        if (taskStatus != null) taskStatus.setText(taskMessage);
        if (taskProgress != null) {
            taskProgress.setIndeterminate(total <= 0);
            if (total > 0) taskProgress.setProgress(taskPercent);
        }
    }

    private void finishTask(boolean success, String message) {
        taskBusy = false;
        taskMessage = message == null ? (success ? "Complete." : "Failed.") : message;
        taskPercent = success ? 100 : 0;
        toast(taskMessage);
        if ("DOWNLOADS".equals(currentTab)) {
            if ("CEF FILES".equals(downloadsSubTab)) renderCefs();
            else renderGameData();
        }
    }

    private Uri parseUri(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return Uri.parse(raw); } catch (Exception e) { return null; }
    }

    private void loadNews(boolean showToast) {
        String urlText = prefs.getString("news_url", "").trim();
        if (urlText.isEmpty()) {
            if (showToast) toast("Add your HTTPS news JSON URL in Settings first.");
            return;
        }
        if (newsLoading) return;
        newsLoading = true;
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlText);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("News feed must use HTTPS.");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "VORTEX-RP-Launcher/3.1");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new IllegalStateException("News feed returned HTTP " + connection.getResponseCode());
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > 512 * 1024) throw new IllegalStateException("News feed is too large.");
                        out.write(buffer, 0, n);
                    }
                }
                String raw = new String(out.toByteArray(), StandardCharsets.UTF_8);
                Object json = raw.trim().startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
                JSONArray items = json instanceof JSONArray ? (JSONArray) json : ((JSONObject) json).optJSONArray("items");
                JSONArray finalItems = items == null ? new JSONArray() : items;
                main.post(() -> {
                    newsLoading = false;
                    newsItems = finalItems;
                    if ("HOME".equals(currentTab)) renderHome();
                    if (showToast) toast("VORTEX news refreshed.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    newsLoading = false;
                    if (showToast) toast("Could not load news: " + e.getMessage());
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void copyText(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("VORTEX RP", value));
        toast(message);
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("Could not open link."); }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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