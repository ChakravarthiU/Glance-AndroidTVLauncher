package com.chakriau.tvhome.v2.eyes;

/** Settings › Eyes › Size: how big the eyes are drawn, against Theme 1's mockup size (Medium). Plain Java. */
public enum EyeSize {
    SMALL("Small", 0.75f),
    MEDIUM("Medium", 1f),
    LARGE("Large", 1.25f),
    EXTRA_LARGE("Extra large", 1.5f);

    public final String label;
    /** Multiplies the theme's eye radius. */
    public final float scale;

    EyeSize(String label, float scale) {
        this.label = label;
        this.scale = scale;
    }

    public static final EyeSize DEFAULT = MEDIUM;

    public EyeSize next() {
        EyeSize[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static EyeSize parse(String s) {
        for (EyeSize z : values()) if (z.name().equals(s)) return z;
        return DEFAULT;
    }
}
