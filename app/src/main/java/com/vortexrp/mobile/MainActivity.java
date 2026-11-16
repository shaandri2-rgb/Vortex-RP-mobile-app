package com.vortexrp.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private LinearLayout root, content, nav;
    private final int bg = Color.rgb(10, 8, 17);
    private final int panel = Color.rgb(24, 19, 34);
    private final int purple = Color.rgb(181, 55, 255);
    private int wallpaperIndex = 1;
    private static final int WALLPAPER_COUNT = 11;
    private ImageView wallpaperPreview;
    private TextView wallpaperCounter;

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

    private Button makeButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE);
        b.setBackground(shape(purple));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void button(String label, Runnable action) {
        Button b = makeButton(label, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 8, 0, 8);
        content.addView(b, p);
    }

    private void show(String page) {
        content.removeAllViews(); nav.removeAllViews();
        if (page.equals("Home")) {
            card("WELCOME TO VORTEX RP", "Your next story begins here. Join the community and explore roleplay in San Andreas.");
            card("SERVER STATUS", "Live server status is not connected yet. Server details can be added when the host address is ready.");
            button("OPEN WALLPAPER VAULT", () -> show("Gallery"));
            button("CREATE ACCOUNT / SIGN IN", () -> show("Profile"));
        } else if (page.equals("Forum")) {
            card("COMMUNITY FORUM", "Forum posts need a secure online backend before members can publish and sync discussions.");
        } else if (page.equals("Store")) {
            card("VORTEX STORE", "VIP packages and in-game products will appear after secure store and payment integration.");
            card("PAYMENTS DISABLED", "This build does not collect payment details or process purchases.");
        } else if (page.equals("Gallery")) {
            showGallery();
        } else if (page.equals("Discord")) {
            card("JOIN DISCORD", "The official invite link has not been configured in this build yet.");
        } else {
            card("PLAYER PROFILE", "Account creation and sign-in require a secure backend. No passwords are stored in this app.");
            button("SIGN IN", () -> Toast.makeText(this, "Account service is not connected yet.", Toast.LENGTH_SHORT).show());
            button("REGISTER", () -> Toast.makeText(this, "Account service is not connected yet.", Toast.LENGTH_SHORT).show());
        }

        String[] pages = {"Home", "Forum", "Store", "Gallery", "Discord", "Profile"};
        for (String item : pages) {
            TextView t = text(item, 11, item.equals(page) ? purple : Color.LTGRAY, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(3, 15, 3, 15);
            t.setOnClickListener(v -> show(item));
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void showGallery() {
        card("VORTEX WALLPAPER VAULT", "11 high-resolution wallpapers are included offline. Browse them and save your favourites to your phone.");
        wallpaperPreview = new ImageView(this);
        wallpaperPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperPreview.setBackground(shape(panel));
        int previewHeight = (int)(getResources().getDisplayMetrics().heightPixels * 0.43f);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, previewHeight);
        imageParams.setMargins(0, 0, 0, 10);
        content.addView(wallpaperPreview, imageParams);

        wallpaperCounter = text("", 14, Color.LTGRAY, true);
        wallpaperCounter.setGravity(Gravity.CENTER);
        content.addView(wallpaperCounter);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button previous = makeButton("PREVIOUS", () -> changeWallpaper(-1));
        Button next = makeButton("NEXT", () -> changeWallpaper(1));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(3, 5, 3, 5);
        controls.addView(previous, half);
        controls.addView(next, half);
        content.addView(controls);
        button("SAVE WALLPAPER TO PHONE", this::saveWallpaper);
        displayWallpaper();
    }

    private void changeWallpaper(int direction) {
        wallpaperIndex = ((wallpaperIndex - 1 + direction + WALLPAPER_COUNT) % WALLPAPER_COUNT) + 1;
        displayWallpaper();
    }

    private String wallpaperPath() {
        return String.format(java.util.Locale.US, "wallpapers/vortex_%02d.jpg", wallpaperIndex);
    }

    private void displayWallpaper() {
        try (InputStream in = getAssets().open(wallpaperPath())) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int targetW = Math.min(1500, getResources().getDisplayMetrics().widthPixels);
            int targetH = 2100;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetW &&
                   bounds.outHeight / (sample * 2) >= targetH) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream image = getAssets().open(wallpaperPath())) {
                Bitmap bitmap = BitmapFactory.decodeStream(image, null, options);
                wallpaperPreview.setImageBitmap(bitmap);
            }
            wallpaperCounter.setText("WALLPAPER " + wallpaperIndex + " / " + WALLPAPER_COUNT);
        } catch (Exception e) {
            Toast.makeText(this, "Could not load wallpaper.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveWallpaper() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Wallpaper saving requires Android 10 or newer.", Toast.LENGTH_LONG).show();
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "VORTEX_RP_Wallpaper_" + wallpaperIndex + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VORTEX RP");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        try {
            android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create image entry");
            try (InputStream in = getAssets().open(wallpaperPath());
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Could not open image output");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, complete, null, null);
            Toast.makeText(this, "Saved to Pictures / VORTEX RP", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
