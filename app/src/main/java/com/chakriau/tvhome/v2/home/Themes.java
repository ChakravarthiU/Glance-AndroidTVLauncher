package com.chakriau.tvhome.v2.home;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The themes the launcher has, and the parts of Home each one draws (Requirements.md section 7). A theme owns every
 * visible part of Home: whether it is drawn, where and how. What the parts contain (apps and their order, photos,
 * banner messages, festival days, Watch Next) is the owner's content, shared by every theme. Plain Java; the saved
 * choice is in {@link ThemeStore}, the per-theme on/off in {@link ThemeParts}.
 */
public final class Themes {

    /** The parts of Home a theme may draw (Requirements.md 7.1). */
    public enum Part {
        TOP_BAR(false, "Top bar", "Time, date, weather and the top buttons"),
        WALLPAPER(false, "Wallpaper", "Your photos behind Home"),
        APPS_BAR(false, "Apps bar", "Your favourite apps, where focus starts"),
        HOME_APPS(false, "Home apps", "The rest of your apps"),
        CONTINUE_WATCHING(true, "Continue watching", "What you were watching in each app"),
        BANNER(true, "Banner", "The banner of today's special day"),
        FESTIVE_LIGHTS(true, "Festive lights", "Lights along the top of Home"),
        EYES(true, "Eyes", "Two pairs of eyes that glance around and follow the remote"),
        SKY(true, "Sky", "The sun or moon crossing Home, with clouds, rain or snow from the weather");

        /** Optional parts have an on/off per theme; structural ones are drawn whenever the theme has them. */
        public final boolean optional;
        public final String label, note;

        Part(boolean optional, String label, String note) {
            this.optional = optional;
            this.label = label;
            this.note = note;
        }
    }

    /** Builds a theme's Home. */
    public interface Factory {
        HomeTheme create();
    }

    public static final class Theme {
        public final String id;
        public final String name;
        /** One line for the theme choice in Settings. */
        public final String description;
        public final Set<Part> parts;
        /** How many Apps bar apps this theme can show; it shows the first ones of the owner's bar list. */
        public final int appsBarMax;
        private final Factory factory;

        Theme(String id, String name, String description, Set<Part> parts, int appsBarMax, Factory factory) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.parts = Collections.unmodifiableSet(parts);
            this.appsBarMax = appsBarMax;
            this.factory = factory;
        }

        /** Whether this theme draws the part at all (before the owner's on/off). */
        public boolean has(Part p) {
            return parts.contains(p);
        }

        public HomeTheme create() {
            return factory.create();
        }
    }

    /**
     * Theme 1: the V1 look rebuilt ({@link Theme1Home}). Every part. A lambda, not {@code Theme1Home::new}, so plain
     * JVM tests can load this class without linking the Android view code.
     */
    @SuppressWarnings("Convert2MethodRef")
    public static final Theme THEME_1 = new Theme("theme1", "Theme 1",
            "Your photo behind a glass apps bar, with the sky, eyes, festive lights and a hanging banner",
            EnumSet.allOf(Part.class), 8, () -> new Theme1Home());

    public static final List<Theme> ALL = Collections.unmodifiableList(Arrays.asList(THEME_1));

    private Themes() {
    }

    /** The theme with this id, or Theme 1 when there is none (a removed theme, or nothing chosen yet). */
    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return THEME_1;
    }

    public static int indexOf(Theme theme) {
        return Math.max(0, ALL.indexOf(theme));
    }
}
