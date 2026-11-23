package com.chakriau.tvhome.v2.watching;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract.WatchNextPrograms;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads Android's Watch Next programs (the stock launcher's Play Next row), the source of Continue watching
 * (Requirements.md 5.7). The build order step 1 probe found the Sony TV gives every app's rows to a launcher holding
 * {@code READ_TV_LISTINGS}. Run off the main thread.
 */
public final class WatchNext {

    private static final String TAG = "TvHomeWatching";

    /** Asked for at first run, or granted with {@code adb shell pm grant} (README). */
    public static final String PERMISSION = "android.permission.READ_TV_LISTINGS";

    public enum Status {
        /** Other apps' rows come back (or the row is really empty, with the permission). */
        READABLE,
        /** The launcher may not read Play Next yet. */
        NO_PERMISSION,
        /** The TV has no TV provider, or it refused the query. */
        UNAVAILABLE
    }

    public static final class Result {
        public final Status status;
        public final List<ContinueWatching.Item> items;

        Result(Status status, List<ContinueWatching.Item> items) {
            this.status = status;
            this.items = items;
        }
    }

    private WatchNext() {
    }

    public static boolean allowed(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static Result read(Context context) {
        boolean allowed = allowed(context);
        List<ContinueWatching.Item> items = new ArrayList<>();
        String me = context.getPackageName();
        boolean others = false;
        try (Cursor c = context.getContentResolver().query(WatchNextPrograms.CONTENT_URI, null, null, null,
                WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            if (c == null) return new Result(allowed ? Status.UNAVAILABLE : Status.NO_PERMISSION, items);
            Columns col = new Columns(c);
            while (c.moveToNext()) {
                ContinueWatching.Item item = col.read(c);
                if (item.packageName != null && !item.packageName.equals(me)) others = true;
                items.add(item);
            }
        } catch (RuntimeException e) {
            // SecurityException without the permission; other errors on TVs with a broken provider.
            Log.w(TAG, "reading Watch Next failed: " + e);
            return new Result(allowed ? Status.UNAVAILABLE : Status.NO_PERMISSION, new ArrayList<>());
        }
        // Without the permission a TV may quietly return only the launcher's own rows: that is not "readable".
        Status status = allowed || others ? Status.READABLE : Status.NO_PERMISSION;
        return new Result(status, items);
    }

    /** Column positions, looked up once per query; a column a TV lacks reads as empty. */
    private static final class Columns {
        final int id, pkg, title, season, episode, episodeTitle, position, duration, engaged, type, browsable,
                intent, poster, posterAspect, thumbnail;

        Columns(Cursor c) {
            id = c.getColumnIndex(WatchNextPrograms._ID);
            pkg = c.getColumnIndex(WatchNextPrograms.COLUMN_PACKAGE_NAME);
            title = c.getColumnIndex(WatchNextPrograms.COLUMN_TITLE);
            season = c.getColumnIndex(WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
            episode = c.getColumnIndex(WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
            episodeTitle = c.getColumnIndex(WatchNextPrograms.COLUMN_EPISODE_TITLE);
            position = c.getColumnIndex(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
            duration = c.getColumnIndex(WatchNextPrograms.COLUMN_DURATION_MILLIS);
            engaged = c.getColumnIndex(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
            type = c.getColumnIndex(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE);
            browsable = c.getColumnIndex(WatchNextPrograms.COLUMN_BROWSABLE);
            intent = c.getColumnIndex(WatchNextPrograms.COLUMN_INTENT_URI);
            poster = c.getColumnIndex(WatchNextPrograms.COLUMN_POSTER_ART_URI);
            posterAspect = c.getColumnIndex(WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO);
            thumbnail = c.getColumnIndex(WatchNextPrograms.COLUMN_THUMBNAIL_URI);
        }

        ContinueWatching.Item read(Cursor c) {
            return new ContinueWatching.Item(
                    number(c, id, -1),
                    text(c, pkg),
                    text(c, title),
                    text(c, season),
                    text(c, episode),
                    text(c, episodeTitle),
                    number(c, position, 0),
                    number(c, duration, 0),
                    number(c, engaged, 0),
                    (int) number(c, type, ContinueWatching.TYPE_CONTINUE),
                    number(c, browsable, 1) != 0,
                    text(c, intent),
                    ContinueWatching.art(text(c, poster), (int) number(c, posterAspect, -1), text(c, thumbnail)));
        }

        private static String text(Cursor c, int i) {
            return i < 0 || c.isNull(i) ? null : c.getString(i);
        }

        private static long number(Cursor c, int i, long none) {
            return i < 0 || c.isNull(i) ? none : c.getLong(i);
        }
    }
}
