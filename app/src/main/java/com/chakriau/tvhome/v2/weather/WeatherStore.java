package com.chakriau.tvhome.v2.weather;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings › Time, date &amp; weather (global data: every theme uses it), plus the last weather reading, so Home can show
 * it at once instead of waiting for the network.
 */
public final class WeatherStore {

    /** A weather reading for a place. Temperatures are kept in °C and converted for display. */
    public static final class Reading {
        public final double celsius;
        public final int code;
        public final boolean day;
        public final String city;
        public final long fetchedAt;

        public Reading(double celsius, int code, boolean day, String city, long fetchedAt) {
            this.celsius = celsius;
            this.code = code;
            this.day = day;
            this.city = city;
            this.fetchedAt = fetchedAt;
        }
    }

    private final SharedPreferences prefs;

    public WeatherStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return prefs.getBoolean("enabled", true);
    }

    public void setEnabled(boolean on) {
        prefs.edit().putBoolean("enabled", on).apply();
    }

    public boolean fahrenheit() {
        return prefs.getBoolean("fahrenheit", false);
    }

    public void setFahrenheit(boolean on) {
        prefs.edit().putBoolean("fahrenheit", on).apply();
    }

    public TimeFormats.Clock clock() {
        return TimeFormats.Clock.parse(prefs.getString("clock", null));
    }

    public void setClock(TimeFormats.Clock c) {
        prefs.edit().putString("clock", c.name()).apply();
    }

    /** Digital (default) or analog clock in the top bar. */
    public TimeFormats.Face clockFace() {
        return TimeFormats.Face.parse(prefs.getString("clock_face", null));
    }

    public void setClockFace(TimeFormats.Face f) {
        prefs.edit().putString("clock_face", f.name()).apply();
    }

    /** Whether the digital clock's digits roll up when they change (default on). */
    public boolean clockRoll() {
        return prefs.getBoolean("clock_roll", true);
    }

    public void setClockRoll(boolean on) {
        prefs.edit().putBoolean("clock_roll", on).apply();
    }

    public TimeFormats.DateStyle dateStyle() {
        return TimeFormats.DateStyle.parse(prefs.getString("date", null));
    }

    public void setDateStyle(TimeFormats.DateStyle d) {
        prefs.edit().putString("date", d.name()).apply();
    }

    /** False when the owner set the city by hand. */
    public boolean automatic() {
        return prefs.getBoolean("automatic", true);
    }

    public void setAutomatic(boolean on) {
        prefs.edit().putBoolean("automatic", on).remove("reading_at").apply();   // refetch for the new place
    }

    public City byHand() {
        return City.parse(prefs.getString("by_hand", null));
    }

    public void setByHand(City c) {
        prefs.edit().putString("by_hand", c.serialize()).remove("reading_at").apply();
    }

    public City lastGood() {
        return City.parse(prefs.getString("last_good", null));
    }

    public void setLastGood(City c) {
        prefs.edit().putString("last_good", c.serialize()).apply();
    }

    /** Where the last reading's place came from (for Settings), or null. */
    public LocationChoice.Source source() {
        String s = prefs.getString("source", null);
        if (s == null) return null;
        try {
            return LocationChoice.Source.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Reading reading() {
        long at = prefs.getLong("reading_at", 0);
        if (at <= 0) return null;
        return new Reading(prefs.getFloat("reading_c", 0f), prefs.getInt("reading_code", -1),
                prefs.getBoolean("reading_day", true), prefs.getString("reading_city", ""), at);
    }

    public void setReading(Reading r, LocationChoice.Source source) {
        prefs.edit().putFloat("reading_c", (float) r.celsius).putInt("reading_code", r.code)
                .putBoolean("reading_day", r.day).putString("reading_city", r.city).putLong("reading_at", r.fetchedAt)
                .putString("source", source == null ? null : source.name()).apply();
    }

    /** Where the weather comes from: Open-Meteo (default) or OpenWeatherMap. */
    public WeatherSource provider() {
        return WeatherSource.parse(prefs.getString("provider", null));
    }

    public void setProvider(WeatherSource w) {
        prefs.edit().putString("provider", w.name()).remove("reading_at").apply();   // fetch again from it
    }

    /** The owner's OpenWeatherMap key, or null. Never logged or shown in full. */
    public String owmKey() {
        return prefs.getString("owm_key", null);
    }

    public void setOwmKey(String key) {
        SharedPreferences.Editor e = prefs.edit().remove("reading_at");
        if (key == null) e.remove("owm_key");
        else e.putString("owm_key", key);
        e.apply();
    }

    /** Which service gave the reading on show, and why OpenWeatherMap wasn't used if it was chosen (or null). */
    public WeatherSource readingProvider() {
        return WeatherSource.parse(prefs.getString("reading_provider", null));
    }

    public String providerProblem() {
        return prefs.getString("provider_problem", null);
    }

    public void setReadingProvider(WeatherSource from, String problem) {
        SharedPreferences.Editor e = prefs.edit().putString("reading_provider", from.name());
        if (problem == null) e.remove("provider_problem");
        else e.putString("provider_problem", problem);
        e.apply();
    }

    /** Forgets the reading, so the next refresh fetches again ("Refresh now"). */
    public void clearReading() {
        prefs.edit().remove("reading_at").apply();
    }
}
