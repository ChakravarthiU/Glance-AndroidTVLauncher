package com.chakriau.tvhome.v2.festive;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The phone page's banner and lights test (Requirements.md 5.14), saved in {@code decor_test}: never backed up, as a
 * test is over in 15 minutes. Keys: {@code banner}, {@code design}, {@code line1}, {@code line2}, {@code lights}
 * (enum names / design id / text) and {@code until} (epoch ms).
 */
public final class DecorTestStore {

    public static final String FILE = "decor_test";

    private final SharedPreferences prefs;

    public DecorTestStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The test, or null if none is running. */
    public DecorTest test() {
        long until = prefs.getLong("until", 0);
        if (until <= System.currentTimeMillis()) return null;
        return new DecorTest(
                DecorTest.parse(DecorTest.BannerChoice.class, prefs.getString("banner", null),
                        DecorTest.BannerChoice.REAL),
                Banners.Design.parse(prefs.getString("design", null)),
                prefs.getString("line1", ""), prefs.getString("line2", ""),
                DecorTest.parse(DecorTest.LightsChoice.class, prefs.getString("lights", null),
                        DecorTest.LightsChoice.REAL),
                until);
    }

    public void set(DecorTest t) {
        prefs.edit()
                .putString("banner", t.banner.name())
                .putString("design", t.design == null ? "" : t.design.id)
                .putString("line1", t.line1)
                .putString("line2", t.line2)
                .putString("lights", t.lights.name())
                .putLong("until", t.until)
                .apply();
    }

    public void stop() {
        prefs.edit().clear().apply();
    }
}
