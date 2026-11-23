package com.chakriau.tvhome.v2.home;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moving settings to where they live since build step 12 (Requirements.md 7, 8), so nothing the owner set is lost.
 * Before it, Eyes and Banner on/off sat in their own per-theme stores, Continue watching on/off and the top-button
 * style were global, and festival greetings were per theme. Now the on/off of every optional part and the top-button
 * style are in {@code parts_<themeId>}, and festival greetings are global. Plain Java over the stores' maps, so it is
 * tested on the JVM; {@link ThemeStore} applies it once.
 */
public final class ThemeMigration {

    /** Version after this migration, kept in the "themes" store. */
    public static final int VERSION = 1;

    /** The only theme before step 12. */
    public static final String OLD_THEME = "theme1";

    // Old places (store file, key)
    public static final String EYES_FILE = "eyes_" + OLD_THEME, BANNER_THEME_FILE = "banner_" + OLD_THEME,
            WATCHING_FILE = "continue_watching", TOP_BAR_FILE = "top_bar", BANNERS_FILE = "banners";
    public static final String ENABLED = "enabled", FESTIVAL_GREETING = "festival_greeting", STYLE = "style";

    // New places
    public static final String TOP_BUTTON_STYLE = "top_button_style";

    private ThemeMigration() {
    }

    /** The per-theme store's file name. */
    public static String partsFile(String themeId) {
        return "parts_" + themeId;
    }

    /** The key for an optional part's on/off in the per-theme store. */
    public static String onKey(Themes.Part p) {
        return "on." + p.name();
    }

    /**
     * What to write into Theme 1's parts store: each old value that was set, unless the new place already has one
     * (a value set since wins). Values not set before stay unset, so the defaults still apply.
     */
    public static Map<String, Object> partsFromOld(Map<String, ?> eyes, Map<String, ?> bannerTheme,
            Map<String, ?> watching, Map<String, ?> topBar, Map<String, ?> parts) {
        Map<String, Object> out = new LinkedHashMap<>();
        copy(eyes, ENABLED, Boolean.class, parts, onKey(Themes.Part.EYES), out);
        copy(bannerTheme, ENABLED, Boolean.class, parts, onKey(Themes.Part.BANNER), out);
        copy(watching, ENABLED, Boolean.class, parts, onKey(Themes.Part.CONTINUE_WATCHING), out);
        copy(topBar, STYLE, String.class, parts, TOP_BUTTON_STYLE, out);
        return out;
    }

    /** Festival greetings for the global banner store, or null when there's nothing to move. */
    public static Boolean greetingFromOld(Map<String, ?> bannerTheme, Map<String, ?> banners) {
        if (banners != null && banners.containsKey(FESTIVAL_GREETING)) return null;
        Object v = bannerTheme == null ? null : bannerTheme.get(FESTIVAL_GREETING);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    private static void copy(Map<String, ?> from, String fromKey, Class<?> type, Map<String, ?> to, String toKey,
            Map<String, Object> out) {
        if (to != null && to.containsKey(toKey)) return;
        Object v = from == null ? null : from.get(fromKey);
        if (type.isInstance(v)) out.put(toKey, v);
    }
}
