package com.chakriau.tvhome.v2.weather;

/**
 * Where the weather comes from (Requirements.md 5.8), chosen in Settings › Time, date &amp; weather. Plain Java.
 * Open-Meteo needs nothing; OpenWeatherMap needs the owner's own API key, entered on the phone page.
 */
public enum WeatherSource {
    OPEN_METEO("Open-Meteo", "Free, no key needed"),
    OPEN_WEATHER_MAP("OpenWeatherMap", "Needs your OpenWeatherMap key (added on the phone page)");

    public final String label, note;

    WeatherSource(String label, String note) {
        this.label = label;
        this.note = note;
    }

    /** The saved choice; Open-Meteo when nothing (or something unknown) is saved. */
    public static WeatherSource parse(String s) {
        if (s != null) for (WeatherSource w : values()) if (w.name().equals(s)) return w;
        return OPEN_METEO;
    }

    /** Whether a typed OpenWeatherMap key looks like one (their keys are 32 letters and digits). */
    public static boolean keyLooksRight(String key) {
        return key != null && key.matches("[A-Za-z0-9]{16,64}");
    }

    /** A key as shown back on screen: never in full ("…a1b2"). */
    public static String masked(String key) {
        if (key == null || key.length() < 4) return "";
        return "…" + key.substring(key.length() - 4);
    }
}
