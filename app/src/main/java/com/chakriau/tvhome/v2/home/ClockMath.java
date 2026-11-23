package com.chakriau.tvhome.v2.home;

/**
 * The maths behind the top bar's clock faces (Requirements.md 5.8): which characters roll when the digital time
 * changes, the roll's timing and easing, and the analog hands' angles. Plain Java, unit-tested in
 * {@code ClockMathTest}; {@link RollingText} and {@link AnalogClock} only draw what this says.
 */
public final class ClockMath {

    /** How long one character's roll takes. */
    public static final long ROLL_MS = 520;
    /** Each changing character starts this much after the one to its right, like an odometer (11:59 → 12:00). */
    public static final long STAGGER_MS = 70;
    /** How long the analog hands take to move to the new minute. */
    public static final long HANDS_MS = 600;
    /** How long the seconds hand takes to tick to the next second. */
    public static final long TICK_MS = 180;
    /** The little overshoot at the end of a roll or a hand's move (0 = none). */
    static final float OVERSHOOT = 1.3f;

    private ClockMath() {
    }

    // ---- Digital: rolling characters ------------------------------------------------------------------------------

    /**
     * Which characters change from {@code before} to {@code after}, compared from the right so "9:59" → "10:00"
     * lines up the minutes. The result has one entry per position of the longer string; a position only one string
     * has counts as changed.
     */
    public static boolean[] changed(String before, String after) {
        int n = Math.max(before.length(), after.length());
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            int b = i - (n - before.length());
            int a = i - (n - after.length());
            char cb = b >= 0 ? before.charAt(b) : 0;
            char ca = a >= 0 ? after.charAt(a) : 0;
            out[i] = cb != ca;
        }
        return out;
    }

    /**
     * When each changed position starts rolling, in ms after the change: the rightmost changed character first, each
     * one further left {@link #STAGGER_MS} later. Unchanged positions get 0.
     */
    public static long[] delays(boolean[] changed) {
        long[] out = new long[changed.length];
        int k = 0;
        for (int i = changed.length - 1; i >= 0; i--) {
            if (changed[i]) out[i] = (k++) * STAGGER_MS;
        }
        return out;
    }

    /** The whole roll's length for these changes (the last character's start plus one roll). */
    public static long rollLength(boolean[] changed) {
        int count = 0;
        for (boolean c : changed) if (c) count++;
        return count == 0 ? 0 : (count - 1) * STAGGER_MS + ROLL_MS;
    }

    /** How far one character has rolled (0 → about 1, with a small overshoot) at {@code elapsed} ms. */
    public static float rollAt(long elapsed, long delay) {
        float t = (elapsed - delay) / (float) ROLL_MS;
        return easeOutBack(clamp(t));
    }

    // ---- Analog: hands --------------------------------------------------------------------------------------------

    /** Hour hand, in degrees clockwise from 12. */
    public static float hourAngle(int hour, int minute, int second) {
        return (hour % 12) * 30f + minute * 0.5f + second / 120f;
    }

    /** Minute hand, in degrees clockwise from 12. Pass second 0 for a hand that moves once a minute. */
    public static float minuteAngle(int minute, int second) {
        return minute * 6f + second * 0.1f;
    }

    /** Seconds hand, in degrees clockwise from 12. */
    public static float secondAngle(int second) {
        return second * 6f;
    }

    /**
     * The turn from one angle to another, in degrees, the short way round: 354 → 0 is +6, not −354. A clock set
     * back turns back.
     */
    public static float sweep(float from, float to) {
        float d = ((to - from) % 360f + 360f) % 360f;
        return d > 180f ? d - 360f : d;
    }

    /** {@code from} turned {@code fraction} of the short way to {@code to}, kept in 0–360. */
    public static float turn(float from, float to, float fraction) {
        float a = from + sweep(from, to) * fraction;
        return (a % 360f + 360f) % 360f;
    }

    // ---- Easing ---------------------------------------------------------------------------------------------------

    /** Fast start, settling with a slight overshoot: a mechanical "click" into place. 0 → 0, 1 → 1. */
    public static float easeOutBack(float t) {
        float c = OVERSHOOT;
        float u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    public static float clamp(float t) {
        return t < 0f ? 0f : t > 1f ? 1f : t;
    }
}
