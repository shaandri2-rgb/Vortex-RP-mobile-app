package com.vortexrp.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(9, 7, 17);
    private final int panel = Color.rgb(25, 19, 37);
    private final int purple = Color.rgb(174, 54, 255);
    private final int red = Color.rgb(255, 48, 89);
    private LinearLayout content;
    private EditText serverInput;
    private TextView selectedGame;
    private String selectedPackage;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vortex_launcher", MODE_PRIVATE);
        selectedPackage = prefs.getString("game_package", "");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        setContentView(root);

        TextView header = text("V O R T E X", 27, Color.WHITE, true);
        header.setGravity(Gravity.CENTER);
        header.setPadding(12, 26, 12, 4);
        root.addView(header);
        TextView sub = text("ROLEPLAY  •  MOBILE LAUNCHER", 11, purple, true);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 18);
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(18, 8, 18, 26);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showHome();
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(24);
        if (stroke != 0) d.setStroke(2, stroke);
        return d;
    }

    private void card(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 16, 18, 16);
        box.setBackground(shape(panel, Color.rgb(67, 39, 91)));
        TextView h = text(title, 16, Color.WHITE, true);
        TextView b = text(body, 13, Color.LTGRAY, false);
        b.setPadding(0, 7, 0, 0);
        box.addView(h); box.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, 13);
        content.addView(box, p);
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(purple, 0));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 6, 0, 8);
        content.addView(b, p);
        return b;
    }

    private void showHome() {
        content.removeAllViews();
        card("WELCOME BACK", "Your roleplay session starts here. Set your game app and server details, then launch your game.");
        selectedGame = text("", 14, Color.WHITE, true);
        selectedGame.setPadding(4, 9, 4, 9);
        content.addView(selectedGame);
        refreshSelectedGame();
        button("SELECT INSTALLED GAME", this::chooseGame);
        button("LAUNCH GAME", this::launchGame);

        card("SERVER ADDRESS", "Save your VORTEX RP server address here for quick access. The launcher does not pretend to connect unless your game supports a connection link.");
        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setTextColor(Color.WHITE);
        serverInput.setHintTextColor(Color.GRAY);
        serverInput.setHint("Server IP or hostname");
        serverInput.setText(prefs.getString("server_address", ""));
        serverInput.setPadding(16, 12, 16, 12);
        serverInput.setBackground(shape(panel, purple));
        content.addView(serverInput, new LinearLayout.LayoutParams(-1, -2));
        button("SAVE SERVER ADDRESS", () -> {
            String address = serverInput.getText().toString().trim();
            prefs.edit().putString("server_address", address).apply();
            Toast.makeText(this, address.isEmpty() ? "Server address cleared" : "Server address saved", Toast.LENGTH_SHORT).show();
        });
        button("COPY SERVER ADDRESS", () -> {
            String address = serverInput.getText().toString().trim();
            if (address.isEmpty()) {
                Toast.makeText(this, "Enter a server address first.", Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VORTEX RP server", address));
            Toast.makeText(this, "Server address copied.", Toast.LENGTH_SHORT).show();
        });
        card("QUICK LINKS", "Community links can be added once the official VORTEX RP Discord and website addresses are provided.");
        button("REFRESH INSTALLED APPS", this::chooseGame);
        TextView footer = text("VORTEX RP  •  UNOFFICIAL GAME LAUNCHER", 10, Color.GRAY, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 18, 0, 6);
        content.addView(footer);
    }

    private void refreshSelectedGame() {
        if (selectedGame == null) return;
        if (selectedPackage == null || selectedPackage.isEmpty()) {
            selectedGame.setText("NO GAME SELECTED");
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch == null) {
            selectedGame.setText("SELECTED GAME IS NOT INSTALLED");
            selectedPackage = "";
            prefs.edit().remove("game_package").apply();
        } else {
            try {
                CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(selectedPackage, 0));
                selectedGame.setText("SELECTED GAME  •  " + label);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                selectedGame.setText("SELECTED GAME IS NOT INSTALLED");
                selectedPackage = "";
                prefs.edit().remove("game_package").apply();
            }
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
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) filtered.add(info);
        }
        if (filtered.isEmpty()) {
            Toast.makeText(this, "No launchable apps found.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            ResolveInfo info = filtered.get(i);
            labels[i] = info.loadLabel(getPackageManager()) + "  (" + info.activityInfo.packageName + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle("Choose your GTA / SA-MP game")
            .setItems(labels, (dialog, which) -> {
                selectedPackage = filtered.get(which).activityInfo.packageName;
                prefs.edit().putString("game_package", selectedPackage).apply();
                refreshSelectedGame();
                Toast.makeText(this, "Game selected.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void launchGame() {
        if (selectedPackage == null || selectedPackage.isEmpty()) {
            Toast.makeText(this, "Select your installed game first.", Toast.LENGTH_LONG).show();
            chooseGame();
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launch == null) {
            Toast.makeText(this, "That game is not installed. Select another app.", Toast.LENGTH_LONG).show();
            selectedPackage = "";
            prefs.edit().remove("game_package").apply();
            refreshSelectedGame();
            return;
        }
        try {
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the selected game.", Toast.LENGTH_LONG).show();
        }
    }
}
