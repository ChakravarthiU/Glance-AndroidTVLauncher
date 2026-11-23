package com.chakriau.tvhome.v2.watching;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.tv.TvContract.WatchNextPrograms;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.Themes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the Continue watching row for Home (Requirements.md 5.7) and keeps it current while Home is visible: Watch
 * Next when the TV lets the launcher read it, otherwise (or while it has nothing) the apps last opened from Home.
 * Shared by every theme; a theme only decides how the cards look.
 */
public final class WatchingRow {

    private static final String TAG = "TvHomeWatching";

    public enum Kind {
        /** Turned off in Settings, or nothing to show: the section is hidden. */
        NONE,
        /** Items from Watch Next. */
        CONTINUE_WATCHING,
        /** The fallback: apps last opened from Home. */
        RECENTLY_OPENED
    }

    /** One card: an app and, for Watch Next, the item to resume. */
    public static final class Card {
        public final AppInfo app;
        /** Null on a Recently opened card. */
        public final ContinueWatching.Item item;
        /** When a Recently opened card's app was opened. */
        public final long openedAt;

        Card(AppInfo app, ContinueWatching.Item item, long openedAt) {
            this.app = app;
            this.item = item;
            this.openedAt = openedAt;
        }

        /** Identifies what the card shows, so an unchanged row is not rebuilt. */
        public String key() {
            if (item == null) return app.packageName + "@" + openedAt;
            return app.packageName + "#" + item.id + ":" + item.positionMs + ":" + item.title + ":"
                    + item.artUri + ":" + item.type;
        }
    }

    public static final class Row {
        public final Kind kind;
        public final List<Card> cards;
        /** Settings › Continue watching limits, for the caption under the row. */
        public final int perApp, total;

        Row(Kind kind, List<Card> cards, int perApp, int total) {
            this.kind = kind;
            this.cards = Collections.unmodifiableList(cards);
            this.perApp = perApp;
            this.total = total;
        }

        public String key() {
            StringBuilder b = new StringBuilder(kind.name()).append(perApp).append('/').append(total);
            for (Card c : cards) b.append('|').append(c.key());
            return b.toString();
        }
    }

    public interface Listener {
        /** On the main thread. */
        void onRow(Row row);
    }

    private final Context context;
    private final AppsModel apps;
    private final WatchingStore store;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable reload = this::reload;
    private ContentObserver observer;
    private boolean running;

    public WatchingRow(Context context, AppsModel apps, Listener listener) {
        this.context = context.getApplicationContext();
        this.apps = apps;
        this.store = new WatchingStore(context);
        this.listener = listener;
    }

    /** Home became visible: read the row now and follow Watch Next changes until {@link #stop()}. */
    public void start() {
        running = true;
        if (observer == null) {
            observer = new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    // Apps write Watch Next in bursts: one read a couple of seconds after the last change.
                    main.removeCallbacks(reload);
                    main.postDelayed(reload, 2000);
                }
            };
            try {
                context.getContentResolver().registerContentObserver(WatchNextPrograms.CONTENT_URI, true, observer);
            } catch (RuntimeException e) {
                Log.w(TAG, "can't follow Watch Next changes: " + e);
            }
        }
        reload();
    }

    public void stop() {
        running = false;
        main.removeCallbacks(reload);
        if (observer != null) {
            ContentResolver r = context.getContentResolver();
            try {
                r.unregisterContentObserver(observer);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            observer = null;
        }
    }

    public void shutdown() {
        stop();
        worker.shutdownNow();
    }

    /** Reads the row again (also after the permission dialog, or when the apps change). */
    public void reload() {
        main.removeCallbacks(reload);
        if (!running) return;
        worker.execute(() -> {
            Row row;
            try {
                row = build();
            } catch (RuntimeException e) {
                Log.w(TAG, "building Continue watching failed", e);
                return;
            }
            main.post(() -> {
                if (running) listener.onRow(row);
            });
        });
    }

    private Row build() {
        if (!ThemeParts.active(context).shows(Themes.Part.CONTINUE_WATCHING)) return new Row(Kind.NONE, new ArrayList<>(), store.perApp(), store.total());
        Map<String, AppInfo> installed = installedApps(apps.current());
        Set<String> blocked = store.blocked();

        WatchNext.Result wn = WatchNext.read(context);
        if (wn.status == WatchNext.Status.READABLE) {
            List<ContinueWatching.Item> picked = ContinueWatching.pick(wn.items, installed.keySet(), blocked,
                    context.getPackageName(), store.mode(), store.perApp(), store.total());
            if (!picked.isEmpty()) {
                List<Card> cards = new ArrayList<>();
                for (ContinueWatching.Item i : picked) cards.add(new Card(installed.get(i.packageName), i, 0));
                return new Row(Kind.CONTINUE_WATCHING, cards, store.perApp(), store.total());
            }
        }
        // Watch Next can't be read, or has nothing to show: the apps last opened from Home.
        List<Card> cards = new ArrayList<>();
        for (ContinueWatching.Opened o : ContinueWatching.recentRow(store.recent(), installed.keySet(), blocked,
                store.total())) {
            cards.add(new Card(installed.get(o.packageName), null, o.at));
        }
        return new Row(cards.isEmpty() ? Kind.NONE : Kind.RECENTLY_OPENED, cards, store.perApp(),
                store.total());
    }

    /** Every launchable app, whatever its state (Apps bar, Home or Hidden), by package. */
    public static Map<String, AppInfo> installedApps(AppsModel.Snapshot s) {
        Map<String, AppInfo> map = new HashMap<>();
        for (AppInfo a : s.hidden) map.put(a.packageName, a);
        for (AppInfo a : s.home) map.put(a.packageName, a);
        for (AppInfo a : s.appsBar) map.put(a.packageName, a);
        return map;
    }
}
