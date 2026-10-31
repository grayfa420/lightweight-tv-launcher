package com.cleanhome.tv;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * iOS-style Wi-Fi fan (a wedge and two bands spanning 90°) with as many bands lit as the
 * signal is good. Follows the view's tint, so it works inside {@link Ui#iconButton}.
 */
final class WifiDrawable extends Drawable {
    private static final float[][] BANDS = {{0f, 0.36f}, {0.49f, 0.69f}, {0.82f, 1f}};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path band = new Path();
    private final RectF outer = new RectF();
    private final RectF inner = new RectF();
    private final int sizePx;
    private int color = 0xFFFFFFFF;
    private int bars = 3;

    WifiDrawable(int sizePx) {
        this.sizePx = sizePx;
        // Fill plus a thin round-joined stroke softens the band corners.
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(sizePx * 0.05f);
    }

    /** Signal quality 0..100, or -1 when not connected (all bands faint). */
    void setQuality(int quality) {
        int b = quality < 0 ? 0 : quality >= 67 ? 3 : quality >= 34 ? 2 : 1;
        if (b != bars) {
            bars = b;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect box = getBounds();
        // The fan is as tall as its radius and 1.41× as wide; size it to the box and centre it.
        float r = Math.min(box.width() / 1.42f, box.height()) * 0.92f;
        float cx = box.exactCenterX();
        float apexY = box.exactCenterY() + r / 2f;
        for (int i = 0; i < BANDS.length; i++) {
            float ri = r * BANDS[i][0];
            float ro = r * BANDS[i][1];
            band.reset();
            outer.set(cx - ro, apexY - ro, cx + ro, apexY + ro);
            if (ri == 0) {
                band.moveTo(cx, apexY);
                band.arcTo(outer, 225, 90);
            } else {
                inner.set(cx - ri, apexY - ri, cx + ri, apexY + ri);
                band.arcTo(outer, 225, 90, true);
                band.arcTo(inner, 315, -90);
            }
            band.close();
            int alpha = bars >= i + 1 ? 0xFF : 0x48;
            paint.setColor((color & 0x00FFFFFF) | (alpha << 24));
            c.drawPath(band, paint);
        }
    }

    @Override
    public void setTintList(ColorStateList tint) {
        color = tint != null ? tint.getColorForState(getState(), tint.getDefaultColor()) : 0xFFFFFFFF;
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
