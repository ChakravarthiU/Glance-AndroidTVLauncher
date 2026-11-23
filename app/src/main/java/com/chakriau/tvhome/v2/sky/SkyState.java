package com.chakriau.tvhome.v2.sky;

import com.chakriau.tvhome.v2.weather.WeatherCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * What the sky shows at one moment (Requirements.md 5.10): the sun or moon and where, the moon's phase, and the
 * weather's effects. Made from the real weather and place, or from a test set on the phone page's Sky tab. Plain
 * Java.
 */
public final class SkyState {

    /** How long a test from the phone page lasts before Home goes back to the real sky. */
    public static final long TEST_MS = 15 * 60_000L;

    /** A test from the phone page: a kind of weather (null: the real one) and a time of day (-1: now). */
    public static final class Test {
        public final WeatherCode.Kind kind;
        /** Minutes after midnight, 0 to 1439, or -1 for the real time. */
        public final int minuteOfDay;
        /** When the test ends (epoch milliseconds). */
        public final long until;

        public Test(WeatherCode.Kind kind, int minuteOfDay, long until) {
            this.kind = kind;
            this.minuteOfDay = minuteOfDay < 0 || minuteOfDay > 1439 ? -1 : minuteOfDay;
            this.until = until;
        }

        public boolean active(long now) {
            return now < until;
        }

        /** Saved as "KIND|minute|until" ("-" for the real weather). */
        public String serialize() {
            return (kind == null ? "-" : kind.name()) + "|" + minuteOfDay + "|" + until;
        }

        /** The saved test, or null if there is none or it can't be read. */
        public static Test parse(String s) {
            if (s == null) return null;
            String[] p = s.split("\\|");
            if (p.length != 3) return null;
            try {
                WeatherCode.Kind k = p[0].equals("-") ? null : WeatherCode.Kind.valueOf(p[0]);
                return new Test(k, Integer.parseInt(p[1]), Long.parseLong(p[2]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public final SkyMath.Position position;
    /** The moon's phase (0 new, 0.5 full). */
    public final float moonPhase;
    /** The weather shown (null: unknown, drawn as clear). */
    public final WeatherCode.Kind kind;
    public final SkyMath.Effects effects;
    /** True while a phone-page test is showing instead of the real sky. */
    public final boolean testing;

    private SkyState(SkyMath.Position position, float moonPhase, WeatherCode.Kind kind, boolean testing) {
        this.position = position;
        this.moonPhase = moonPhase;
        this.kind = kind;
        this.effects = SkyMath.effects(kind);
        this.testing = testing;
    }

    /**
     * The sky at {@code now}. {@code place} is {latitude, longitude} or null; {@code realKind} is the weather on show
     * (null when off or nothing recent); {@code test} is the phone page's test, used while it is active.
     */
    public static SkyState at(long now, ZoneId zone, double[] place, WeatherCode.Kind realKind, Test test) {
        boolean testing = test != null && test.active(now);
        long when = now;
        WeatherCode.Kind kind = realKind;
        if (testing) {
            if (test.kind != null) kind = test.kind;
            if (test.minuteOfDay >= 0) when = timeToday(now, zone, test.minuteOfDay);
        }
        return new SkyState(SkyMath.position(when, zone, place), SkyMath.moonPhase(when), kind, testing);
    }

    /** Today's date at {@code minuteOfDay} in {@code zone}, as epoch milliseconds. */
    static long timeToday(long now, ZoneId zone, int minuteOfDay) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return today.atStartOfDay(zone).plusMinutes(minuteOfDay).toInstant().toEpochMilli();
    }

    /** What makes two states look different, so an unchanged sky isn't rebuilt (position rounded to 0.1%). */
    public String key() {
        return position.body + "|" + Math.round(position.progress * 1000) + "|" + kind + "|"
                + Math.round(moonPhase * 100);
    }
}
