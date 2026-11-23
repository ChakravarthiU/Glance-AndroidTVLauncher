package com.chakriau.tvhome.v2.eyes;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings › Eyes (Requirements.md 5.2, 7): the personality and the size. Theme settings, saved per theme, so two
 * themes can have different eyes. On/off is in {@code home/ThemeParts} with the other optional parts.
 */
public final class EyesStore {

    private final SharedPreferences prefs;

    public EyesStore(Context context, String themeId) {
        prefs = context.getApplicationContext().getSharedPreferences("eyes_" + themeId, Context.MODE_PRIVATE);
    }

    public Personality personality() {
        return Personality.parse(prefs.getString("personality", null));
    }

    public void setPersonality(Personality p) {
        prefs.edit().putString("personality", p.name()).apply();
    }

    public EyeSize size() {
        return EyeSize.parse(prefs.getString("size", null));
    }

    public void setSize(EyeSize s) {
        prefs.edit().putString("size", s.name()).apply();
    }
}
