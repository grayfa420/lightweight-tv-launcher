package com.cleanhome.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

/** Shared look for the home screen's controls. */
final class Ui {
    static final int TEXT = 0xFFF2F4F8;
    static final int TEXT_MUTED = 0xFFA9B1C2;
    static final int DARK = 0xFF07080B;
    static final int CARD = 0xFF1A1F2B;
    static final int GLASS = 0x8C0B0E16;
    static final int GLASS_EDGE = 0x22FFFFFF;
    private static final int BUTTON_IDLE = 0x26FFFFFF;

    private Ui() {
    }

    /** Round icon button that turns white with a dark icon when focused. */
    static ImageView iconButton(Context ctx, int iconRes, String description, int sizeDp, Runnable action) {
        return iconButton(ctx, ctx.getDrawable(iconRes), description, sizeDp, action, null);
    }

    /** Same, with any drawable as the icon; {@code onFocus} (optional) also gets focus changes. */
    static ImageView iconButton(Context ctx, android.graphics.drawable.Drawable icon, String description,
            int sizeDp, Runnable action, java.util.function.Consumer<Boolean> onFocus) {
        ImageView v = new ImageView(ctx);
        v.setImageDrawable(icon);
        v.setContentDescription(description);
        v.setImageTintList(ColorStateList.valueOf(TEXT));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(ctx, sizeDp / 4f);
        v.setPadding(pad, pad, pad, pad);
        v.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(BUTTON_IDLE);
        v.setBackground(bg);
        v.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? TEXT : BUTTON_IDLE);
            v.setImageTintList(ColorStateList.valueOf(has ? DARK : TEXT));
            view.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f)
                    .setInterpolator(has ? Motion.SPRING : Motion.SETTLE)
                    .setDuration(has ? Motion.SPRING_MS : Motion.FADE_MS).start();
            if (onFocus != null) onFocus.accept(has);
        });
        v.setOnClickListener(view -> action.run());
        return v;
    }

    static GradientDrawable glass(Context ctx, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, radiusDp));
        g.setColor(GLASS);
        g.setStroke(dp(ctx, 1), GLASS_EDGE);
        return g;
    }

    static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
