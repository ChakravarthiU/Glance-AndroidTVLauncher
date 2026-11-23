package com.chakriau.tvhome.v2.watching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * What the Continue watching row shows (Requirements.md 5.7), as plain Java so it can be unit tested: which Watch Next
 * items make the row, how a card describes one, and the Recently opened list used when Watch Next can't be read.
 */
public final class ContinueWatching {

    /** Settings › Continue watching › Items from each app: the choices, and the default (the newest only). */
    public static final int[] PER_APP_CHOICES = {1, 2, 3, 5};
    public static final int PER_APP_DEFAULT = 1;
    /** Settings › Continue watching › Items in the row: the choices, and the default. */
    public static final int[] TOTAL_CHOICES = {5, 10, 15, 20};
    public static final int TOTAL_DEFAULT = 10;

    /** The choice after {@code current} in {@code choices}, wrapping round (OK on a Settings row). */
    public static int nextChoice(int[] choices, int current) {
        for (int i = 0; i < choices.length; i++) if (choices[i] == current) return choices[(i + 1) % choices.length];
        return choices[0];
    }

    /** A saved choice, or the default when it is not one of the choices. */
    public static int validChoice(int[] choices, int saved, int fallback) {
        for (int c : choices) if (c == saved) return saved;
        return fallback;
    }

    /** {@code TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*}. */
    public static final int TYPE_CONTINUE = 0;
    public static final int TYPE_NEXT = 1;
    public static final int TYPE_NEW = 2;
    public static final int TYPE_WATCHLIST = 3;

    /** {@code TvContract.PreviewPrograms.ASPECT_RATIO_*} (the Watch Next poster's shape). */
    static final int ASPECT_16_9 = 0;
    static final int ASPECT_3_2 = 1;
    static final int ASPECT_4_3 = 2;

    /** Which Watch Next items count (Settings › Continue watching › Show). */
    public enum Mode {
        /** Things the owner is part-way through. Many apps also add next and new episodes at 0% (probe, 6 Nov 2026). */
        STARTED("Started only", "Only what you are part-way through"),
        /** Everything the apps put in Play Next: next and new episodes and watchlists too. */
        ALL("Everything in Play Next", "Also next and new episodes and watchlists the apps suggest");

        public final String label;
        public final String note;

        Mode(String label, String note) {
            this.label = label;
            this.note = note;
        }

        public Mode next() {
            return this == STARTED ? ALL : STARTED;
        }

        public static Mode parse(String s) {
            for (Mode m : values()) if (m.name().equals(s)) return m;
            return STARTED;
        }
    }

    /** One Watch Next item, as read from the TV's provider. */
    public static final class Item {
        public final long id;
        public final String packageName;
        public final String title;
        public final String season;
        public final String episode;
        public final String episodeTitle;
        public final long positionMs;
        public final long durationMs;
        public final long lastEngagedMs;
        public final int type;
        public final boolean browsable;
        public final String intentUri;
        public final String artUri;

