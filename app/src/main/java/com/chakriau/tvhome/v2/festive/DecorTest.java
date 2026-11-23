package com.chakriau.tvhome.v2.festive;

/**
 * A test of the banner and the festive lights from the phone page's Test tab (Requirements.md 5.14): Home shows them
 * as chosen instead of what today brings, until {@link #until}. Goes with the sky's test ({@code SkyState.Test}),
 * started and stopped together. Plain Java.
 */
public final class DecorTest {

    /** The banner: today's real one, none, or a design with lines. */
    public enum BannerChoice { REAL, NONE, DESIGN }

    /** The lights: as Settings and today say, or forced on or off. */
    public enum LightsChoice { REAL, ON, OFF }

    /** Line one when a design is tested without any text. */
    public static final String SAMPLE_LINE1 = "Happy Birthday!";

    public final BannerChoice banner;
    /** The design tested; only with {@link BannerChoice#DESIGN}. */
    public final Banners.Design design;
    public final String line1, line2;
    public final LightsChoice lights;
    /** When the test ends (epoch milliseconds). */
    public final long until;

    public DecorTest(BannerChoice banner, Banners.Design design, String line1, String line2, LightsChoice lights,
                     long until) {
        boolean withDesign = banner == BannerChoice.DESIGN && design != null;
        this.banner = banner == null || (banner == BannerChoice.DESIGN && design == null) ? BannerChoice.REAL : banner;
        this.design = withDesign ? design : null;
        String l1 = withDesign ? Banners.cleanLine(line1, Banners.LINE1_MAX) : "";
        this.line1 = withDesign && l1.isEmpty() ? SAMPLE_LINE1 : l1;
        this.line2 = withDesign ? Banners.cleanLine(line2, Banners.LINE2_MAX) : "";
        this.lights = lights == null ? LightsChoice.REAL : lights;
        this.until = until;
    }

    public boolean active(long now) {
        return now < until;
    }

    /** True if the test changes anything at all (both on "real" changes nothing). */
    public boolean changesAnything() {
        return banner != BannerChoice.REAL || lights != LightsChoice.REAL;
    }

    /** The banner to show, given today's real one (null: none). */
    public Banners.Shown banner(Banners.Shown real) {
        switch (banner) {
            case NONE:
                return null;
            case DESIGN:
                return new Banners.Shown(design, line1, line2, null);
            default:
                return real;
        }
    }

    /** Whether the lights show, given what Settings and today say. */
    public boolean lightsOn(boolean real) {
        return lights == LightsChoice.ON || (lights == LightsChoice.REAL && real);
    }

    /** The banner on Home: today's, or the test's while {@code test} is active. */
    public static Banners.Shown banner(DecorTest test, long now, Banners.Shown real) {
        return test != null && test.active(now) ? test.banner(real) : real;
    }

    /** The lights on Home: Settings and today's, or the test's while {@code test} is active. */
    public static boolean lightsOn(DecorTest test, long now, boolean real) {
        return test != null && test.active(now) ? test.lightsOn(real) : real;
    }

    /** A choice by its saved name, or {@code fallback}. */
    public static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
