package com.chakriau.tvhome.v2.apps;

import android.content.Context;
import android.content.SharedPreferences;

/** Saves {@link AppStates} in the app's private preferences. Global data: every theme uses it. */
public final class AppStore {

    private static final String FILE = "apps";
    private static final String KEY = "states";

    private final SharedPreferences prefs;

    public AppStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public AppStates load() {
        return AppStates.parse(prefs.getString(KEY, null));
    }

    public void save(AppStates states) {
        prefs.edit().putString(KEY, states.serialize()).apply();
    }
}