        public Item(long id, String packageName, String title, String season, String episode, String episodeTitle,
                    long positionMs, long durationMs, long lastEngagedMs, int type, boolean browsable,
                    String intentUri, String artUri) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.season = season;
            this.episode = episode;
            this.episodeTitle = episodeTitle;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.lastEngagedMs = lastEngagedMs;
            this.type = type;
            this.browsable = browsable;
            this.intentUri = intentUri;
            this.artUri = artUri;
        }
    }

    private ContinueWatching() {
    }

    // ---- Picking --------------------------------------------------------------------------------------------------

    /** Part-way through: a "continue" item, or any item with a playback position. */
    public static boolean started(Item i) {
        return i.type == TYPE_CONTINUE || i.positionMs > 0;
    }

    /**
     * Items that may appear at all: shown in the TV's Play Next row (browsable), from an installed app other than
     * this launcher, with a title. Settings › Continue watching lists apps from these.
     */
    public static boolean usable(Item i, Set<String> installed, String ownPackage) {
        return i.browsable && i.packageName != null && !i.packageName.equals(ownPackage)
                && installed.contains(i.packageName) && i.title != null && !i.title.trim().isEmpty();
    }

    /**
     * The row: usable items the mode allows from apps not turned off, newest first, at most {@code perApp} from each
     * app and {@code max} in all.
     */
    public static List<Item> pick(Collection<Item> all, Set<String> installed, Set<String> blocked, String ownPackage,
                                  Mode mode, int perApp, int max) {
        List<Item> candidates = new ArrayList<>();
        for (Item i : all) {
            if (!usable(i, installed, ownPackage) || blocked.contains(i.packageName)) continue;
            if (mode == Mode.STARTED && !started(i)) continue;
            candidates.add(i);
        }
        candidates.sort(NEWEST_FIRST);
        List<Item> out = new ArrayList<>();
        Map<String, Integer> fromApp = new HashMap<>();
        for (Item i : candidates) {
            if (out.size() >= max) break;
            int n = fromApp.containsKey(i.packageName) ? fromApp.get(i.packageName) : 0;
            if (n >= perApp) continue;
            fromApp.put(i.packageName, n + 1);
            out.add(i);
        }
        return out;
    }

    /** How many usable items each app has, in order of each app's newest item (for Settings). */
    public static Map<String, Integer> countByApp(Collection<Item> all, Set<String> installed, String ownPackage) {
        List<Item> sorted = new ArrayList<>(all);
        sorted.sort(NEWEST_FIRST);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Item i : sorted) {
            if (!usable(i, installed, ownPackage)) continue;
            Integer n = counts.get(i.packageName);
            counts.put(i.packageName, n == null ? 1 : n + 1);
        }
        return counts;
    }

    private static final Comparator<Item> NEWEST_FIRST = (a, b) -> {
        int c = Long.compare(b.lastEngagedMs, a.lastEngagedMs);
        return c != 0 ? c : Long.compare(b.id, a.id);
    };

    // ---- How a card describes an item -----------------------------------------------------------------------------

    /** Played share, 0 to 1; 0 when the app gives no position or length. */
    public static float progress(Item i) {
        if (i.durationMs <= 0 || i.positionMs <= 0) return 0f;
        return Math.min(1f, i.positionMs / (float) i.durationMs);
    }

    /** "24 min left", "1 h 12 min left", "1 min left"; null when the app gives no position and length. */
    public static String timeLeft(long positionMs, long durationMs) {
        if (durationMs <= 0 || positionMs <= 0) return null;
        long left = Math.max(0, durationMs - positionMs);
        long min = Math.max(1, (left + 30_000) / 60_000);
        if (min < 60) return min + " min left";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h left" : h + " h " + m + " min left";
    }

    /**
     * The line under the title: "S2 E6 · 24 min left", "Ep 3 · Next episode", the episode's name when it has no
     * numbers, or just the time left.
     */
    public static String subtitle(Item i) {
        List<String> parts = new ArrayList<>();
        String s = clean(i.season), e = clean(i.episode);
        if (s != null && e != null) parts.add("S" + s + " E" + e);
        else if (e != null) parts.add("Ep " + e);
        else if (clean(i.episodeTitle) != null) parts.add(clean(i.episodeTitle));
        String left = timeLeft(i.positionMs, i.durationMs);
        if (left != null) parts.add(left);
        else if (i.type == TYPE_NEXT) parts.add("Next episode");
        else if (i.type == TYPE_NEW) parts.add("New episode");
        else if (i.type == TYPE_WATCHLIST) parts.add("Watchlist");
        return String.join(" · ", parts);
    }

    /** What OK does on a card: "Resume" if started, otherwise "Play". */
    public static String action(Item i) {
        return started(i) && progress(i) > 0 ? "Resume" : "Play";
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || t.equals("0") ? null : t;
    }

    /**
     * The picture for a card: a landscape poster (16:9, 3:2 or 4:3) first, then the thumbnail (a still from the
     * programme), then any poster. Null: the card shows the app's banner.
     */
    public static String art(String poster, int posterAspect, String thumbnail) {
        boolean landscape = posterAspect == ASPECT_16_9 || posterAspect == ASPECT_3_2 || posterAspect == ASPECT_4_3;
        if (has(poster) && landscape) return poster;
        if (has(thumbnail)) return thumbnail;
        return has(poster) ? poster : null;
    }

    private static boolean has(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ---- Recently opened (the fallback row) -----------------------------------------------------------------------

    /** One app opened from Home, and when. */
    public static final class Opened {
        public final String packageName;
        public final long at;

        public Opened(String packageName, long at) {
            this.packageName = packageName;
            this.at = at;
        }
    }

    /** Reads the saved list ("pkg@time,pkg@time", newest first); bad entries are skipped. */
    public static List<Opened> parseRecent(String saved) {
        List<Opened> out = new ArrayList<>();
        if (saved == null || saved.isEmpty()) return out;
        for (String part : saved.split(",")) {
            int at = part.lastIndexOf('@');
            if (at <= 0) continue;
            try {
                out.add(new Opened(part.substring(0, at), Long.parseLong(part.substring(at + 1))));
            } catch (NumberFormatException ignored) {
                // Skip it.
            }
        }
        return out;
    }

    public static String formatRecent(List<Opened> list) {
        StringBuilder b = new StringBuilder();
        for (Opened o : list) {
            if (b.length() > 0) b.append(',');
            b.append(o.packageName).append('@').append(o.at);
        }
        return b.toString();
    }

    /** Puts an app at the front of the list (once), keeping the newest {@code max}. */
    public static List<Opened> addRecent(List<Opened> list, String pkg, long now, int max) {
        List<Opened> out = new ArrayList<>();
        out.add(new Opened(pkg, now));
        for (Opened o : list) {
            if (out.size() >= max) break;
            if (!o.packageName.equals(pkg)) out.add(o);
        }
        return out;
    }

    /** The recently opened apps still installed and not turned off, newest first. */
    public static List<Opened> recentRow(List<Opened> list, Set<String> installed, Set<String> blocked, int max) {
        List<Opened> out = new ArrayList<>();
        for (Opened o : list) {
            if (out.size() >= max) break;
            if (installed.contains(o.packageName) && !blocked.contains(o.packageName)) out.add(o);
        }
        return out;
    }

    /** "Opened today", "Opened yesterday", "Opened 3 days ago" in the TV's time zone. */
    public static String opened(long at, long now, TimeZone zone) {
        long days = dayNumber(now, zone) - dayNumber(at, zone);
        if (days <= 0) return "Opened today";
        if (days == 1) return "Opened yesterday";
        return String.format(Locale.US, "Opened %d days ago", days);
    }

    private static long dayNumber(long ms, TimeZone zone) {
        return Math.floorDiv(ms + zone.getOffset(ms), 86_400_000L);
    }
}
