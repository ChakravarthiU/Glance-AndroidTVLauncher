package com.chakriau.tvhome.v2.safe;

import java.util.Arrays;

/**
 * When the launcher goes into safe mode (Requirements.md 5.12), plain Java so it is unit-tested: three crashes within
 * ten minutes. Android starts Home again after every crash, so a launcher that crashes on start would otherwise
 * leave the TV with no usable Home; in safe mode Home is a plain list of apps that uses none of the parts that can
 * fail (no photos, banner, sky, eyes, weather or Watch Next). It ends when the owner chooses "Try normal Home again"
 * (on the TV or the phone page), or by itself when a newer version of the launcher is installed, which may have the
 * fix.
 */
public final class SafeMode {

    public static final int CRASHES = 3;
    public static final long WINDOW_MS = 10 * 60_000L;
    /** How many crash times are kept. */
    static final int KEEP = 5;
    /** How much of a crash report is kept: enough lines to see where it happened. */
    static final int MAX_LINES = 40, MAX_CHARS = 6000;

    private SafeMode() {
    }

    /** The crash times with {@code now} added, newest last, at most {@link #KEEP}. */
    public static long[] add(long[] times, long now) {
        long[] all = Arrays.copyOf(times, times.length + 1);
        all[times.length] = now;
        return all.length <= KEEP ? all : Arrays.copyOfRange(all, all.length - KEEP, all.length);
    }

    /** Whether these crashes mean safe mode: {@link #CRASHES} or more within {@link #WINDOW_MS} of {@code now}. */
    public static boolean shouldEnter(long[] times, long now) {
        int recent = 0;
        for (long t : times) if (t <= now && now - t <= WINDOW_MS) recent++;
        return recent >= CRASHES;
    }

    /** A newer launcher than the one that went into safe mode: try normal Home again. */
    public static boolean newerVersion(long safeVersion, long currentVersion) {
        return currentVersion > safeVersion;
    }

    public static String format(long[] times) {
        StringBuilder b = new StringBuilder();
        for (long t : times) {
            if (b.length() > 0) b.append(',');
            b.append(t);
        }
        return b.toString();
    }

    public static long[] parse(String s) {
        if (s == null || s.isEmpty()) return new long[0];
        String[] parts = s.split(",");
        long[] out = new long[parts.length];
        int n = 0;
        for (String p : parts) {
            try {
                out[n++] = Long.parseLong(p.trim());
            } catch (NumberFormatException e) {
                n--;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** A crash report cut to its first {@link #MAX_LINES} lines and {@link #MAX_CHARS} characters. */
    public static String trim(String report) {
        if (report == null) return "";
        String[] lines = report.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            if (i > 0) b.append('\n');
            b.append(lines[i]);
        }
        if (lines.length > MAX_LINES) b.append("\n… ").append(lines.length - MAX_LINES).append(" more lines");
        return b.length() <= MAX_CHARS ? b.toString() : b.substring(0, MAX_CHARS) + "…";
    }
}
