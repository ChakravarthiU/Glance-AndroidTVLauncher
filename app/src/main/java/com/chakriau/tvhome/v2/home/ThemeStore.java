package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Which theme is active (global: Settings › Theme), and the one-time move of older settings to their step-12 places
 * ({@link ThemeMigration}). Everything that reads the theme comes through here, so the move always runs first.
 */
public final class ThemeStore {

    private static final String FILE = "themes", ACTIVE = "active", VERSION = "version";
    private static volatile boolean migrated;

    private ThemeStore() {
    }

    public static Themes.Theme active(Context context) {
        migrate(context);
        return Themes.byId(prefs(context).getString(ACTIVE, null));
    }

    public static void setActive(Context context, Themes.Theme theme) {
        prefs(context).edit().putString(ACTIVE, theme.id).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static SharedPreferences file(Context app, String name) {
        return app.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /** Runs once per install (cheap after the first time: a flag in memory, then one int). */
    static void migrate(Context context) {
        if (migrated) return;
        synchronized (ThemeStore.class) {
            if (migrated) return;
            Context app = context.getApplicationContext();
            SharedPreferences meta = prefs(app);
            if (meta.getInt(VERSION, 0) < ThemeMigration.VERSION) {
                SharedPreferences eyes = file(app, ThemeMigration.EYES_FILE);
                SharedPreferences bannerTheme = file(app, ThemeMigration.BANNER_THEME_FILE);
                SharedPreferences watching = file(app, ThemeMigration.WATCHING_FILE);
                SharedPreferences topBar = file(app, ThemeMigration.TOP_BAR_FILE);
                SharedPreferences banners = file(app, ThemeMigration.BANNERS_FILE);
                SharedPreferences parts = file(app, ThemeMigration.partsFile(ThemeMigration.OLD_THEME));

                SharedPreferences.Editor p = parts.edit();
                for (Map.Entry<String, Object> e : ThemeMigration.partsFromOld(eyes.getAll(), bannerTheme.getAll(),
                        watching.getAll(), topBar.getAll(), parts.getAll()).entrySet()) {
                    if (e.getValue() instanceof Boolean) p.putBoolean(e.getKey(), (Boolean) e.getValue());
                    else p.putString(e.getKey(), String.valueOf(e.getValue()));
                }
                p.commit();
                Boolean greeting = ThemeMigration.greetingFromOld(bannerTheme.getAll(), banners.getAll());
                if (greeting != null) banners.edit().putBoolean(ThemeMigration.FESTIVAL_GREETING, greeting).commit();

                eyes.edit().remove(ThemeMigration.ENABLED).commit();
                bannerTheme.edit().remove(ThemeMigration.ENABLED).remove(ThemeMigration.FESTIVAL_GREETING).commit();
                watching.edit().remove(ThemeMigration.ENABLED).commit();
                topBar.edit().remove(ThemeMigration.STYLE).commit();
                meta.edit().putInt(VERSION, ThemeMigration.VERSION).commit();
            }
            migrated = true;
        }
    }
}
