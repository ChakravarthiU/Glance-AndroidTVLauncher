package com.chakriau.tvhome.v2.eyes;

/**
 * How the eyes look and behave (Requirements.md 5.2): eye shape, how often and how slowly they blink, how often they
 * look around, how far and how fast the pupils move, and how often the two pairs look at each other. The eight
 * personalities are V1's ({@code GooglyEyesView}); more can be added here. Plain Java.
 */
public enum Personality {

    CURIOUS("Curious", "Round eyes that look around often and follow the remote",
            Style.ROUND, 360, 3500, 8000, 4500, 8500, 1300, 0.43f, 0.22f, 0.25f),
    PLAYFUL("Playful", "Quick and lively: darting glances and fast blinks",
            Style.ROUND, 360, 2200, 4500, 2200, 4600, 1300, 0.52f, 0.34f, 0.35f),
    SLEEPY("Sleepy", "Heavy lids, slow blinks, hardly bothered to look around",
            Style.ROUND, 560, 1800, 4000, 8000, 13000, 1900, 0.23f, 0.08f, 0.10f),
    ANGRY("Angry", "Red irises under a heavy scowl",
            Style.ANGRY, 380, 3500, 8000, 4500, 8500, 1300, 0.20f, 0.18f, 0.20f),
    MISCHIEVOUS("Mischievous", "Narrow, sly eyes with a raised brow; winks now and then",
            Style.ROUND, 360, 3500, 8000, 2200, 4600, 1000, 0.52f, 0.30f, 0.30f),
    SPARKLING("Sparkling", "Big violet eyes full of sparkle",
            Style.ROUND, 360, 3500, 8000, 4500, 8500, 1300, 0.43f, 0.20f, 0.25f),
    SHY("Shy", "Looks down and away, with a little blush",
            Style.ROUND, 360, 3500, 8000, 6500, 10500, 1900, 0.23f, 0.12f, 0.40f),
    DETERMINED("Determined", "Focused blue eyes under a firm brow",
            Style.ANGRY, 380, 3500, 8000, 6500, 10000, 1300, 0.20f, 0.20f, 0.10f);

    /** Round googly eyes, or V1's angular anime-style eyes. */
    public enum Style { ROUND, ANGRY }

    public final String label;
    public final String note;
    public final Style style;
    /** How long one blink takes. */
    public final int blinkMs;
    /** Time between blinks: from {@code blinkGapMin} to {@code blinkGapMax}. */
    public final int blinkGapMin, blinkGapMax;
    /** Time between idle glances. */
    public final int wanderGapMin, wanderGapMax;
    /** How long an idle glance holds. */
    public final int glanceMs;
    /** How far the pupil travels from the middle, as a share of the eye's radius. */
    public final float travel;
    /** Share of the remaining distance the pupil covers each frame (higher is quicker). */
    public final float speed;
    /** Chance that an idle glance is the two pairs looking at each other. */
    public final float lookAtEachOther;

    Personality(String label, String note, Style style, int blinkMs, int blinkGapMin, int blinkGapMax,
                int wanderGapMin, int wanderGapMax, int glanceMs, float travel, float speed, float lookAtEachOther) {
        this.label = label;
        this.note = note;
        this.style = style;
        this.blinkMs = blinkMs;
        this.blinkGapMin = blinkGapMin;
        this.blinkGapMax = blinkGapMax;
        this.wanderGapMin = wanderGapMin;
        this.wanderGapMax = wanderGapMax;
        this.glanceMs = glanceMs;
        this.travel = travel;
        this.speed = speed;
        this.lookAtEachOther = lookAtEachOther;
    }

    public static final Personality DEFAULT = CURIOUS;

    public Personality next() {
        Personality[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public Personality previous() {
        Personality[] all = values();
        return all[(ordinal() + all.length - 1) % all.length];
    }

    /** A saved name (V1 used lower case ids such as "sleepy"); anything unknown is the default. */
    public static Personality parse(String s) {
        if (s == null) return DEFAULT;
        for (Personality p : values()) if (p.name().equalsIgnoreCase(s.trim())) return p;
        return DEFAULT;
    }

    /** A time from {@code min} to {@code max}, with {@code r} from 0 to 1. */
    static int between(int min, int max, float r) {
        return min + Math.round((max - min) * Math.max(0f, Math.min(1f, r)));
    }
}
