package com.chakriau.tvhome.v2.sky;

import android.content.Context;
import android.content.SharedPreferences;

import com.chakriau.tvhome.v2.weather.City;
import com.chakriau.tvhome.v2.weather.TimeFormats;
import com.chakriau.tvhome.v2.weather.WeatherCode;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import java.time.ZoneId;

/**
 * The sky's inputs on the TV (Requirements.md 5.10): the place (the weather's city, for sunrise and sunset), the
 * weather on show, and the phone page's test, saved in {@code sky_test} (never backed up: a test is over in 15
 * minutes).
 */
public final class SkyStore {

    public static final String TEST_FILE = "sky_test";

    private final SharedPreferences prefs;
    private final WeatherStore weather;

    public SkyStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE);
        weather = new WeatherStore(app);
    }

    /** The phone page's test, or null if none is running. */
    public SkyState.Test test() {
        SkyState.Test t = SkyState.Test.parse(prefs.getString("test", null));
        return t != null && t.active(System.currentTimeMillis()) ? t : null;
    }

    public void setTest(SkyState.Test t) {
        prefs.edit().putString("test", t.serialize()).apply();
    }

    public void stopTest() {
        prefs.edit().remove("test").apply();
    }

    /** {latitude, longitude} of the weather's city (set by hand, or the last one found), or null. */
    public double[] place() {
        City c = weather.automatic() ? weather.lastGood() : weather.byHand();
        if (c == null) c = weather.automatic() ? weather.byHand() : weather.lastGood();
        return c == null || !c.valid() ? null : new double[]{c.latitude, c.longitude};
    }

    /** The name of that city, or null. */
    public String placeName() {
        City c = weather.automatic() ? weather.lastGood() : weather.byHand();
        if (c == null) c = weather.automatic() ? weather.byHand() : weather.lastGood();
        return c == null ? null : c.name;
    }

    /** The weather on show (null when weather is off or nothing recent is known). */
    public WeatherCode.Kind realKind() {
        if (!weather.enabled()) return null;
        WeatherStore.Reading r = weather.reading();
        if (r == null || !TimeFormats.showable(r.fetchedAt, System.currentTimeMillis())) return null;
        return WeatherCode.kind(r.code);
    }

    /** The sky now, with the test if one is running. */
    public SkyState now() {
        return SkyState.at(System.currentTimeMillis(), ZoneId.systemDefault(), place(), realKind(), test());
    }
}
