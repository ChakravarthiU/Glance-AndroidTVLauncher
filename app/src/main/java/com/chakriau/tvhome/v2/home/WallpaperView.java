package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.chakriau.tvhome.v2.festive.SpecialDayStore;
import com.chakriau.tvhome.v2.festive.SpecialDays;
import com.chakriau.tvhome.v2.sky.SkyMath;
import com.chakriau.tvhome.v2.wallpapers.Photos;
import com.chakriau.tvhome.v2.wallpapers.Rotation;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home's wallpaper (Requirements.md 5.1): on a special day its own photos ({@link SpecialDays}), otherwise the
 * ticked phone photos, one fixed or several taking turns, with an optional cross-fade; the built-in
 * {@link DefaultWallpaper} when the wallpaper is off or nothing is ticked. It changes only
 * while Home is visible ({@link #start} / {@link #stop}), and decodes at screen size, one photo at a time
 * (two during a fade).
 */
final class WallpaperView extends FrameLayout {

    private static final String TAG = "TvHomeWallpaper";
    private static final long FADE_MS = 1500;

    private final Photos photos;
    private final WallpaperStore store;
    private final SpecialDayStore specialDays;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final DefaultWallpaper fallback;
    private final ImageView[] layers = new ImageView[2];
    private int top = 0;
    private String shownId;
    private boolean running;
    private final Runnable tick = this::refresh;
    private Runnable onChange;
    private LocalDate shownDay;
    /** The theme's sky box, top and bottom in design pixels of 1080: where {@link #skyLight()} looks. */
    private int skyTop = 180, skyBottom = 612;
    /** How bright the photo on show is across the sky box (SkyMath.skyLight); null for the built-in drawing. */
    private float[] skyLight;

    WallpaperView(Context c) {
        super(c);
        photos = new Photos(c);
        store = new WallpaperStore(c);
        specialDays = new SpecialDayStore(c);
        fallback = new DefaultWallpaper(c);
        addView(fallback, match());
        for (int i = 0; i < 2; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setVisibility(View.GONE);
            layers[i] = v;
            addView(v, match());
        }
    }

    private static LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Told on the main thread when a different picture is shown (the eyes move to new spots, Requirements 5.2). */
    void setOnChange(Runnable r) {
        onChange = r;
    }

    /** Where the theme's sky is (design pixels, 0..1080), so {@link #skyLight()} measures the right band. */
    void setSkyBand(int top, int bottom) {
        skyTop = top;
        skyBottom = bottom;
    }

    /** How bright the photo on show is across the sky (left to right, 0..1), or null for the built-in drawing. */
    float[] skyLight() {
        return skyLight;
    }

    /** Home is visible: show the right photo now (Settings may have changed it) and keep rotating. */
    void start() {
        running = true;
        refresh();
    }

    /** Called every minute while Home is visible: a new day may bring (or end) special-day photos. */
    void onMinute() {
        if (running && !LocalDate.now().equals(shownDay)) refresh();
    }

    /** Home is hidden: no more changes; the photo leaving in a fade is let go. */
    void stop() {
        running = false;
        main.removeCallbacks(tick);
        // Finish a fade at once (a cancelled fade doesn't run its end action).
        ImageView shown = layers[top];
        shown.animate().cancel();
        shown.setAlpha(1f);
        ImageView under = layers[1 - top];
        under.animate().cancel();
        under.setImageDrawable(null);
        under.setVisibility(View.GONE);
        if (shown.getVisibility() == View.VISIBLE) fallback.setVisibility(View.GONE);
    }

    /** Memory is short (Home in the background): let the photo go too; it is decoded again on return. */
    void release() {
        stop();
        for (ImageView v : layers) {
            v.setImageDrawable(null);
            v.setVisibility(View.GONE);
        }
        shownId = null;
        fallback.setVisibility(View.VISIBLE);
    }

    void shutdown() {
        release();
        worker.shutdownNow();
    }

    private void refresh() {
        main.removeCallbacks(tick);
        if (!running) return;
        shownDay = LocalDate.now();
        final String onScreen = shownId;
        final int w = Math.max(1, getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels);
        final int h = Math.max(1, getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels);
        worker.execute(() -> {
            String id = null;
            Bitmap bitmap = null;
            float[] light = null;
            long wait = -1;
            boolean fade = false;
            try {
                List<String> all = photos.list();
                // A special day's photos come first (SpecialDays), whatever is ticked; else the ticked ones.
                List<String> ticked = SpecialDays.photos(LocalDate.now(), specialDays.days(), all);
                if (ticked.isEmpty() && store.enabled()) ticked = store.tickedInOrder(all);
                if (!ticked.isEmpty()) {
                    long now = System.currentTimeMillis();
                    int interval = store.intervalMinutes();
                    id = Rotation.current(ticked, store.current(), store.shownAt(), now, interval, store.shuffle(),
                            random);
                    if (!id.equals(store.current())) store.setCurrent(id, now);
                    if (ticked.size() > 1) wait = Math.max(1000, Rotation.untilDue(store.shownAt(), now, interval));
                    if (!id.equals(onScreen)) {
                        bitmap = Photos.decode(photos.file(id), w, h);
                        light = measureSky(bitmap);
                    }
                    fade = store.crossFade() && onScreen != null;
                }
            } catch (RuntimeException | OutOfMemoryError e) {
                Log.w(TAG, "wallpaper failed: " + e);
                id = null;
            }
            final String newId = id;
            final Bitmap b = bitmap;
            final float[] bLight = light;
            final long next = wait;
            final boolean withFade = fade;
            main.post(() -> {
                if (!running) return;
                if (newId == null) {
                    showFallback();
                } else if (b != null) {
                    show(newId, b, bLight, withFade);
                } else if (!newId.equals(shownId)) {
                    showFallback();   // the file couldn't be read
                }
                if (next > 0) main.postDelayed(tick, next);
            });
        });
    }

    /**
     * How bright the photo is across the sky box: shrunk to 64 × 36 (a few KB; the photo is 16:9 like the screen),
     * the sky's rows read and averaged in 16 columns. Null if it can't be measured.
     */
    private float[] measureSky(Bitmap photo) {
        if (photo == null) return null;
        try {
            Bitmap small = Bitmap.createScaledBitmap(photo, 64, 36, true);
            int y0 = Math.max(0, Math.round(skyTop * 36f / 1080f));
            int y1 = Math.min(36, Math.max(y0 + 1, Math.round(skyBottom * 36f / 1080f)));
            int[] px = new int[64 * (y1 - y0)];
            small.getPixels(px, 0, 64, 0, y0, 64, y1 - y0);
            if (small != photo) small.recycle();
            return SkyMath.skyLight(px, 64, y1 - y0, 16);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "can't measure the wallpaper: " + e);
            return null;
        }
    }

    private void show(String id, Bitmap bitmap, float[] light, boolean fade) {
        ImageView old = layers[top];
        top = 1 - top;
        ImageView now = layers[top];
        now.animate().cancel();
        now.setImageBitmap(bitmap);
        now.setVisibility(View.VISIBLE);
        now.bringToFront();
        shownId = id;
        skyLight = light;
        if (onChange != null) onChange.run();
        if (fade && old.getVisibility() == View.VISIBLE) {
            now.setAlpha(0f);
            now.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
                old.setImageDrawable(null);   // only one photo decoded once the fade is over
                old.setVisibility(View.GONE);
                fallback.setVisibility(View.GONE);
            }).start();
        } else {
            now.setAlpha(1f);
            old.setImageDrawable(null);
            old.setVisibility(View.GONE);
            fallback.setVisibility(View.GONE);
        }
    }

    private void showFallback() {
        boolean changed = shownId != null;
        fallback.setVisibility(View.VISIBLE);
        for (ImageView v : layers) {
            v.animate().cancel();
            v.setImageDrawable(null);
            v.setVisibility(View.GONE);
        }
        shownId = null;
        skyLight = null;
        if (changed && onChange != null) onChange.run();
    }
}
