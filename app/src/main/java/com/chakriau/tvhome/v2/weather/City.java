package com.chakriau.tvhome.v2.weather;

import java.util.Locale;

/** A place to show weather for: its name (as shown in the top bar), where it is, and a detail line for search. */
public final class City {
    public final String name;
    public final double latitude;
    public final double longitude;
    /** State and country, for telling apart places with the same name in a search ("Karnataka, India"). */
    public final String detail;

    public City(String name, double latitude, double longitude, String detail) {
        this.name = name == null ? "" : name.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.detail = detail == null ? "" : detail.trim();
    }

    public boolean valid() {
        return !name.isEmpty() && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    /** One line, for saving: name|lat|lon|detail. '|' is removed from the text parts. */
    public String serialize() {
        return clean(name) + "|" + String.format(Locale.ROOT, "%.5f", latitude) + "|"
                + String.format(Locale.ROOT, "%.5f", longitude) + "|" + clean(detail);
    }

    /** Reads {@link #serialize()}; null if missing or unreadable. */
    public static City parse(String s) {
        if (s == null) return null;
        String[] p = s.split("\\|", -1);
        if (p.length < 3) return null;
        try {
            City c = new City(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), p.length > 3 ? p[3] : "");
            return c.valid() ? c : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String s) {
        return s.replace('|', ' ').replace('\n', ' ');
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof City && ((City) o).serialize().equals(serialize());
    }

    @Override
    public int hashCode() {
        return serialize().hashCode();
    }
}
