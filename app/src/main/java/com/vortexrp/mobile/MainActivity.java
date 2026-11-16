package com.vortexrp.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout root, content, nav;
    private final int bg = Color.rgb(10, 8, 17);
    private final int panel = Color.rgb(24, 19, 34);
    private final int purple = Color.rgb(181, 55, 255);
    private final int red = Color.rgb(255, 55, 86);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        setContentView(root);

        TextView brand = new TextView(this);
        brand.setText("V O R T E X   R P");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(23);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(12, 25, 12, 18);
        root.addView(brand);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(18, 10, 18, 20);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setBackgroundColor(panel);
        nav.setGravity(Gravity.CENTER);
        root.addView(nav);
        show("Home");
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(22); return d;
    }

    private void card(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 16, 18, 16);
        box.setBackground(shape(panel));
        TextView h = text(title, 18, Color.WHITE, true);
        TextView b = text(body, 14, Color.LTGRAY, false);
        b.setPadding(0, 8, 0, 0);
        box.addView(h); box.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, 14);
        content.addView(box, p);
    }

    private void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE);
        b.setBackground(shape(purple));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 8, 0, 8);
        content.addView(b, p);
    }

    private void show(String page) {
        content.removeAllViews(); nav.removeAllViews();
        if (page.equals("Home")) {
            card("WELCOME TO VORTEX RP", "Your next story begins here. Join the community and explore roleplay in San Andreas.");
            card("SERVER STATUS", "Server connection will be configured when the game server address is provided.");
            button("JOIN OUR COMMUNITY", () -> show("Discord"));
            button("CREATE ACCOUNT / SIGN IN", () -> show("Profile"));
        } else if (page.equals("Forum")) {
            card("COMMUNITY FORUM", "Discuss roleplay, report issues and share announcements. Online forum storage will require a backend.");
            button("NEW DISCUSSION", () -> show("Profile"));
        } else if (page.equals("Store")) {
            card("VORTEX STORE", "VIP packages and in-game products will appear here after secure store integration.");
            card("COMING SOON", "No payments are enabled in this starter version.");
        } else if (page.equals("Discord")) {
            card("JOIN DISCORD", "Add your official Discord invite in the app configuration to connect your community.");
        } else {
            card("PLAYER PROFILE", "Account registration and sign-in UI will be connected to a secure backend in a later update.");
            button("SIGN IN", () -> Toast.makeText(this, "Account service is not connected yet.", Toast.LENGTH_SHORT).show());
            button("REGISTER", () -> Toast.makeText(this, "Account service is not connected yet.", Toast.LENGTH_SHORT).show());
        }
        String[] pages = {"Home", "Forum", "Store", "Discord", "Profile"};
        for (String item : pages) {
            TextView t = text(item, 12, item.equals(page) ? purple : Color.LTGRAY, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(7, 16, 7, 16);
            t.setOnClickListener(v -> show(item));
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }
}
