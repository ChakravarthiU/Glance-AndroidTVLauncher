package com.chakriau.tvhome.v2.festive;



/**
 * Banner designs and text (Requirements.md 5.4, 6.5): which banner a special day ({@link SpecialDays}) shows, and how
 * its two lines are cleaned and fitted. Plain Java.
 */
public final class Banners {

    public static final int LINE1_MAX = 40, LINE2_MAX = 60;

    /** The banner designs that ship with the app ({@code assets/banners/<id>.json}). */
    public enum Design {
        DIWALI("diwali", "Diwali"), SIMPLE("simple", "Simple"), BIRTHDAY("birthday", "Birthday"),
        CHRISTMAS("christmas", "Christmas");

        public final String id;
        public final String label;

        Design(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String asset() {
            return "banners/" + id + ".json";
        }

        public static Design parse(String s) {
            for (Design d : values()) if (d.id.equals(s)) return d;
            return null;
        }
    }

    /** What the banner shows. */
    public static final class Shown {
        public final Design design;
        public final String line1, line2;
        /** The special day it is for, or null for a preview. */
        public final String dayId;

        public Shown(Design design, String line1, String line2, String dayId) {
            this.design = design;
            this.line1 = line1;
            this.line2 = line2;
            this.dayId = dayId;
        }
    }

    private Banners() {
    }

    /** The design a festival suggestion starts with. */
    public static Design designFor(String festivalId) {
        if ("diwali".equals(festivalId)) return Design.DIWALI;
        if ("christmas".equals(festivalId)) return Design.CHRISTMAS;
        return Design.SIMPLE;
    }

    // ---- Fitting the text -----------------------------------------------------------------------------------------

    /** How much to shrink a line so it fits the template's safe zone (600 px wide); never below 40%. */
    public static float fitScale(float textWidth, float maxWidth) {
        if (textWidth <= maxWidth || textWidth <= 0) return 1f;
        return Math.max(0.4f, maxWidth / textWidth);
    }

    /** One line, trimmed, at most {@code max} characters. */
    public static String cleanLine(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    /** Ids are the time saved in base 36 plus a random part; tabs and newlines can never appear. */
    public static String newId(long now, int random) {
        return Long.toString(now, 36) + Integer.toString(Math.abs(random % 1296), 36);
    }
}
