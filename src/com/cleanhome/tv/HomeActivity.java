package com.cleanhome.tv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Android TV home screen: clock, weather, a grid of chosen apps, Spotify,
 * a Wi-Fi button (with join-by-QR card), Settings,
 * plus Live TV and Inputs on TVs that have a tuner. Apple-TV-style motion throughout.
 */
public class HomeActivity extends Activity {

    // Default home screen apps, in order, until the user edits the list (see AppList); missing ones are skipped.
    static final String[] APPS = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream",
            "com.skyshowtime.skyshowtime.google",
            "org.jellyfin.androidtv",
            "si.titan.stb.atv",
            "tv.twitch.android.app",
            "com.phonegap.voyo",
            "com.spotify.tv.android",
            "com.android.vending",
    };
    // Tuner app on the Tesla TV; on devices without it (TV sticks) Live TV and Inputs are hidden.
    private static final String LIVE_TV = "com.mediatek.wwtv.tvcenter";
    private static final int MAX_TILE_W = 152;
    private static final int TILE_GAP = 20;
    private static final int SIDE_PADDING = 56;

    private static final String SPOTIFY = "com.spotify.tv.android";
    private static final long WEATHER_REFRESH_MS = 20 * 60 * 1000;

    private static final int TEXT = Ui.TEXT;
    private static final int TEXT_MUTED = Ui.TEXT_MUTED;
    private static final int CARD = Ui.CARD;

    private LinearLayout grid;
    private final List<View> tiles = new ArrayList<>();
    private int lastFocused = 0;
    private boolean firstShow = true;
    private View header;
    private View content;
    /** Last D-pad direction, so a newly focused tile can lean the way focus travelled. */
    private int lastDirection = 0;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView weatherTemp;
    private TextView weatherIcon;
    private TextView weatherDetail;
    private long weatherFetchedAt = -WEATHER_REFRESH_MS;
    private NowPlayingView nowPlaying;
    private WallpaperView wallpaper;
    private WifiDrawable wifiIcon;
    private View wifiButton;
    private WifiCard wifiCard;
    private final Runnable wifiTick = new Runnable() {
        @Override
        public void run() {
            wifiIcon.setQuality(wifiCard.refresh());
            // Faster while the card is open, so latency and live throughput keep moving.
            main.postDelayed(this, wifiCard.isShown() ? 2000 : 5000);
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            main.postDelayed(this, WEATHER_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.DARK);

        wallpaper = new WallpaperView(this);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        // Darken the photo so text and tiles stay readable over bright wallpapers.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xC0000000, 0x70000000, 0x80000000, 0xD0000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(SIDE_PADDING), dp(32), dp(SIDE_PADDING), dp(28));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        this.content = content;

        header = buildHeader();
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        View bottom = new View(this);
        content.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 0.6f));

        nowPlaying = new NowPlayingView(this, () -> launchPackage(SPOTIFY));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(nowPlaying, np);

        // Wi-Fi details card floats over the screen under the Wi-Fi button while it's focused.
        wifiCard = new WifiCard(this);
        wifiCard.setVisibility(View.INVISIBLE);
        root.addView(wifiCard, new FrameLayout.LayoutParams(-2, -2));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildGrid();
        if (!tiles.isEmpty()) {
            // -1 = the edit tile, which is always last (its position moves as apps are added).
            int i = lastFocused < 0 ? tiles.size() - 1 : Math.min(lastFocused, tiles.size() - 1);
            tiles.get(i).requestFocus();
        }
        if (firstShow) {
            playEntrance();
            firstShow = false;
        } else {
            // Coming back from an app: the home screen settles back into place, like tvOS.
            content.setScaleX(1.04f);
            content.setScaleY(1.04f);
            content.setAlpha(0.4f);
            content.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setInterpolator(Motion.EASE_OUT).setDuration(450).start();
        }
        nowPlaying.start();
        wallpaper.start();
        main.post(wifiTick);
        long sinceFetch = SystemClock.elapsedRealtime() - weatherFetchedAt;
        main.postDelayed(weatherTick, Math.max(0, WEATHER_REFRESH_MS - sinceFetch));
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.stop();
        wallpaper.stop();
        main.removeCallbacks(weatherTick);
        main.removeCallbacks(wifiTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home jumps back to the first app.
        lastFocused = 0;
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                    || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                lastDirection = k;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** First appearance: header drops in, tiles rise in one after another. */
    private void playEntrance() {
        header.setAlpha(0f);
        header.setTranslationY(-dp(16));
        header.animate().alpha(1f).translationY(0).setInterpolator(Motion.EASE_OUT)
                .setDuration(600).start();
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            t.setAlpha(0f);
            t.setTranslationY(dp(28));
            t.setScaleX(0.94f);
            t.setScaleY(0.94f);
            t.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                    .setInterpolator(Motion.EASE_OUT).setStartDelay(120 + i * 40L)
                    .setDuration(550).start();
        }
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setClipChildren(false);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextColor(TEXT);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        clockCol.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d. MMMM");
        date.setFormat12Hour("EEEE, d. MMMM");
        date.setTextColor(TEXT_MUTED);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        date.setPadding(dp(2), dp(4), 0, 0);
        clockCol.addView(date);

        header.addView(clockCol);

        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(56));
        dl.leftMargin = dp(28);
        dl.rightMargin = dp(28);
        header.addView(divider, dl);

        header.addView(buildWeather(), new LinearLayout.LayoutParams(0, -2, 1f));

        if (launchIntent(LIVE_TV) != null) {
            header.addView(headerButton(R.drawable.ic_tv, "Live TV", () -> launchPackage(LIVE_TV)));
            header.addView(headerButton(R.drawable.ic_input, "Inputs",
                    () -> safeStart(new Intent("com.android.tv.action.VIEW_INPUTS"))));
        }
        // Wi-Fi: looks like the Settings button; its bars show signal, focus shows the QR card.
        wifiIcon = new WifiDrawable(dp(24));
        wifiButton = Ui.iconButton(this, wifiIcon, "Wi-Fi", 48,
                () -> safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS)),
                has -> {
                    if (has) showWifiCard();
                    else hideWifiCard();
                });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(dp(48), dp(48));
        wl.leftMargin = dp(14);
        header.addView(wifiButton, wl);
        header.addView(headerButton(R.drawable.ic_settings, "Settings",
                () -> safeStart(new Intent(Settings.ACTION_SETTINGS))));
        // Leftmost header control (Live TV, or Wi-Fi without a tuner): pressing left stays here
        // instead of dropping into the grid.
        View first = header.getChildAt(3);
        first.setId(View.generateViewId());
        first.setNextFocusLeftId(first.getId());
        return header;
    }

    private View buildWeather() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setVisibility(View.INVISIBLE); // until the first forecast arrives

        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.HORIZONTAL);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new TextView(this);
        weatherIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        weatherIcon.setIncludeFontPadding(false);
        now.addView(weatherIcon);
        weatherTemp = new TextView(this);
        weatherTemp.setTextColor(TEXT);
        weatherTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, 0, 0);
        now.addView(weatherTemp);
        col.addView(now);

        weatherDetail = new TextView(this);
        weatherDetail.setTextColor(TEXT_MUTED);
        weatherDetail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        weatherDetail.setPadding(dp(2), dp(6), 0, 0);
        col.addView(weatherDetail);
        return col;
    }

    private void refreshWeather() {
        new Thread(() -> {
            try {
                Weather w = Weather.fetch();
                main.post(() -> showWeather(w));
            } catch (Exception ignored) {
                // Offline: keep showing the last forecast.
            }
        }).start();
    }

    private void showWeather(Weather w) {
        weatherFetchedAt = SystemClock.elapsedRealtime();
        weatherIcon.setText(w.icon());
        weatherTemp.setText(Math.round(w.temperature) + "°");
        weatherDetail.setText("\u2602 " + w.rainChance + " %   \u2191" + Math.round(w.high)
                + "°  \u2193" + Math.round(w.low) + "°");
        ((View) weatherTemp.getParent().getParent()).setVisibility(View.VISIBLE);
    }

    private void showWifiCard() {
        wifiIcon.setQuality(wifiCard.refresh());
        wifiCard.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        wifiButton.getLocationInWindow(at);
        int screenW = getWindow().getDecorView().getWidth();
        float x = at[0] + wifiButton.getWidth() / 2f - wifiCard.getMeasuredWidth() / 2f;
        x = Math.max(dp(24), Math.min(x, screenW - wifiCard.getMeasuredWidth() - dp(24)));
        // Position via margins; translation is left free for the drop-in animation.
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wifiCard.getLayoutParams();
        lp.leftMargin = Math.round(x);
        // Just below the header row.
        View headerRow = (View) wifiButton.getParent();
        int[] row = new int[2];
        headerRow.getLocationInWindow(row);
        lp.topMargin = row[1] + headerRow.getHeight() + dp(8);
        wifiCard.setLayoutParams(lp);
        wifiCard.animate().cancel();
        wifiCard.setVisibility(View.VISIBLE);
        wifiCard.setAlpha(0f);
        wifiCard.setTranslationY(-dp(10));
        wifiCard.setScaleX(0.96f);
        wifiCard.setScaleY(0.96f);
        wifiCard.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                .setInterpolator(Motion.SPRING).setDuration(Motion.SPRING_MS).start();
    }

    private void hideWifiCard() {
        wifiCard.animate().cancel();
        wifiCard.animate().alpha(0f).translationY(-dp(6)).setInterpolator(Motion.EASE_OUT)
                .setDuration(160).withEndAction(() -> wifiCard.setVisibility(View.INVISIBLE)).start();
    }

    private View headerButton(int iconRes, String description, Runnable action) {
        View v = Ui.iconButton(this, iconRes, description, 48, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.leftMargin = dp(14);
        v.setLayoutParams(lp);
        return v;
    }

    private void buildGrid() {
        grid.removeAllViews();
        tiles.clear();
        PackageManager pm = getPackageManager();

        List<String> installed = new ArrayList<>();
        for (String pkg : AppList.load(this, APPS)) {
            if (launchIntent(pkg) != null) installed.add(pkg);
        }
        // Two rows while they fit (tiles shrink as apps are added), then a third. +1 for the edit tile.
        int count = installed.size() + 1;
        int columns = Math.min(7, Math.max(4, (count + 1) / 2));
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density - 2 * SIDE_PADDING;
        int tileW = (int) Math.min(MAX_TILE_W, (widthDp - (columns - 1) * TILE_GAP) / columns);
        int tileH = tileW * 9 / 16;

        LinearLayout row = null;
        for (String pkg : installed) {
            Intent launch = launchIntent(pkg);
            if (tiles.size() % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
                if (!tiles.isEmpty()) rp.topMargin = dp(20);
                grid.addView(row, rp);
            }
            View tile = buildTile(pm, pkg, launch, tiles.size(), tileW, tileH);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            if (tiles.size() % columns != 0) tp.leftMargin = dp(TILE_GAP);
            row.addView(tile, tp);
            tiles.add(tile);
        }

        if (tiles.size() % columns == 0) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            if (!tiles.isEmpty()) rp.topMargin = dp(20);
            grid.addView(row, rp);
        }
        View edit = buildEditTile(tileW, tileH);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        if (tiles.size() % columns != 0) ep.leftMargin = dp(TILE_GAP);
        row.addView(edit, ep);
        tiles.add(edit);
    }

    private View buildTile(PackageManager pm, String pkg, Intent launch, int index, int tileW, int tileH) {
        CharSequence name = pkg;
        Drawable banner = null;
        Drawable icon = null;
        try {
            // The launch activity's label is what users know the app by (NEO's app label is a class name).
            android.content.pm.ResolveInfo ri = pm.resolveActivity(launch, 0);
            name = ri != null ? ri.loadLabel(pm) : pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            banner = pm.getActivityBanner(launch);
            if (banner == null) banner = pm.getApplicationBanner(pkg);
            if (banner == null) icon = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        ImageView img = new ImageView(this);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return tile(img, new FrameLayout.LayoutParams(-1, -1), name, CARD, launch, pkg, index, tileW, tileH);
        }
        img.setImageDrawable(icon);
        return tile(img, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER), name, CARD,
                launch, pkg, index, tileW, tileH);
    }

    /** The last tile: a "+" that opens the picker for which apps the home screen shows. */
    private View buildEditTile(int tileW, int tileH) {
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_add);
        plus.setImageTintList(android.content.res.ColorStateList.valueOf(TEXT));
        int size = Math.min(dp(40), dp(tileH) / 2);
        return tile(plus, new FrameLayout.LayoutParams(size, size, Gravity.CENTER), "Uredi aplikacije",
                0x33FFFFFF, new Intent(this, AppPickerActivity.class), null, -1, tileW, tileH);
    }

    /**
     * A grid tile around {@code content}: Apple-TV-style lift, tilt, gloss and sheen on focus, a
     * zoom-out launch of {@code open}, and (when {@code infoPkg} is set) long-press OK for the
     * app's system info page.
     */
    private View tile(View content, FrameLayout.LayoutParams contentLp, CharSequence name, int background,
            Intent open, String infoPkg, int index, int tileW, int tileH) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);

        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(12));
        cardBg.setColor(background);
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.addView(content, contentLp);

        // Glossy top highlight, only visible while focused.
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        gloss.setCornerRadius(dp(12));
        gloss.setAlpha(0);
        card.setForeground(gloss);

        // Diagonal light sweep that crosses the tile once when it gains focus.
        View sheen = new View(this);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}));
        sheen.setRotation(20);
        sheen.setAlpha(0f);
        card.addView(sheen, new FrameLayout.LayoutParams(dp(tileW) / 3, dp(tileH) * 2, Gravity.CENTER_VERTICAL));

        card.setCameraDistance(10000 * getResources().getDisplayMetrics().density);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(TEXT);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        label.setPadding(0, dp(14), 0, 0);
        label.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);

        card.setAlpha(0.85f);
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().cancel();
            if (has) {
                lastFocused = index;
                // Lean the way focus came from, then spring upright while lifting toward you.
                float tilt = 7f;
                v.setRotationY(lastDirection == KeyEvent.KEYCODE_DPAD_RIGHT ? -tilt
                        : lastDirection == KeyEvent.KEYCODE_DPAD_LEFT ? tilt : 0);
                v.setRotationX(lastDirection == KeyEvent.KEYCODE_DPAD_DOWN ? tilt
                        : lastDirection == KeyEvent.KEYCODE_DPAD_UP ? -tilt : 0);
                v.animate().scaleX(1.13f).scaleY(1.13f).rotationX(0).rotationY(0).alpha(1f)
                        .translationZ(dp(18)).setInterpolator(Motion.SPRING)
                        .setDuration(Motion.SPRING_MS).start();
                gloss.setAlpha(255);
                sheen.setTranslationX(-dp(tileW) / 2f);
                sheen.setAlpha(1f);
                sheen.animate().translationX(dp(tileW) * 1.2f).setStartDelay(90)
                        .setInterpolator(Motion.EASE_OUT).setDuration(750)
                        .withEndAction(() -> sheen.setAlpha(0f)).start();
                label.setTranslationY(-dp(6));
                label.animate().alpha(1f).translationY(0).setInterpolator(Motion.EASE_OUT)
                        .setDuration(Motion.FADE_MS).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).rotationX(0).rotationY(0).alpha(0.85f)
                        .translationZ(0).setInterpolator(Motion.SETTLE)
                        .setDuration(Motion.FADE_MS).start();
                gloss.setAlpha(0);
                sheen.animate().cancel();
                sheen.setAlpha(0f);
                label.animate().alpha(0f).setInterpolator(Motion.EASE_OUT)
                        .setDuration(160).start();
            }
        });
        card.setOnClickListener(v -> {
            // Press dip, then the app zooms open out of the tile.
            v.animate().scaleX(1.06f).scaleY(1.06f).setInterpolator(Motion.EASE_OUT).setDuration(90)
                    .withEndAction(() -> {
                        v.animate().scaleX(1.13f).scaleY(1.13f).setInterpolator(Motion.SPRING)
                                .setDuration(Motion.SPRING_MS).start();
                        ActivityOptions zoom = ActivityOptions.makeScaleUpAnimation(
                                v, 0, 0, v.getWidth(), v.getHeight());
                        safeStart(open, zoom.toBundle());
                    }).start();
        });
        card.setOnKeyListener((v, keyCode, e) -> {
            // Long-press OK opens the app's system info page (force stop, clear cache…).
            if (infoPkg != null && keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    && e.getAction() == KeyEvent.ACTION_DOWN && e.isLongPress()) {
                safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + infoPkg)));
                return true;
            }
            return false;
        });

        col.addView(card, new LinearLayout.LayoutParams(dp(tileW), dp(tileH)));
        col.addView(label, new LinearLayout.LayoutParams(dp(tileW), -2));
        return col;
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private void launchPackage(String pkg) {
        Intent i = launchIntent(pkg);
        if (i != null) safeStart(i);
    }

    private void safeStart(Intent i) {
        safeStart(i, null);
    }

    private void safeStart(Intent i, Bundle options) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i, options);
        } catch (Exception ignored) {
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
