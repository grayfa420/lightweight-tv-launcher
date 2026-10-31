package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.Arrays;

/**
 * Rotating wallpaper from assets/wallpapers/. The photo is picked from the wall clock
 * (changes at :00 and :30), so it stays in step across launcher restarts, and crossfades.
 * Images are pre-cropped to 1280x720 by hand; CENTER_CROP is the fallback for any that
 * aren't, so nothing is ever stretched.
 */
final class WallpaperView extends FrameLayout {
    private static final String DIR = "wallpapers";
    private static final long PERIOD_MS = 30 * 60 * 1000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String[] files;
    private ImageView front;
    private ImageView back;
    private int shown = -1;

    private final Runnable next = this::showCurrent;

    WallpaperView(Context ctx) {
        super(ctx);
        String[] list;
        try {
            list = ctx.getAssets().list(DIR);
        } catch (Exception e) {
            list = new String[0];
        }
        Arrays.sort(list);
        files = list;
        back = image(ctx);
        front = image(ctx);
        addView(back, new LayoutParams(-1, -1));
        addView(front, new LayoutParams(-1, -1));
    }

    void start() {
        showCurrent();
    }

    void stop() {
        main.removeCallbacks(next);
    }

    private void showCurrent() {
        main.removeCallbacks(next);
        if (files.length == 0) return;
        long now = System.currentTimeMillis();
        int index = (int) ((now / PERIOD_MS) % files.length);
        if (index != shown) load(index, shown != -1);
        main.postDelayed(next, PERIOD_MS - (now % PERIOD_MS) + 500);
    }

    private void load(int index, boolean fade) {
        shown = index;
        String path = DIR + "/" + files[index];
        new Thread(() -> {
            Bitmap bmp;
            try (InputStream in = getContext().getAssets().open(path)) {
                bmp = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                return;
            }
            main.post(() -> {
                if (index != shown) return;
                if (!fade) {
                    front.setImageBitmap(bmp);
                    front.setAlpha(0f);
                    front.animate().alpha(1f).setInterpolator(Motion.EASE_OUT).setDuration(800).start();
                    return;
                }
                // New photo goes behind, slightly zoomed, and settles while the old one fades out on top.
                back.setImageBitmap(bmp);
                back.setScaleX(1.06f);
                back.setScaleY(1.06f);
                back.animate().scaleX(1f).scaleY(1f).setInterpolator(Motion.EASE_OUT).setDuration(3000).start();
                ImageView old = front;
                old.animate().alpha(0f).setInterpolator(Motion.EASE_OUT).setDuration(1500).withEndAction(() -> {
                    old.setImageDrawable(null);
                    old.setAlpha(1f);
                    bringChildToFront(back);
                    front = back;
                    back = old;
                }).start();
            });
        }).start();
    }

    private static ImageView image(Context ctx) {
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }
}
