package com.chakriau.tvhome.v2.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import org.junit.Test;

public class WeatherLogicTest {

    private static final City TV = new City("Chennai", 13.08, 80.27, "Tamil Nadu, India");
    private static final City IP = new City("Bengaluru", 12.97, 77.59, "Karnataka, India");
    private static final City LAST = new City("Mysuru", 12.30, 76.64, "");
    private static final City HAND = new City("Hyderabad", 17.38, 78.48, "Telangana, India");

    // ---- Location order -------------------------------------------------------------------------------------------

    @Test
    public void automaticPrefersTheTvThenIpThenLastGoodThenByHand() {
        assertEquals(TV, LocationChoice.choose(true, TV, false, IP, LAST, HAND).city);
        assertEquals(IP, LocationChoice.choose(true, null, false, IP, LAST, HAND).city);
        assertEquals(LAST, LocationChoice.choose(true, null, false, null, LAST, HAND).city);
        assertEquals(HAND, LocationChoice.choose(true, null, false, null, null, HAND).city);
        assertNull(LocationChoice.choose(true, null, false, null, null, null).city);
    }

    @Test
    public void ipLocationIsSkippedWhileAVpnIsOn() {
        LocationChoice.Result r = LocationChoice.choose(true, null, true, IP, LAST, HAND);
        assertEquals(LAST, r.city);
        assertEquals(LocationChoice.Source.LAST_GOOD, r.source);
        // The TV's own location is still fine with a VPN
        assertEquals(TV, LocationChoice.choose(true, TV, true, IP, LAST, HAND).city);
    }

    @Test
    public void setByHandUsesOnlyThatCity() {
        assertEquals(HAND, LocationChoice.choose(false, TV, false, IP, LAST, HAND).city);
        assertNull(LocationChoice.choose(false, TV, false, IP, LAST, null).city);
    }

    @Test
    public void onlyAutomaticFindsAreRememberedAsLastGood() {
        assertTrue(LocationChoice.remember(LocationChoice.choose(true, TV, false, null, null, null)));
        assertTrue(LocationChoice.remember(LocationChoice.choose(true, null, false, IP, null, null)));
        assertFalse(LocationChoice.remember(LocationChoice.choose(true, null, false, null, LAST, null)));
        assertFalse(LocationChoice.remember(LocationChoice.choose(false, null, false, null, null, HAND)));
    }

    @Test
    public void invalidPlacesAreIgnored() {
        City nowhere = new City("Null Island", 0, 0, "");
        City nameless = new City(" ", 12, 77, "");
        assertEquals(IP, LocationChoice.choose(true, nowhere, false, IP, null, null).city);
        assertEquals(IP, LocationChoice.choose(true, nameless, false, IP, null, null).city);
    }

    // ---- City -----------------------------------------------------------------------------------------------------

    @Test
    public void citySavesAndReads() {
        City c = City.parse(HAND.serialize());
        assertEquals(HAND, c);
        assertEquals("Telangana, India", c.detail);
        assertEquals(17.38, c.latitude, 1e-6);
        assertNull(City.parse(null));
        assertNull(City.parse("junk"));
        assertNull(City.parse("A|x|y"));
        assertEquals("A B", City.parse(new City("A|B", 1, 2, "").serialize()).name);
    }

    // ---- Weather codes and units ----------------------------------------------------------------------------------

