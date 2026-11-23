package com.chakriau.tvhome.v2.system;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings › System: what a press on the Settings button opens (global). The button style is per theme (ThemeParts). */
public final class TopBarStore {

    private final SharedPreferences prefs;

    public TopBarStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("top_bar", Context.MODE_PRIVATE);
    }

    public TopBar.SettingsPress settingsPress() {
        return TopBar.SettingsPress.parse(prefs.getString("settings_press", null));
    }

    public void setSettingsPress(TopBar.SettingsPress p) {
        prefs.edit().putString("settings_press", p.name()).apply();
    }
}
