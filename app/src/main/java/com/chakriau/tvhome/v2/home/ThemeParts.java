package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.content.SharedPreferences;

import com.chakriau.tvhome.v2.festive.Festivals;
import com.chakriau.tvhome.v2.festive.LightsStore;
import com.chakriau.tvhome.v2.system.TopBar;

/**
 * One theme's choices for its parts (Requirements.md 7.1, Settings › Theme): the on/off of each optional part and the
 * top-button style, saved per theme. Festive lights' on/off is its mode ({@link LightsStore}, also per theme).
 */
public final class ThemeParts {

    private final Themes.Theme theme;
    private final SharedPreferences prefs;
    private final LightsStore lights;

    public ThemeParts(Context context, Themes.Theme theme) {
        ThemeStore.migrate(context);
        this.theme = theme;
        this.prefs = context.getApplicationContext().getSharedPreferences(ThemeMigration.partsFile(theme.id),
                Context.MODE_PRIVATE);
        this.lights = new LightsStore(context, theme.id);
    }

    /** The active theme's parts. */
    public static ThemeParts active(Context context) {
        return new ThemeParts(context, ThemeStore.active(context));
    }

    public Themes.Theme theme() {
        return theme;
    }

    /** Whether the theme draws the part at all. */
    public boolean has(Themes.Part p) {
        return theme.has(p);
    }

    /** The owner's on/off. Structural parts are always on; festive lights are on unless their mode is Off. */
    public boolean on(Themes.Part p) {
        if (!p.optional) return true;
        if (p == Themes.Part.FESTIVE_LIGHTS) return lights.mode() != Festivals.LightsMode.OFF;
        return prefs.getBoolean(ThemeMigration.onKey(p), true);
    }

    /** For Continue watching, Banner and Eyes; festive lights are set by their mode. */
    public void setOn(Themes.Part p, boolean on) {
        if (!p.optional || p == Themes.Part.FESTIVE_LIGHTS) throw new IllegalArgumentException(p.name());
        prefs.edit().putBoolean(ThemeMigration.onKey(p), on).apply();
    }

    /** Drawn on Home: the theme has it and the owner hasn't turned it off. */
    public boolean shows(Themes.Part p) {
        return has(p) && on(p);
    }

    public LightsStore lights() {
        return lights;
    }

    public TopBar.Style topButtonStyle() {
        return TopBar.Style.parse(prefs.getString(ThemeMigration.TOP_BUTTON_STYLE, null));
    }

    public void setTopButtonStyle(TopBar.Style s) {
        prefs.edit().putString(ThemeMigration.TOP_BUTTON_STYLE, s.name()).apply();
    }
}
