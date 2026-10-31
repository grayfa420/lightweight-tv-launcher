package com.cleanhome.tv;

import android.animation.TimeInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

/** Apple-TV-like motion: springy focus, soft eased fades. */
final class Motion {
    /** Damped spring: overshoots ~10% of the distance, then settles. */
    static final TimeInterpolator SPRING = t -> (float) (1 - Math.exp(-9 * t) * Math.cos(13 * t));
    /** iOS-style ease-out for fades and slides. */
    static final TimeInterpolator EASE_OUT = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
    static final TimeInterpolator SETTLE = new DecelerateInterpolator(2f);

    static final long SPRING_MS = 480;
    static final long FADE_MS = 260;

    private Motion() {
    }
}
