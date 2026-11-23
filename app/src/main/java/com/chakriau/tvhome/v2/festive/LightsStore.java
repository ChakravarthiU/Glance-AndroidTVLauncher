package com.chakriau.tvhome.v2.festive;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings › Festive lights & banner › Lights: a theme setting, saved per theme (Requirements.md section 7). */
public final class LightsStore {

    private final SharedPreferences prefs;

    public LightsStore(Context context, String themeId) {
        prefs = context.getApplicationContext().getSharedPreferences("lights_" + themeId, Context.MODE_PRIVATE);
    }

    public Festivals.LightsMode mode() {
        return Festivals.LightsMode.parse(prefs.getString("mode", null));
    }

    public void setMode(Festivals.LightsMode m) {
        prefs.edit().putString("mode", m.name()).apply();
    }
}
