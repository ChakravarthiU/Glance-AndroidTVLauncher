package com.chakriau.tvhome.v2.wallpapers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Which photo Home shows and when it changes (Requirements.md 5.1). One ticked photo stays fixed; several rotate every
 * interval, in turn or shuffled. Plain Java, unit-tested.
 */
public final class Rotation {

    /** The rotation intervals offered in Settings, in minutes. */
    public static final int[] INTERVALS = {5, 15, 30, 60};
    public static final int DEFAULT_INTERVAL = 15;

    private Rotation() {
    }

    /** The next interval in Settings' list, wrapping around (OK on the row steps through them). */
    public static int nextInterval(int minutes) {
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == minutes) return INTERVALS[(i + 1) % INTERVALS.length];
        return DEFAULT_INTERVAL;
    }

    /** A saved interval, or the default if it isn't one of the offered values. */
    public static int validInterval(int minutes) {
        for (int m : INTERVALS) if (m == minutes) return m;
        return DEFAULT_INTERVAL;
    }

    /**
     * The photo to show after {@code current}.
     *
     * @param ticked  the ticked photos, in the order they are listed
     * @param current the photo on screen now, or null
     * @return null when nothing is ticked
     */
    public static String next(List<String> ticked, String current, boolean shuffle, Random random) {
        if (ticked.isEmpty()) return null;
        if (ticked.size() == 1) return ticked.get(0);
        if (shuffle) {
            List<String> others = new ArrayList<>(ticked);
            others.remove(current);   // never the same photo twice in a row
            return others.get(random.nextInt(others.size()));
        }
        int i = current == null ? -1 : ticked.indexOf(current);
        return ticked.get((i + 1) % ticked.size());   // a photo no longer ticked starts again at the first
    }

    /**
     * The photo Home should show now: keeps the current one while it is still ticked and its time isn't up, otherwise
     * moves on. With one ticked photo that photo always shows.
     */
    public static String current(List<String> ticked, String current, long shownAt, long now, int intervalMinutes,
                                 boolean shuffle, Random random) {
        if (ticked.isEmpty()) return null;
        if (current == null || !ticked.contains(current)) return next(ticked, current, shuffle, random);
        if (ticked.size() > 1 && due(shownAt, now, intervalMinutes)) return next(ticked, current, shuffle, random);
        return current;
    }

    /** Whether a photo shown at {@code shownAt} has been up for the interval (a clock set back counts as due). */
    public static boolean due(long shownAt, long now, int intervalMinutes) {
        long interval = intervalMinutes * 60_000L;
        return shownAt <= 0 || now < shownAt || now - shownAt >= interval;
    }

    /** Milliseconds until the photo shown at {@code shownAt} should change (0 if it is due now). */
    public static long untilDue(long shownAt, long now, int intervalMinutes) {
        if (due(shownAt, now, intervalMinutes)) return 0;
        return shownAt + intervalMinutes * 60_000L - now;
    }
}
