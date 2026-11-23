package com.chakriau.tvhome.v2.weather;

/** Open-Meteo's WMO weather codes as a condition and an icon (Requirements.md 5.8). Plain Java. */
public final class WeatherCode {

    public enum Kind {
        CLEAR("Clear"), PARTLY_CLOUDY("Partly cloudy"), CLOUDY("Cloudy"), FOG("Fog"), DRIZZLE("Drizzle"),
        RAIN("Rain"), SNOW("Snow"), STORM("Thunderstorm");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private WeatherCode() {
    }

    public static Kind kind(int code) {
        if (code == 0) return Kind.CLEAR;
        if (code == 1 || code == 2) return Kind.PARTLY_CLOUDY;
        if (code == 3) return Kind.CLOUDY;
        if (code == 45 || code == 48) return Kind.FOG;
        if (code >= 51 && code <= 57) return Kind.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Kind.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95 && code <= 99) return Kind.STORM;
        return Kind.CLOUDY;
    }

    /**
     * What a WMO code means in words, as the weather service reported it ("Mainly clear"), for the phone page's Test
     * tab, which explains why Home draws what it draws. Unknown codes: "Code n".
     */
    public static String describe(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: return "Fog";
            case 48: return "Freezing fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Heavy drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "Code " + code;
        }
    }

    /** Celsius to Fahrenheit, rounded. */
    public static int toFahrenheit(double celsius) {
        return (int) Math.round(celsius * 9 / 5 + 32);
    }

    /** The temperature as shown: "24°" in the chosen unit. */
    public static String degrees(double celsius, boolean fahrenheit) {
        return (fahrenheit ? toFahrenheit(celsius) : (int) Math.round(celsius)) + "°";
    }
}
