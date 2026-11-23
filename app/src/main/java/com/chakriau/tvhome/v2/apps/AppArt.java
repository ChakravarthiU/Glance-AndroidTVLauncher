package com.chakriau.tvhome.v2.apps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads each app's TV banner (or its icon when it has none) off the main thread and keeps recent ones in a small
 * cache. The package manager decodes them at the TV's density, never at a larger size.
 */
public final class AppArt {

    private static final String TAG = "TvHomeApps";

    /** Art for one app: a banner fills the tile; an icon is drawn smaller in the middle, with the label. */
    public static final class Art {
        public final Drawable drawable;
        public final boolean isBanner;

        Art(Drawable drawable, boolean isBanner) {
            this.drawable = drawable;
            this.isBanner = isBanner;
        }
    }

    public interface Target {
        void onArt(Art art);
    }

    private final PackageManager pm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Art> cache = new LruCache<>(64);

    public AppArt(Context context) {
        pm = context.getApplicationContext().getPackageManager();
    }

    /**
     * Delivers the app's art to the target on the main thread. The view's tag guards against a recycled view: art
     * arriving for an app the view no longer shows is dropped.
     */
    public void load(AppInfo app, ImageView view, Target target) {
        view.setTag(app.packageName);
        Art cached = cache.get(app.packageName);
        if (cached != null) {
            target.onArt(cached);
            return;
        }
        executor.execute(() -> {
            Art art = read(app);
            if (art.drawable != null) cache.put(app.packageName, art);
            main.post(() -> {
                if (app.packageName.equals(view.getTag())) target.onArt(art);
            });
        });
    }

    private Art read(AppInfo app) {
        try {
            Drawable banner = pm.getActivityBanner(app.component);
            if (banner == null) banner = pm.getApplicationBanner(app.packageName);
            if (banner != null) return new Art(banner, true);
            return new Art(pm.getActivityIcon(app.component), false);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "no art for " + app.packageName + ": " + e);
            return new Art(null, false);
        }
    }

    /** Frees the cached art (Home hidden for a while, or memory is short). */
    public void clear() {
        cache.evictAll();
    }

    public void shutdown() {
        executor.shutdownNow();
        cache.evictAll();
    }
}
