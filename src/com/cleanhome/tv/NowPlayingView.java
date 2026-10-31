package com.cleanhome.tv;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

/** Spotify's current track with play/pause/skip, read from its media session. Hidden when idle. */
final class NowPlayingView extends LinearLayout {
    private static final String SPOTIFY = "com.spotify.tv.android";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final Runnable openSpotify;

    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final View progressFill;
    private final ImageView playPause;

    private MediaController controller;
    private String artKey;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            update();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            update();
        }

        @Override
        public void onSessionDestroyed() {
            attach(null);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickSpotify;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            main.postDelayed(this, 1000);
        }
    };

    NowPlayingView(Context ctx, Runnable openSpotify) {
        super(ctx);
        this.openSpotify = openSpotify;
        sessions = ctx.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(ctx, MediaListener.class);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.glass(ctx, 20));
        setPadding(dp(10), dp(10), dp(16), dp(10));
        setVisibility(GONE);

        // Left part: artwork + text, opens Spotify.
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(4), dp(4), dp(12), dp(4));
        info.setFocusable(true);
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setCornerRadius(dp(12));
        infoBg.setColor(0);
        info.setBackground(infoBg);
        info.setOnFocusChangeListener((v, has) -> infoBg.setColor(has ? 0x33FFFFFF : 0));
        info.setOnClickListener(v -> this.openSpotify.run());

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setCornerRadius(dp(8));
        artBg.setColor(Ui.CARD);
        art.setBackground(artBg);
        art.setClipToOutline(true);
        info.addView(art, new LayoutParams(dp(52), dp(52)));

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);

        title = line(ctx, 19, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        artist = line(ctx, 13, Ui.TEXT_MUTED);
        text.addView(title);
        text.addView(artist);

        FrameLayout track = new FrameLayout(ctx);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(2));
        trackBg.setColor(0x33FFFFFF);
        track.setBackground(trackBg);
        progressFill = new View(ctx);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(2));
        fillBg.setColor(0xFF1ED760); // Spotify green
        progressFill.setBackground(fillBg);
        progressFill.setPivotX(0);
        progressFill.setScaleX(0);
        track.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));
        LayoutParams trackLp = new LayoutParams(-1, dp(3));
        trackLp.topMargin = dp(8);
        text.addView(track, trackLp);

        info.addView(text, new LayoutParams(dp(260), -2));
        addView(info);

        addView(control(R.drawable.ic_prev, "Previous",
                () -> { if (controller != null) controller.getTransportControls().skipToPrevious(); }));
        playPause = control(R.drawable.ic_pause, "Play/pause", this::togglePlay);
        addView(playPause);
        addView(control(R.drawable.ic_next, "Next",
                () -> { if (controller != null) controller.getTransportControls().skipToNext(); }));
    }

    void start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main);
            pickSpotify(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            // Notification-listener access not granted; stay hidden.
            setVisibility(GONE);
        }
        main.post(tick);
    }

    void stop() {
        main.removeCallbacks(tick);
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (Exception ignored) {
        }
        attach(null);
    }

    private void pickSpotify(List<MediaController> active) {
        MediaController found = null;
        if (active != null) {
            for (MediaController c : active) {
                if (SPOTIFY.equals(c.getPackageName())) {
                    found = c;
                    break;
                }
            }
        }
        if (found == null || controller == null
                || !found.getSessionToken().equals(controller.getSessionToken())) {
            attach(found);
        }
    }

    private void attach(MediaController c) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = c;
        if (controller != null) controller.registerCallback(callback, main);
        update();
    }

    private void update() {
        MediaMetadata meta = controller != null ? controller.getMetadata() : null;
        PlaybackState state = controller != null ? controller.getPlaybackState() : null;
        int s = state != null ? state.getState() : PlaybackState.STATE_NONE;
        boolean idle = meta == null || s == PlaybackState.STATE_NONE
                || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR;
        if (idle) {
            if (hasFocus()) focusSearch(FOCUS_UP);
            setVisibility(GONE);
            return;
        }
        if (getVisibility() != VISIBLE) {
            // Rise into place when music starts.
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(Ui.dp(getContext(), 24));
            animate().alpha(1f).translationY(0).setInterpolator(Motion.EASE_OUT).setDuration(500).start();
        }
        title.setText(meta.getString(MediaMetadata.METADATA_KEY_TITLE));
        artist.setText(meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
        playPause.setImageResource(isPlaying(state) ? R.drawable.ic_pause : R.drawable.ic_play);
        loadArt(meta);
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null || getVisibility() != VISIBLE) return;
        MediaMetadata meta = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        long duration = meta != null ? meta.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        if (state == null || duration <= 0) {
            progressFill.setScaleX(0);
            return;
        }
        long pos = state.getPosition();
        if (isPlaying(state)) {
            pos += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime())
                    * state.getPlaybackSpeed());
        }
        progressFill.setScaleX(Math.max(0f, Math.min(1f, pos / (float) duration)));
    }

    private void togglePlay() {
        if (controller == null) return;
        if (isPlaying(controller.getPlaybackState())) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    private static boolean isPlaying(PlaybackState state) {
        return state != null && (state.getState() == PlaybackState.STATE_PLAYING
                || state.getState() == PlaybackState.STATE_BUFFERING);
    }

    /** Uses the bitmap in the session if present, otherwise downloads the artwork URL. */
    private void loadArt(MediaMetadata meta) {
        Bitmap bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        String uri = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = meta.getString(MediaMetadata.METADATA_KEY_ART_URI);
        String key = bmp != null ? "bmp:" + bmp.getGenerationId() : uri;
        if (TextUtils.equals(key, artKey)) return;
        artKey = key;

        if (bmp != null) {
            art.setImageBitmap(bmp);
            fadeInArt();
        } else if (uri != null && uri.startsWith("http")) {
            art.setImageDrawable(null);
            final String url = uri;
            new Thread(() -> {
                try (InputStream in = new URL(url).openStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    main.post(() -> {
                        if (url.equals(artKey)) {
                            art.setImageBitmap(b);
                            fadeInArt();
                        }
                    });
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            try {
                art.setImageDrawable(getContext().getPackageManager().getApplicationIcon(SPOTIFY));
            } catch (Exception ignored) {
                art.setImageDrawable(null);
            }
        }
    }

    private void fadeInArt() {
        art.setAlpha(0f);
        art.setScaleX(0.92f);
        art.setScaleY(0.92f);
        art.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(Motion.SPRING)
                .setDuration(Motion.SPRING_MS).start();
    }

    private ImageView control(int icon, String description, Runnable action) {
        ImageView b = Ui.iconButton(getContext(), icon, description, 40, action);
        LayoutParams lp = new LayoutParams(dp(40), dp(40));
        lp.leftMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private static TextView line(Context ctx, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }
}