    @Test
    public void weatherCodesMapToConditions() {
        assertEquals(WeatherCode.Kind.CLEAR, WeatherCode.kind(0));
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, WeatherCode.kind(2));
        assertEquals(WeatherCode.Kind.CLOUDY, WeatherCode.kind(3));
        assertEquals(WeatherCode.Kind.FOG, WeatherCode.kind(45));
        assertEquals(WeatherCode.Kind.DRIZZLE, WeatherCode.kind(53));
        assertEquals(WeatherCode.Kind.RAIN, WeatherCode.kind(63));
        assertEquals(WeatherCode.Kind.RAIN, WeatherCode.kind(81));
        assertEquals(WeatherCode.Kind.SNOW, WeatherCode.kind(75));
        assertEquals(WeatherCode.Kind.STORM, WeatherCode.kind(95));
        assertEquals(WeatherCode.Kind.CLOUDY, WeatherCode.kind(-1));
    }

    @Test
    public void degreesInEitherUnit() {
        assertEquals("24°", WeatherCode.degrees(24.4, false));
        assertEquals("76°", WeatherCode.degrees(24.4, true));
        assertEquals("-3°", WeatherCode.degrees(-2.6, false));
        assertEquals(32, WeatherCode.toFahrenheit(0));
    }

    // ---- Clock, date and refresh ----------------------------------------------------------------------------------

    @Test
    public void clockChoiceFollowsTheTvOnlyWhenAutomatic() {
        assertTrue(TimeFormats.Clock.AUTO.is24(true));
        assertFalse(TimeFormats.Clock.AUTO.is24(false));
        assertTrue(TimeFormats.Clock.H24.is24(false));
        assertFalse(TimeFormats.Clock.H12.is24(true));
        assertEquals(TimeFormats.Clock.H12, TimeFormats.Clock.AUTO.next());
        assertEquals(TimeFormats.Clock.AUTO, TimeFormats.Clock.H24.next());
        assertEquals(TimeFormats.Clock.AUTO, TimeFormats.Clock.parse("junk"));
    }

    @Test
    public void dateFormatsNeverShowTheYear() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 6);
        String year = "2026";
        for (TimeFormats.DateStyle d : TimeFormats.DateStyle.values()) {
            String shown = new SimpleDateFormat(d.pattern, Locale.ENGLISH).format(c.getTime());
            assertFalse(shown, shown.contains(year));
        }
        assertEquals("Fri, 6 Nov", new SimpleDateFormat(TimeFormats.DateStyle.SHORT.pattern, Locale.ENGLISH)
                .format(c.getTime()));
        assertEquals(TimeFormats.DateStyle.SHORT, TimeFormats.DateStyle.parse(null));
    }

    @Test
    public void weatherRefreshesHourlyAndHidesWhenStale() {
        long h = TimeFormats.REFRESH_MS;
        assertTrue(TimeFormats.refreshDue(0, 5));
        assertFalse(TimeFormats.refreshDue(1000, 1000 + h - 1));
        assertTrue(TimeFormats.refreshDue(1000, 1000 + h));
        assertTrue(TimeFormats.refreshDue(5000, 1000));   // clock set back
        assertTrue(TimeFormats.showable(1000, 1000 + 2 * h));
        assertFalse(TimeFormats.showable(1000, 1000 + 3 * h));
        assertFalse(TimeFormats.showable(0, 10));
        assertEquals(h - 1000, TimeFormats.untilRefresh(10_000, 11_000));
        assertEquals(60_000L, TimeFormats.untilRefresh(0, 11_000));
    }

    @Test
    public void codesAreDescribedForThePhonesTestTab() {
        assertEquals("Mainly clear", WeatherCode.describe(1));   // OpenWeatherMap's "few clouds" (801)
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, WeatherCode.kind(1));
        assertEquals("Overcast", WeatherCode.describe(3));
        assertEquals("Thunderstorm", WeatherCode.describe(95));
        assertEquals("Code 42", WeatherCode.describe(42));
    }

    @Test
    public void clockCountsAsSetFrom2025() {
        assertFalse(TimeFormats.clockSet(0L));                        // 1970: no clock battery, no internet yet
        assertFalse(TimeFormats.clockSet(1_704_067_200_000L));        // 1 Jan 2024, e.g. the firmware's build date
        assertFalse(TimeFormats.clockSet(TimeFormats.CLOCK_SET_AFTER - 1));
        assertTrue(TimeFormats.clockSet(TimeFormats.CLOCK_SET_AFTER));
        assertTrue(TimeFormats.clockSet(1_795_219_200_000L));         // Nov 2026
    }
}
