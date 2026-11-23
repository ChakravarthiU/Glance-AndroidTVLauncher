package com.chakriau.tvhome.v2.weather;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpenWeatherMapTest {

    private static WeatherCode.Kind kind(int owmId) {
        return WeatherCode.kind(OwmCode.toWmo(owmId));
    }

    @Test
    public void cloudsAreCloudsNotDrizzle() {
        assertEquals(WeatherCode.Kind.CLEAR, kind(800));
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, kind(801));   // "few clouds"
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, kind(802));   // "scattered clouds"
        assertEquals(WeatherCode.Kind.CLOUDY, kind(803));          // "broken clouds"
        assertEquals(WeatherCode.Kind.CLOUDY, kind(804));          // "overcast clouds"
    }

    @Test
    public void everyGroupMapsToItsKind() {
        for (int id : new int[]{200, 201, 202, 210, 211, 212, 221, 230, 231, 232, 781}) {
            assertEquals("id " + id, WeatherCode.Kind.STORM, kind(id));
        }
        for (int id : new int[]{300, 301, 302, 310, 311, 312, 313, 314, 321}) {
            assertEquals("id " + id, WeatherCode.Kind.DRIZZLE, kind(id));
        }
        for (int id : new int[]{500, 501, 502, 503, 504, 511, 520, 521, 522, 531, 771}) {
            assertEquals("id " + id, WeatherCode.Kind.RAIN, kind(id));
        }
        for (int id : new int[]{600, 601, 602, 611, 612, 613, 615, 616, 620, 621, 622}) {
            assertEquals("id " + id, WeatherCode.Kind.SNOW, kind(id));
        }
        for (int id : new int[]{701, 711, 721, 731, 741, 751, 761, 762}) {
            assertEquals("id " + id, WeatherCode.Kind.FOG, kind(id));
        }
    }

    @Test
    public void heavierMeansAHeavierCode() {
        assertTrue(OwmCode.toWmo(500) < OwmCode.toWmo(501));
        assertTrue(OwmCode.toWmo(501) < OwmCode.toWmo(502));
        assertTrue(OwmCode.toWmo(600) < OwmCode.toWmo(602));
    }

    @Test
    public void anUnknownIdIsNeverRain() {
        assertEquals(WeatherCode.Kind.CLOUDY, kind(999));
        assertEquals(WeatherCode.Kind.CLOUDY, kind(0));
    }

    @Test
    public void nightComesFromTheIcon() {
        assertTrue(OwmCode.day("01d"));
        assertFalse(OwmCode.day("04n"));
        assertTrue(OwmCode.day(null));
    }

    @Test
    public void keysAreCheckedAndNeverShownInFull() {
        assertTrue(WeatherSource.keyLooksRight("0123456789abcdef0123456789ABCDEF"));
        assertFalse(WeatherSource.keyLooksRight("short"));
        assertFalse(WeatherSource.keyLooksRight("0123456789abcdef 0123456789abcde"));
        assertFalse(WeatherSource.keyLooksRight(null));
        assertEquals("…CDEF", WeatherSource.masked("0123456789abcdef0123456789ABCDEF"));
        assertEquals("", WeatherSource.masked(null));
    }

    @Test
    public void theSavedSourceIsReadBackOrOpenMeteo() {
        assertEquals(WeatherSource.OPEN_WEATHER_MAP, WeatherSource.parse("OPEN_WEATHER_MAP"));
        assertEquals(WeatherSource.OPEN_METEO, WeatherSource.parse(null));
        assertEquals(WeatherSource.OPEN_METEO, WeatherSource.parse("SOMETHING"));
    }
}
