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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final int BG = Color.rgb(9, 7, 15);
    private static final int PANEL = Color.rgb(24, 18, 32);
    private static final int PANEL_2 = Color.rgb(36, 27, 48);
    private static final int RED = Color.rgb(255, 55, 82);
    private static final int PURPLE = Color.rgb(177, 70, 255);
    private static final int MUTED = Color.rgb(166, 157, 181);
    private static final int GREEN = Color.rgb(86, 224, 155);
    private static final int AMBER = Color.rgb(255, 190, 88);
    private static final int REQUEST_GAME_FOLDER = 1101;
    private static final int REQUEST_CEF_FOLDER = 1102;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout page;
    private LinearLayout bottomBar;
    private TextView taskStatus;
    private ProgressBar taskProgress;
    private String currentTab = "HOME";
    private ServerQuery.Info lastServerInfo;
    private String lastServerError = "Not checked yet";
    private JSONArray newsItems = new JSONArray();
    private String taskMessage = "Ready.";
    private int taskPercent = 0;
    private boolean taskBusy = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vortex_launcher_v3", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

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
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(7), dp(6), dp(7));
        bottomBar.setBackgroundColor(Color.rgb(16, 12, 23));
        root.addView(bottomBar);
        updateNavigation();
        renderHome();
        refreshServerStatus(false);
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

    private void buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(13), dp(18), dp(13));

        TextView mark = label("V", 24, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(RED, 0, 13));
        bar.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        mark.animate().alpha(0.68f).setDuration(1100).setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> mark.animate().alpha(1f).setDuration(1100)
                        .withEndAction(() -> mark.animate().alpha(0.68f).setDuration(1100).start()).start()).start();

        LinearLayout brand = column();
        brand.setPadding(dp(11), 0, 0, 0);
        brand.addView(label("VORTEX RP", 18, Color.WHITE, true));
        brand.addView(label("GEORGIAN ROLEPLAY • MOBILE", 9, MUTED, true));
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = label("ANDROID 15", 9, PURPLE, true);
        version.setPadding(dp(9), dp(6), dp(9), dp(6));
        version.setBackground(shape(PANEL_2, PURPLE, 9));
        bar.addView(version);
        root.addView(bar);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(63, 39, 75));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void updateNavigation() {
        bottomBar.removeAllViews();
        String[] tabs = {"HOME", "SERVER", "CEF", "SETTINGS", "TESTS"};
        for (String tab : tabs) {
            boolean active = currentTab.equals(tab);
            Button button = new Button(this);
            button.setText(tab);
            button.setAllCaps(false);
            button.setTextSize(9);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(active ? Color.WHITE : MUTED);
            button.setPadding(dp(2), 0, dp(2), 0);
            button.setMinWidth(0);
            button.setBackground(shape(active ? PANEL_2 : Color.TRANSPARENT, active ? PURPLE : 0, 9));
            button.setOnClickListener(v -> showTab(tab));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(43), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bottomBar.addView(button, lp);
        }
    }

    private void showTab(String tab) {
        currentTab = tab;
        updateNavigation();
        if ("HOME".equals(tab)) renderHome();
        else if ("SERVER".equals(tab)) renderServer();
        else if ("CEF".equals(tab)) renderCef();
        else if ("SETTINGS".equals(tab)) renderSettings();
        else renderDiagnostics();
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
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

    private void heading(String title, String subtitle) {
        page.addView(label(title, 23, Color.WHITE, true));
        TextView sub = label(subtitle, 10, MUTED, true);
        sub.setPadding(0, dp(5), 0, dp(15));
        page.addView(sub);
    }

    private LinearLayout card(String title, String description) {
        LinearLayout box = column();
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(shape(PANEL, Color.rgb(65, 43, 79), 15));
        box.addView(label(title, 13, Color.WHITE, true));
        if (description != null && !description.isEmpty()) {
            TextView body = label(description, 11, MUTED, false);
            body.setPadding(0, dp(7), 0, 0);
            box.addView(body);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        page.addView(box, lp);
        return box;
    }

    private Button actionButton(String title, boolean secondary, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setBackground(shape(secondary ? PANEL_2 : PURPLE, secondary ? Color.rgb(89, 59, 113) : 0, 11));
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
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(shape(PANEL_2, Color.rgb(85, 61, 103), 10));
        return e;
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private String serverHost() { return prefs.getString("server_host", "").trim(); }
    private int serverPort() { return prefs.getInt("server_port", 7777); }
    private String serverAddress() { return serverHost().isEmpty() ? "NOT CONFIGURED" : serverHost() + ":" + serverPort(); }

    private void renderHome() {
        page.removeAllViews();
        heading("WELCOME TO VORTEX", "YOUR SAN ANDREAS ROLEPLAY STARTS HERE");

        LinearLayout hero = card("VORTEX ROLEPLAY", "A native Android launcher for an installed GTA: San Andreas game and a compatible SA-MP mobile client.");
        TextView address = label(serverAddress(), 12, PURPLE, true);
        address.setPadding(0, dp(12), 0, dp(2));
        hero.addView(address);

        String status;
        int statusColor;
        if (lastServerInfo != null) {
            status = "●  ONLINE  •  " + lastServerInfo.players + "/" + lastServerInfo.maxPlayers + " PLAYERS";
            statusColor = GREEN;
        } else if ("Not checked yet".equals(lastServerError)) {
            status = "●  STATUS NOT CHECKED";
            statusColor = AMBER;
        } else if (serverHost().isEmpty()) {
            status = "●  SERVER NOT CONFIGURED";
            statusColor = AMBER;
        } else {
            status = "●  UNVERIFIED  •  " + lastServerError;
            statusColor = AMBER;
        }
        TextView line = label(status, 10, statusColor, true);
        line.setPadding(0, dp(7), 0, 0);
        hero.addView(line);
        if (lastServerInfo != null) {
            TextView name = label(lastServerInfo.hostname + "  •  " + lastServerInfo.gameMode + "  •  " + lastServerInfo.pingMs + " ms", 10, MUTED, false);
            name.setPadding(0, dp(4), 0, 0);
            hero.addView(name);
        }

        String game = packageSummary(prefs.getString("game_package", ""));
        String client = packageSummary(prefs.getString("client_package", ""));
        card("INSTALLATION CHECK", "GTA GAME  •  " + game + "\nMULTIPLAYER CLIENT  •  " + client
                + "\nDEVICE ABI  •  " + Build.SUPPORTED_ABIS[0]);

        actionButton("▶  PLAY VORTEX RP", false, this::play);
        actionButton("REFRESH SERVER STATUS", true, () -> refreshServerStatus(true));
        actionButton("CONFIGURE SERVER", true, () -> showTab("SERVER"));

        LinearLayout news = card("NEWS & ANNOUNCEMENTS", "");
        String newsUrl = prefs.getString("news_url", "").trim();
        if (newsUrl.isEmpty()) {
            news.addView(label("No official news feed is configured. Add your HTTPS JSON feed in Settings to show live VORTEX announcements here.", 11, MUTED, false));
        } else if (newsItems.length() == 0) {
            news.addView(label("No announcements have been loaded yet. Tap refresh to check the configured feed.", 11, MUTED, false));
        } else {
            for (int i = 0; i < Math.min(newsItems.length(), 5); i++) {
                JSONObject item = newsItems.optJSONObject(i);
                if (item == null) continue;
                TextView title = label(item.optString("title", "VORTEX update"), 12, Color.WHITE, true);
                title.setPadding(0, dp(9), 0, 0);
                news.addView(title);
                TextView body = label(item.optString("body", item.optString("description", "")), 10, MUTED, false);
                news.addView(body);
            }
        }
        actionButton("REFRESH NEWS", true, () -> loadNews(true));

        card("CEF SUPPORT", "CEFS package installation is available below. In-game HTML rendering still requires a compatible CEF-enabled multiplayer client; a launcher cannot inject CEF into another app.");
        actionButton("OPEN CEFS MANAGER", true, () -> showTab("CEF"));
        actionButton("CONNECTION DIAGNOSTICS", true, () -> showTab("TESTS"));
        actionButton("LAUNCHER RELEASES", true, () -> openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/releases"));
    }

    private void renderServer() {
        page.removeAllViews();
        heading("SERVER CONFIGURATION", "CHANGE THE LIVE VORTEX ENDPOINT WITHOUT REBUILDING");

        LinearLayout config = card("VORTEX RP SERVER", "The launcher queries the configured host using the SA-MP/open.mp UDP information protocol.");
        EditText host = edit("Server IP or hostname", serverHost(), InputType.TYPE_CLASS_TEXT);
        EditText port = edit("Port", String.valueOf(serverPort()), InputType.TYPE_CLASS_NUMBER);
        config.addView(host);
        gap(config, 8);
        config.addView(port);

        Button save = new Button(this);
        save.setText("SAVE SERVER ADDRESS");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackground(shape(PURPLE, 0, 10));
        save.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            int p;
            try { p = Integer.parseInt(port.getText().toString().trim()); }
            catch (Exception ex) { p = -1; }
            if (h.isEmpty() || p < 1 || p > 65535 || h.contains(" ")) {
                toast("Enter a valid hostname/IP and port (1–65535).");
                return;
            }
            prefs.edit().putString("server_host", h).putInt("server_port", p).apply();
            lastServerInfo = null;
            lastServerError = "Not checked yet";
            toast("VORTEX server address saved.");
            renderServer();
            refreshServerStatus(false);
        });
        config.addView(save, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout info = card("LIVE SERVER STATUS", "");
        if (lastServerInfo == null) {
            info.addView(label(lastServerError, 11, AMBER, false));
        } else {
            info.addView(label(lastServerInfo.hostname, 15, Color.WHITE, true));
            info.addView(label(lastServerInfo.players + " / " + lastServerInfo.maxPlayers + " players online", 12, GREEN, true));
            info.addView(label("Mode: " + lastServerInfo.gameMode + "\nLanguage: " + lastServerInfo.language
                    + "\nPassword protected: " + (lastServerInfo.passworded ? "Yes" : "No")
                    + "\nQuery response: " + lastServerInfo.pingMs + " ms", 10, MUTED, false));
        }
        actionButton("QUERY SERVER NOW", false, () -> refreshServerStatus(true));
        card("IMPORTANT", "A query timeout is shown as UNVERIFIED. Some SA-MP/open.mp servers disable information queries, so a timeout alone does not prove the game server is offline.");
    }

    private void renderCef() {
        page.removeAllViews();
        heading("CEFS MANAGER", "VERIFIED RESOURCE PACKAGES • BACKUP • RESTORE");

        LinearLayout source = card("TRUSTED PACKAGE SOURCE", "Configure an HTTPS ZIP URL and the SHA-256 checksum supplied by the VORTEX package publisher.");
        EditText url = edit("https://example.com/vortex-cefs.zip", prefs.getString("cef_url", ""),
                InputType.TYPE_TEXT_VARIATION_URI);
        EditText hash = edit("Expected SHA-256 (64 hex characters)", prefs.getString("cef_sha256", ""),
                InputType.TYPE_CLASS_TEXT);
        source.addView(url);
        gap(source, 8);
        source.addView(hash);
        Button save = new Button(this);
        save.setText("SAVE PACKAGE SOURCE");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackground(shape(PURPLE, 0, 10));
        save.setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            String h = hash.getText().toString().trim().toLowerCase(Locale.US);
            if (!u.isEmpty() && !u.toLowerCase(Locale.US).startsWith("https://")) {
                toast("Only HTTPS package sources are accepted.");
                return;
            }
            if (!h.isEmpty() && !h.matches("[a-f0-9]{64}")) {
                toast("SHA-256 must contain exactly 64 hexadecimal characters.");
                return;
            }
            prefs.edit().putString("cef_url", u).putString("cef_sha256", h).apply();
            toast("CEFS source saved.");
        });
        source.addView(save, new LinearLayout.LayoutParams(-1, dp(43)));

        LinearLayout target = card("INSTALL DESTINATION", "Pick a folder that belongs to your installed multiplayer client or its supported game-data location. Android does not allow this launcher to write into arbitrary private app folders.");
        String cefTree = prefs.getString("cef_tree_uri", "");
        target.addView(label(cefTree.isEmpty() ? "No destination folder selected." : "Folder access saved for this device.", 10, cefTree.isEmpty() ? AMBER : GREEN, true));
        actionButton("CHOOSE CEFS DESTINATION FOLDER", true, () -> chooseFolder(REQUEST_CEF_FOLDER));

        LinearLayout state = card("PACKAGE STATE", "");
        File packageFile = new File(getFilesDir(), "vortex-cefs-package.zip");
        state.addView(label(packageFile.isFile() ? "Verified package is downloaded." : "No verified package downloaded.", 11,
                packageFile.isFile() ? GREEN : AMBER, true));
        state.addView(label(CefsManager.hasBackup(this)
                ? "A rollback backup is available. Restore it before installing another package."
                : "No active install backup.", 10, MUTED, false));
        state.addView(label("A compatible CEF runtime must already exist in the game client. Copying web files does not add native CEF support.", 10, MUTED, false));

        actionButton("DOWNLOAD + VERIFY CEFS ZIP", false, this::downloadCefs);
        actionButton("BACK UP + INSTALL PACKAGE", false, this::confirmInstallCefs);
        actionButton("RESTORE ORIGINAL FILES", true, this::confirmRestoreCefs);

        LinearLayout progress = card("TASK PROGRESS", "");
        taskStatus = label(taskMessage, 10, MUTED, false);
        progress.addView(taskStatus);
        taskProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        taskProgress.setMax(100);
        taskProgress.setProgress(0);
        progress.addView(taskProgress, new LinearLayout.LayoutParams(-1, dp(7)));

        card("PACKAGE FORMAT", "The ZIP must contain vortex-cef-manifest.json with schemaVersion 1 and a files array. Each listed file must include a relative path and its own SHA-256. Unsafe paths, missing files, duplicate paths and hash mismatches are rejected.");
        actionButton("OPEN CEFS SOURCE DOCUMENTATION", true, () -> openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/tree/main/cef"));
    }

    private void renderSettings() {
        page.removeAllViews();
        heading("LAUNCHER SETTINGS", "DEVICE • PLAYER • CLIENT INTEGRATION");

        LinearLayout profile = card("PLAYER NAME", "Saved locally on this device.");
        EditText nickname = edit("In-game nickname", prefs.getString("nickname", ""), InputType.TYPE_CLASS_TEXT);
        profile.addView(nickname);
        Button saveNickname = new Button(this);
        saveNickname.setText("SAVE PLAYER NAME");
        saveNickname.setAllCaps(false);
        saveNickname.setTextColor(Color.WHITE);
        saveNickname.setBackground(shape(PURPLE, 0, 10));
        saveNickname.setOnClickListener(v -> {
            String value = nickname.getText().toString().trim();
            if (value.length() < 1 || value.length() > 24 || value.contains(" ")) {
                toast("Use a 1–24 character nickname without spaces.");
                return;
            }
            prefs.edit().putString("nickname", value).apply();
            toast("Player name saved.");
        });
        profile.addView(saveNickname, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout game = card("INSTALLED GTA: SAN ANDREAS", "Select the legally installed game package. The launcher reports its installed version but cannot inspect another app's private game files.");
        String gamePkg = prefs.getString("game_package", "");
        game.addView(label(packageSummary(gamePkg), 11, PURPLE, true));
        actionButton("SELECT GTA SAN ANDREAS APP", true, () -> chooseInstalledApp(true));
        actionButton("SELECT GAME DATA FOLDER", true, () -> chooseFolder(REQUEST_GAME_FOLDER));

        LinearLayout client = card("MULTIPLAYER CLIENT", "Select the actual SA-MP Android client installed on this phone. The launcher never bundles a game executable.");
        String clientPkg = prefs.getString("client_package", "");
        client.addView(label(packageSummary(clientPkg), 11, PURPLE, true));
        actionButton("SELECT SA-MP MOBILE CLIENT", true, () -> chooseInstalledApp(false));

        LinearLayout bridge = card("OPTIONAL DIRECT-CONNECT BRIDGE", "There is no universal Android SA-MP intent contract. Leave this blank unless your chosen client's own documentation gives you a URI template. Supported placeholders: {host}, {port}, {nickname}.");
        EditText template = edit("Example only: samp://{host}:{port}", prefs.getString("connect_uri_template", ""),
                InputType.TYPE_TEXT_VARIATION_URI);
        bridge.addView(template);
        Button saveBridge = new Button(this);
        saveBridge.setText("SAVE CLIENT URI TEMPLATE");
        saveBridge.setAllCaps(false);
        saveBridge.setTextColor(Color.WHITE);
        saveBridge.setBackground(shape(PANEL_2, PURPLE, 10));
        saveBridge.setOnClickListener(v -> {
            String value = template.getText().toString().trim();
            if (value.toLowerCase(Locale.US).startsWith("javascript:") || value.toLowerCase(Locale.US).startsWith("file:")) {
                toast("That URI scheme is not allowed.");
                return;
            }
            prefs.edit().putString("connect_uri_template", value).apply();
            toast(value.isEmpty() ? "Direct-connect bridge disabled." : "Client URI template saved. Test it with your client before use.");
        });
        bridge.addView(saveBridge, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout feed = card("NEWS FEED", "Optional HTTPS JSON endpoint. Expected format: an array of {title, body} objects, or an object containing an items array.");
        EditText newsUrl = edit("https://your-site.example/news.json", prefs.getString("news_url", ""),
                InputType.TYPE_TEXT_VARIATION_URI);
        feed.addView(newsUrl);
        Button saveNews = new Button(this);
        saveNews.setText("SAVE NEWS FEED");
        saveNews.setAllCaps(false);
        saveNews.setTextColor(Color.WHITE);
        saveNews.setBackground(shape(PANEL_2, PURPLE, 10));
        saveNews.setOnClickListener(v -> {
            String value = newsUrl.getText().toString().trim();
            if (!value.isEmpty() && !value.toLowerCase(Locale.US).startsWith("https://")) {
                toast("The news feed must use HTTPS.");
                return;
            }
            prefs.edit().putString("news_url", value).apply();
            newsItems = new JSONArray();
            toast("News feed saved.");
        });
        feed.addView(saveNews, new LinearLayout.LayoutParams(-1, dp(42)));

        card("ABOUT", "VORTEX RP Launcher 3.0 • Android 8.0+\nNative Java Android app • Android 15 target\nNo GTA files or third-party multiplayer binaries are included.");
        actionButton("VIEW SOURCE REPOSITORY", true, () -> openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app"));
        actionButton("CHECK LAUNCHER RELEASES", true, () -> openUrl("https://github.com/shaandri2-rgb/Vortex-RP-mobile-app/releases"));
    }

    private void renderDiagnostics() {
        page.removeAllViews();
        heading("CONNECTION DIAGNOSTICS", "REAL DEVICE, PACKAGE AND SERVER CHECKS");

        card("DEVICE", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nABI: " + join(Build.SUPPORTED_ABIS)
                + "\nPackage: " + getPackageName());

        card("GTA SAN ANDREAS", packageSummary(prefs.getString("game_package", "")));
        card("MULTIPLAYER CLIENT", packageSummary(prefs.getString("client_package", "")));
        card("SERVER QUERY", lastServerInfo == null
                ? "UNVERIFIED: " + lastServerError + "\nEndpoint: " + serverAddress()
                : "ONLINE: " + lastServerInfo.hostname + "\nPlayers: " + lastServerInfo.players + "/" + lastServerInfo.maxPlayers
                    + "\nPing: " + lastServerInfo.pingMs + " ms");

        Uri target = parseUri(prefs.getString("cef_tree_uri", ""));
        DocumentFile folder = target == null ? null : DocumentFile.fromTreeUri(this, target);
        card("CEFS DESTINATION", folder == null ? "NOT SELECTED" : (folder.canWrite() ? "WRITABLE FOLDER PERMISSION GRANTED" : "FOLDER IS NOT WRITABLE"));
        Uri gameTree = parseUri(prefs.getString("game_tree_uri", ""));
        DocumentFile gameFolder = gameTree == null ? null : DocumentFile.fromTreeUri(this, gameTree);
        card("GAME DATA FOLDER", gameFolder == null ? "NOT SELECTED — game resources cannot be inspected." : (gameFolder.canRead() ? "READ PERMISSION GRANTED — client-specific resource compatibility is still unverified." : "FOLDER IS NOT READABLE"));
        File downloaded = new File(getFilesDir(), "vortex-cefs-package.zip");
        card("CEFS PACKAGE", downloaded.isFile() ? "A hash-verified archive is stored privately in the launcher." : "No verified archive is stored.");
        card("DIRECT CONNECT", prefs.getString("connect_uri_template", "").isEmpty()
                ? "NOT CONFIGURED. PLAY will open the selected client and copy the server address for manual connection."
                : "CUSTOM URI TEMPLATE CONFIGURED. The selected client must document and support that exact URI.");
        card("RESOURCE COMPATIBILITY", "The selected game app version can be read, but Android prevents this launcher from inspecting another app's private files. Client-specific game-data and CEF compatibility cannot be proven here.");

        actionButton("RUN SERVER QUERY", false, () -> refreshServerStatus(true));
        actionButton("RECHECK INSTALLED CLIENTS", true, () -> {
            String game = prefs.getString("game_package", "");
            String client = prefs.getString("client_package", "");
            toast("GTA package: " + (isInstalled(game) ? "found" : "missing")
                    + " • Client: " + (isInstalled(client) ? "found" : "missing"));
            renderDiagnostics();
        });
    }

    private void refreshServerStatus(boolean showToast) {
        final String host = serverHost();
        final int port = serverPort();
        if (host.isEmpty()) {
            lastServerInfo = null;
            lastServerError = "Server address has not been configured.";
            if ("HOME".equals(currentTab)) renderHome();
            if ("SERVER".equals(currentTab)) renderServer();
            if (showToast) toast(lastServerError);
            return;
        }
        lastServerError = "Query in progress…";
        if ("HOME".equals(currentTab)) renderHome();
        if ("SERVER".equals(currentTab)) renderServer();

        io.execute(() -> {
            try {
                ServerQuery.Info info = ServerQuery.query(host, port, 3500);
                main.post(() -> {
                    lastServerInfo = info;
                    lastServerError = "";
                    if ("HOME".equals(currentTab)) renderHome();
                    if ("SERVER".equals(currentTab)) renderServer();
                    if ("TESTS".equals(currentTab)) renderDiagnostics();
                });
            } catch (Exception ex) {
                main.post(() -> {
                    lastServerInfo = null;
                    lastServerError = ex.getMessage() == null ? "No valid query response." : ex.getMessage();
                    if ("HOME".equals(currentTab)) renderHome();
                    if ("SERVER".equals(currentTab)) renderServer();
                    if ("TESTS".equals(currentTab)) renderDiagnostics();
                    if (showToast) toast("Server not verified: " + lastServerError);
                });
            }
        });
    }

    private void loadNews(boolean showToast) {
        String urlText = prefs.getString("news_url", "").trim();
        if (urlText.isEmpty()) {
            if (showToast) toast("Add an HTTPS news feed in Settings first.");
            return;
        }
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlText);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("News feed must use HTTPS.");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "VORTEX-RP-Launcher/3.0");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new IllegalStateException("News feed returned HTTP " + connection.getResponseCode());
                }
                byte[] bytes;
                try (InputStream in = connection.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > 512 * 1024) throw new IllegalStateException("News response is too large.");
                        out.write(buffer, 0, n);
                    }
                    bytes = out.toByteArray();
                }
                String body = new String(bytes, StandardCharsets.UTF_8);
                Object json = body.trim().startsWith("[") ? new JSONArray(body) : new JSONObject(body);
                JSONArray items = json instanceof JSONArray ? (JSONArray) json : ((JSONObject) json).optJSONArray("items");
                if (items == null) items = new JSONArray();
                JSONArray finalItems = items;
                main.post(() -> {
                    newsItems = finalItems;
                    if ("HOME".equals(currentTab)) renderHome();
                    if (showToast) toast("News feed refreshed.");
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (showToast) toast("News could not be loaded: " + ex.getMessage());
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
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
                    ? (name.contains("gta") || name.contains("san andreas") || id.contains("gtasa"))
                    : (name.contains("samp") || name.contains("sa-mp") || name.contains("san andreas") || name.contains("multiplayer") || id.contains("samp"));
            if (hit && !pkg.equals(getPackageName())) matching.add(item);
        }
        Collections.sort(matching, Comparator.comparing(a -> String.valueOf(a.loadLabel(getPackageManager())).toLowerCase(Locale.US)));

        List<String> labels = new ArrayList<>();
        for (ResolveInfo item : matching) {
            labels.add(item.loadLabel(getPackageManager()) + "\n" + item.activityInfo.packageName);
        }
        labels.add("Enter package name manually…");
        String[] options = labels.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(game ? "SELECT INSTALLED GTA: SAN ANDREAS" : "SELECT INSTALLED MULTIPLAYER CLIENT")
                .setItems(options, (dialog, which) -> {
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
        EditText input = edit("Android package name, e.g. com.example.client", "", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("ENTER PACKAGE NAME")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
                        toast("That package name is not valid.");
                        return;
                    }
                    prefs.edit().putString(game ? "game_package" : "client_package", value).apply();
                    toast(isInstalled(value) ? "Installed package saved." : "Package saved, but it is not installed.");
                    renderSettings();
                })
                .show();
    }

    private String packageSummary(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "NOT SELECTED";
        try {
            PackageInfo pi = getPackageInfo(packageName);
            String name = String.valueOf(getPackageManager().getApplicationLabel(pi.applicationInfo));
            return name + " • " + packageName + " • v" + pi.versionName;
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
        try {
            getPackageInfo(packageName);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void play() {
        String game = prefs.getString("game_package", "");
        String client = prefs.getString("client_package", "");
        if (game.isEmpty()) {
            showTab("SETTINGS");
            toast("Select your installed GTA: San Andreas package first.");
            return;
        }
        if (!isInstalled(game)) {
            toast("The selected GTA package is missing. Re-select the installed game in Settings.");
            showTab("SETTINGS");
            return;
        }
        if (client.isEmpty()) {
            showTab("SETTINGS");
            toast("Select an installed SA-MP mobile client first.");
            return;
        }
        if (!isInstalled(client)) {
            toast("The selected multiplayer client is missing. Re-select it in Settings.");
            showTab("SETTINGS");
            return;
        }

        String host = serverHost();
        if (host.isEmpty()) {
            showTab("SERVER");
            toast("Set the VORTEX server address before playing.");
            return;
        }

        String template = prefs.getString("connect_uri_template", "").trim();
        String nick = prefs.getString("nickname", "");
        try {
            if (!template.isEmpty()) {
                String expanded = template.replace("{host}", host)
                        .replace("{port}", String.valueOf(serverPort()))
                        .replace("{nickname}", Uri.encode(nick));
                Uri uri = Uri.parse(expanded);
                Intent direct = new Intent(Intent.ACTION_VIEW, uri);
                direct.setPackage(client);
                if (direct.resolveActivity(getPackageManager()) == null) {
                    toast("The selected client does not handle the configured connect URI. Check its documentation.");
                    return;
                }
                startActivity(direct);
                return;
            }

            copyText(host + ":" + serverPort(), "Server address copied");
            Intent launch = getPackageManager().getLaunchIntentForPackage(client);
            if (launch == null) {
                toast("The selected client has no launch activity.");
                return;
            }
            startActivity(launch);
            new AlertDialog.Builder(this)
                    .setTitle("CLIENT OPENED")
                    .setMessage("VORTEX copied " + host + ":" + serverPort()
                            + " to the clipboard. This client has no configured direct-connect bridge, so choose the server in its own server browser and paste the address.\n\nThe launcher opened the real installed app; it did not simulate game launch or claim that you are connected.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception ex) {
            toast("Could not open the selected client: " + ex.getMessage());
        }
    }

    private void chooseFolder(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            toast("Android folder picker is unavailable.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_GAME_FOLDER || requestCode == REQUEST_CEF_FOLDER)
                && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception e) {
                toast("Could not keep folder access. Select the folder again.");
                return;
            }
            String key = requestCode == REQUEST_GAME_FOLDER ? "game_tree_uri" : "cef_tree_uri";
            prefs.edit().putString(key, uri.toString()).apply();
            toast("Folder access saved.");
            if (requestCode == REQUEST_GAME_FOLDER) {
                showTab("TESTS");
            } else {
                showTab("CEF");
            }
        }
    }

    private void downloadCefs() {
        String url = prefs.getString("cef_url", "").trim();
        String sha = prefs.getString("cef_sha256", "").trim();
        if (url.isEmpty() || sha.isEmpty()) {
            toast("Set the trusted HTTPS URL and SHA-256 first.");
            return;
        }
        beginTask("Starting secure download…");
        io.execute(() -> {
            try {
                File file = CefsManager.downloadVerified(this, url, sha,
                        (current, total, label) -> main.post(() -> updateTask(current, total, label)));
                main.post(() -> finishTask(true, "Package downloaded and SHA-256 verified."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, e.getMessage()));
            }
        });
    }

    private void confirmInstallCefs() {
        Uri tree = parseUri(prefs.getString("cef_tree_uri", ""));
        File archive = new File(getFilesDir(), "vortex-cefs-package.zip");
        if (tree == null) {
            toast("Choose the destination folder first.");
            return;
        }
        if (!archive.isFile()) {
            toast("Download and verify the package first.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("BACK UP AND INSTALL CEFS?")
                .setMessage("Every existing file listed by the verified package will be backed up privately before replacement. Files absent before installation will be removed by Restore. No files are changed until you confirm here.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("BACK UP + INSTALL", (d, w) -> installCefs(tree, archive))
                .show();
    }

    private void installCefs(Uri tree, File archive) {
        beginTask("Checking manifest and file hashes…");
        io.execute(() -> {
            try {
                int count = CefsManager.install(this, tree, archive,
                        (current, total, label) -> main.post(() -> updateTask(current, total, label)));
                main.post(() -> finishTask(true, count + " CEFS files installed. Original files are backed up."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, "Install stopped safely: " + e.getMessage()));
            }
        });
    }

    private void confirmRestoreCefs() {
        if (!CefsManager.hasBackup(this)) {
            toast("No CEFS backup is available.");
            return;
        }
        Uri tree = parseUri(prefs.getString("cef_tree_uri", ""));
        if (tree == null) {
            toast("Choose the same destination folder used during installation.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("RESTORE ORIGINAL FILES?")
                .setMessage("VORTEX will remove files added by the package and restore the exact originals backed up before installation.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESTORE", (d, w) -> restoreCefs(tree))
                .show();
    }

    private void restoreCefs(Uri tree) {
        beginTask("Preparing restore…");
        io.execute(() -> {
            try {
                int count = CefsManager.restore(this, tree,
                        (current, total, label) -> main.post(() -> updateTask(current, total, label)));
                main.post(() -> finishTask(true, count + " file changes restored from backup."));
            } catch (Exception e) {
                main.post(() -> finishTask(false, "Restore stopped: " + e.getMessage()));
            }
        });
    }

    private void beginTask(String message) {
        taskBusy = true;
        taskMessage = message;
        taskPercent = 0;
        if (!"CEF".equals(currentTab)) showTab("CEF");
        if (taskStatus != null) taskStatus.setText(taskMessage);
        if (taskProgress != null) {
            taskProgress.setIndeterminate(true);
            taskProgress.setProgress(0);
        }
    }

    private void updateTask(long current, long total, String label) {
        taskMessage = label + " • " + current + (total > 0 ? " / " + total : "");
        if (total > 0 && total <= Integer.MAX_VALUE) taskPercent = (int) Math.min(100, (current * 100L) / total);
        if (taskStatus != null) taskStatus.setText(taskMessage);
        if (taskProgress != null) {
            if (total > 0 && total <= Integer.MAX_VALUE) {
                taskProgress.setIndeterminate(false);
                taskProgress.setProgress(taskPercent);
            } else {
                taskProgress.setIndeterminate(true);
            }
        }
    }

    private void finishTask(boolean success, String message) {
        taskBusy = false;
        taskMessage = message == null ? (success ? "Complete." : "Failed.") : message;
        taskPercent = success ? 100 : 0;
        toast(taskMessage);
        if ("CEF".equals(currentTab)) renderCef();
    }

    private Uri parseUri(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return Uri.parse(raw); } catch (Exception e) { return null; }
    }

    private String join(String[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(", ");
            out.append(values[i]);
        }
        return out.toString();
    }

    private void copyText(String value, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("VORTEX RP", value));
        toast(message);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Could not open link.");
        }
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
