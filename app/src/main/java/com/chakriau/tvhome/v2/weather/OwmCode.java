package com.chakriau.tvhome.v2.weather;

/**
 * OpenWeatherMap's condition ids (weather[0].id) as the WMO codes the launcher keeps (Open-Meteo's), so a reading
 * from either service is saved, shown and drawn the same way ({@link WeatherCode}). Plain Java.
 *
 * <pre>
 * 2xx thunderstorm → 95        3xx drizzle → 51 / 53 / 55       5xx rain → 61 / 63 / 65, 66 freezing, 80-82 showers
 * 6xx snow → 71 / 73 / 75, 85-86 showers      7xx mist, haze, fog, dust → 45 (squall 771 → 81, tornado 781 → 95)
 * 800 clear → 0     801 few clouds → 1     802 scattered → 2     803 broken, 804 overcast → 3
 * </pre>
 */
public final class OwmCode {

    private OwmCode() {
    }

    public static int toWmo(int id) {
        if (id >= 200 && id < 300) return 95;
        if (id >= 300 && id < 400) {
            if (id == 300 || id == 310) return 51;
            if (id == 302 || id == 312 || id == 314) return 55;
            return 53;
        }
        if (id >= 500 && id < 600) {
            switch (id) {
                case 500: return 61;
                case 501: return 63;
                case 511: return 66;
                case 520: return 80;
                case 521: return 81;
                case 522: case 531: return 82;
                default: return id < 511 ? 65 : 63;
            }
        }
        if (id >= 600 && id < 700) {
            switch (id) {
                case 600: return 71;
                case 601: return 73;
                case 602: return 75;
                case 620: case 621: return 85;
                case 622: return 86;
                default: return 73;   // sleet and rain-and-snow
            }
        }
        if (id == 771) return 81;
        if (id == 781) return 95;
        if (id >= 700 && id < 800) return 45;
        if (id == 800) return 0;
        if (id == 801) return 1;
        if (id == 802) return 2;
        if (id == 803 || id == 804) return 3;
        return 3;   // unknown: cloudy, never rain
    }

    /** OpenWeatherMap's icon ends in "n" at night ("04n"), "d" by day. */
    public static boolean day(String icon) {
        return icon == null || !icon.endsWith("n");
    }
}
