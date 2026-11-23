package com.chakriau.tvhome.v2.weather;

/**
 * The owner's choices for the clock and date (Settings › Time, date &amp; weather), and when weather is refreshed.
 * Plain Java, unit-tested.
 */
public final class TimeFormats {

    /** 12 or 24-hour clock; Automatic follows the TV's own setting. */
    public enum Clock {
        AUTO("Same as the TV"), H12("12-hour"), H24("24-hour");

        public final String label;

        Clock(String label) {
            this.label = label;
        }

        public Clock next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Whether to show 24-hour time, given the TV's own setting. */
        public boolean is24(boolean tvIs24) {
            return this == H24 || (this == AUTO && tvIs24);
        }

        public static Clock parse(String s) {
            for (Clock c : values()) if (c.name().equals(s)) return c;
            return AUTO;
        }
    }

    /** The top bar's clock face (Requirements.md 5.8, 14 Nov 2026). Digital is how it always was. */
    public enum Face {
        DIGITAL("Digital"), ANALOG("Analog"), ANALOG_SECONDS("Analog with seconds hand");

        public final String label;

        Face(String label) {
            this.label = label;
        }

        public boolean analog() {
            return this != DIGITAL;
        }

        public boolean seconds() {
            return this == ANALOG_SECONDS;
        }

        public static Face parse(String s) {
            for (Face f : values()) if (f.name().equals(s)) return f;
            return DIGITAL;
        }
    }

    /** Date formats; all show the weekday or day and month, never the year (Requirements.md 3, 5.8). */
    public enum DateStyle {
        SHORT("EEE, d MMM"), LONG("EEEE, d MMMM"), DAY_MONTH("d MMM");

        /** A {@link java.text.SimpleDateFormat} pattern. */
        public final String pattern;

        DateStyle(String pattern) {
            this.pattern = pattern;
        }

        public DateStyle next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static DateStyle parse(String s) {
            for (DateStyle d : values()) if (d.name().equals(s)) return d;
            return SHORT;
        }
    }

    /** Weather is fetched at most once an hour while Home is visible. */
    public static final long REFRESH_MS = 60 * 60_000L;
    /** An older reading is not shown (the top bar shows no weather rather than stale weather). */
    public static final long STALE_MS = 3 * 60 * 60_000L;

    /**
     * 1 Jan 2025 00:00 UTC. A TV clock before this hasn't been set yet: a box with no clock battery starts at 1970 or
     * its build date after a power cut, until it reaches the internet (or the owner sets the time).
     */
    public static final long CLOCK_SET_AFTER = 1_735_689_600_000L;

    private TimeFormats() {
    }

    /** Whether the TV's clock looks set; until it is, the top bar shows no time or date rather than a wrong one. */
    public static boolean clockSet(long now) {
        return now >= CLOCK_SET_AFTER;
    }

    public static boolean refreshDue(long fetchedAt, long now) {
        return fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= REFRESH_MS;
    }

    public static boolean showable(long fetchedAt, long now) {
        return fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < STALE_MS;
    }

    /** Milliseconds until the next hourly refresh (at least a minute, so a failing network isn't hammered). */
    public static long untilRefresh(long fetchedAt, long now) {
        if (refreshDue(fetchedAt, now)) return 60_000L;
        return Math.max(60_000L, fetchedAt + REFRESH_MS - now);
    }
}
