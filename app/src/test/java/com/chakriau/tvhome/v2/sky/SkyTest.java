package com.chakriau.tvhome.v2.sky;

import com.chakriau.tvhome.v2.weather.WeatherCode;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkyTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final double[] CHENNAI = {13.0827, 80.2707};
    private static final double[] LONDON_PLACE = {51.5074, -0.1278};

    private static long at(int y, int m, int d, int h, int min, ZoneId z) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant().toEpochMilli();
    }

    private static void near(LocalTime expected, LocalTime actual, int minutes) {
        int diff = Math.abs(expected.toSecondOfDay() - actual.toSecondOfDay()) / 60;
        assertTrue("expected about " + expected + " but was " + actual, diff <= minutes);
    }

    // ---- Sunrise and sunset ----

    @Test
    public void chennaiSunriseAndSunsetInNovember() {
        // Published times for Chennai, 8 Nov 2026: sunrise about 6:00, sunset about 17:40.
        LocalTime[] t = SkyMath.riseAndSet(LocalDate.of(2026, 11, 8), IST, CHENNAI);
        near(LocalTime.of(6, 0), t[0], 6);
        near(LocalTime.of(17, 40), t[1], 6);
    }

    @Test
    public void londonOnTheLongestDay() {
        // 21 June: sunrise about 4:43, sunset about 21:21 (BST).
        LocalTime[] t = SkyMath.riseAndSet(LocalDate.of(2026, 6, 21), LONDON, LONDON_PLACE);
        near(LocalTime.of(4, 43), t[0], 4);
        near(LocalTime.of(21, 21), t[1], 4);
    }

    @Test
    public void noSunriseInThePolarNight() {
        assertNull(SkyMath.sunEvents(LocalDate.of(2026, 12, 21), 78.2, 15.6));   // Svalbard
        assertNotNull(SkyMath.sunEvents(LocalDate.of(2026, 12, 21), 13.08, 80.27));
    }

    @Test
    public void anUnknownPlaceUsesSixToSix() {
        LocalTime[] t = SkyMath.riseAndSet(LocalDate.of(2026, 11, 8), IST, null);
        assertEquals(LocalTime.of(6, 0), t[0]);
        assertEquals(LocalTime.of(18, 0), t[1]);
    }

    // ---- Sun or moon ----

    @Test
    public void theSunTakesTheDayAndTheMoonTheNight() {
        SkyMath.Position noon = SkyMath.position(at(2026, 11, 8, 12, 0, IST), IST, null);
        assertEquals(SkyMath.Body.SUN, noon.body);
        assertEquals(0.5f, noon.progress, 0.01f);

        SkyMath.Position morning = SkyMath.position(at(2026, 11, 8, 9, 0, IST), IST, null);
        assertEquals(SkyMath.Body.SUN, morning.body);
        assertEquals(0.25f, morning.progress, 0.01f);

        SkyMath.Position midnight = SkyMath.position(at(2026, 11, 9, 0, 0, IST), IST, null);
        assertEquals(SkyMath.Body.MOON, midnight.body);
        assertEquals(0.5f, midnight.progress, 0.01f);   // halfway from 18:00 to 6:00

        SkyMath.Position evening = SkyMath.position(at(2026, 11, 8, 21, 0, IST), IST, null);
        assertEquals(SkyMath.Body.MOON, evening.body);
        assertEquals(0.25f, evening.progress, 0.01f);

        SkyMath.Position early = SkyMath.position(at(2026, 11, 8, 3, 0, IST), IST, null);
        assertEquals(SkyMath.Body.MOON, early.body);
        assertEquals(0.75f, early.progress, 0.01f);
    }

    @Test
    public void theArcRisesLeftPeaksInTheMiddleAndSetsRight() {
        float[] rise = SkyMath.arc(0f, 100, 1800, 600, 200);
        float[] top = SkyMath.arc(0.5f, 100, 1800, 600, 200);
        float[] set = SkyMath.arc(1f, 100, 1800, 600, 200);
        assertEquals(100f, rise[0], 0.01f);
        assertEquals(600f, rise[1], 0.01f);
        assertEquals(950f, top[0], 0.01f);
        assertEquals(200f, top[1], 0.01f);
        assertEquals(1800f, set[0], 0.01f);
        assertEquals(600f, set[1], 0.5f);
        assertEquals(0f, SkyMath.horizonFade(0f), 0.001f);
        assertEquals(1f, SkyMath.horizonFade(0.5f), 0.001f);
        assertEquals(0.5f, SkyMath.horizonFade(0.98f), 0.01f);
    }

    @Test
    public void moonPhasesFollowKnownNewAndFullMoons() {
        // New moon 17 Feb 2026 12:01 UTC; full moon 3 Mar 2026 11:38 UTC (a day or so of error is fine).
        float newMoon = SkyMath.moonPhase(at(2026, 2, 17, 12, 1, ZoneId.of("UTC")));
        assertTrue("new moon phase " + newMoon, newMoon < 0.04f || newMoon > 0.96f);
        float full = SkyMath.moonPhase(at(2026, 3, 3, 11, 38, ZoneId.of("UTC")));
        assertEquals(0.5f, full, 0.04f);
    }

    @Test
    public void theSunWarmsNearTheHorizonInEightSteps() {
        assertEquals(0f, SkyMath.warmth(0.5f), 0f);
        assertEquals(0f, SkyMath.warmth(0.3f), 0f);
        assertEquals(1f, SkyMath.warmth(0f), 0f);
        assertEquals(1f, SkyMath.warmth(1f), 0f);
        assertEquals(SkyMath.warmth(0.05f), SkyMath.warmth(0.95f), 0f);   // the same at rising and setting
        assertTrue(SkyMath.warmth(0.05f) > SkyMath.warmth(0.12f));
        for (float p = 0; p <= 1f; p += 0.01f) {
            float w = SkyMath.warmth(p);
            assertEquals("8 steps at " + p, Math.round(w * 8), w * 8, 1e-4);
        }
    }

    @Test
    public void theMoonsGlowFollowsHowMuchIsLit() {
        assertEquals(0f, SkyMath.moonLit(0f), 1e-6);
        assertEquals(1f, SkyMath.moonLit(0.5f), 1e-6);
        assertEquals(SkyMath.moonLit(0.25f), SkyMath.moonLit(0.75f), 1e-6);
    }

    // ---- Weather ----

    @Test
    public void eachWeatherHasItsEffects() {
        SkyMath.Effects clear = SkyMath.effects(WeatherCode.Kind.CLEAR);
        assertEquals(0, clear.clouds);
        assertEquals(1f, clear.bodyAlpha, 0f);
        assertTrue(clear.stars);
        assertFalse(clear.fast());

        SkyMath.Effects unknown = SkyMath.effects(null);
        assertEquals(0, unknown.clouds);
        assertEquals(0, unknown.rain);

        assertTrue(SkyMath.effects(WeatherCode.Kind.PARTLY_CLOUDY).clouds > 0);
        assertFalse(SkyMath.effects(WeatherCode.Kind.PARTLY_CLOUDY).fast());
        SkyMath.Effects cloudy = SkyMath.effects(WeatherCode.Kind.CLOUDY);
        assertTrue(cloudy.clouds > SkyMath.effects(WeatherCode.Kind.PARTLY_CLOUDY).clouds);
        assertTrue(cloudy.bodyAlpha < 1f);

        SkyMath.Effects rain = SkyMath.effects(WeatherCode.Kind.RAIN);
        assertTrue(rain.rain > 0);
        assertTrue(rain.greyClouds);
        assertTrue(rain.fast());
        SkyMath.Effects drizzle = SkyMath.effects(WeatherCode.Kind.DRIZZLE);
        assertTrue(drizzle.drizzle);
        assertTrue(drizzle.rain < rain.rain);

        assertTrue(SkyMath.effects(WeatherCode.Kind.SNOW).snow > 0);
        assertTrue(SkyMath.effects(WeatherCode.Kind.STORM).lightning);
        assertTrue(SkyMath.effects(WeatherCode.Kind.FOG).haze);
    }

    @Test
    public void aCloudSitsInFrontOfADimmedSunOrMoon() {
        assertFalse(SkyMath.effects(WeatherCode.Kind.CLEAR).veil());
        assertFalse(SkyMath.effects(WeatherCode.Kind.PARTLY_CLOUDY).veil());
        assertTrue(SkyMath.effects(WeatherCode.Kind.CLOUDY).veil());
        assertTrue(SkyMath.effects(WeatherCode.Kind.RAIN).veil());
        assertFalse(SkyMath.effects(WeatherCode.Kind.STORM).veil());   // the sun is hidden anyway
        // Cloudy dims the sun, but it still reads on a pale photo.
        assertTrue(SkyMath.effects(WeatherCode.Kind.CLOUDY).bodyAlpha >= 0.5f);
    }

    @Test
    public void theCloudCoversTheLowerPartOfTheDiscAndRidesWithIt() {
        float r = 46, w = 300, h = 110;
        float[] a = SkyMath.veilAt(800, 400, r, w, h, 0);
        // Its tallest puff starts just above the centre and a little to the right.
        float puffTop = a[1] + 0.06f * h, middle = a[0] + w / 2f;
        assertTrue(puffTop < 400 && puffTop > 400 - r);
        assertTrue(middle > 800 && middle < 800 + r);
        // It moves with the body.
        float[] b = SkyMath.veilAt(900, 380, r, w, h, 0);
        assertEquals(a[0] + 100, b[0], 1e-3);
        assertEquals(a[1] - 20, b[1], 1e-3);
        // It sways, but never further than VEIL_SWAY radii.
        float[] q = SkyMath.veilAt(800, 400, r, w, h, SkyMath.VEIL_SWAY_MS / 4);
        assertEquals(a[0] + SkyMath.VEIL_SWAY * r, q[0], 1e-2);
        for (long t = 0; t < SkyMath.VEIL_SWAY_MS; t += 1000) {
            assertTrue(Math.abs(SkyMath.veilAt(800, 400, r, w, h, t)[0] - a[0]) <= SkyMath.VEIL_SWAY * r + 1e-3);
        }
    }

    // ---- Phone test ----

    @Test
    public void aTestShowsItsWeatherAndTimeUntilItEnds() {
        long now = at(2026, 11, 8, 12, 0, IST);
        SkyState.Test test = new SkyState.Test(WeatherCode.Kind.RAIN, 22 * 60, now + SkyState.TEST_MS);
        SkyState s = SkyState.at(now, IST, null, WeatherCode.Kind.CLEAR, test);
        assertTrue(s.testing);
        assertEquals(WeatherCode.Kind.RAIN, s.kind);
        assertEquals(SkyMath.Body.MOON, s.position.body);   // 22:00

        SkyState after = SkyState.at(now + SkyState.TEST_MS + 1, IST, null, WeatherCode.Kind.CLEAR, test);
        assertFalse(after.testing);
        assertEquals(WeatherCode.Kind.CLEAR, after.kind);
        assertEquals(SkyMath.Body.SUN, after.position.body);
    }

    @Test
    public void aTestOfTheRealWeatherOnlyMovesTheClock() {
        long now = at(2026, 11, 8, 12, 0, IST);
        SkyState.Test test = new SkyState.Test(null, 6 * 60 + 30, now + 1000);
        SkyState s = SkyState.at(now, IST, null, WeatherCode.Kind.SNOW, test);
        assertEquals(WeatherCode.Kind.SNOW, s.kind);
        assertEquals(SkyMath.Body.SUN, s.position.body);
        assertTrue(s.position.progress < 0.1f);   // just after sunrise
    }

    @Test
    public void testsAreSavedAndReadBack() {
        SkyState.Test t = new SkyState.Test(WeatherCode.Kind.STORM, 75, 123456789L);
        SkyState.Test back = SkyState.Test.parse(t.serialize());
        assertEquals(WeatherCode.Kind.STORM, back.kind);
        assertEquals(75, back.minuteOfDay);
        assertEquals(123456789L, back.until);
        SkyState.Test real = SkyState.Test.parse(new SkyState.Test(null, -1, 5L).serialize());
        assertNull(real.kind);
        assertEquals(-1, real.minuteOfDay);
        assertNull(SkyState.Test.parse("BOGUS|1|2"));
        assertNull(SkyState.Test.parse("nonsense"));
        assertNull(SkyState.Test.parse(null));
        assertEquals(-1, new SkyState.Test(null, 5000, 0).minuteOfDay);   // out of range: now
    }

    // ---- The moon over a pale wallpaper ----

    @Test
    public void skyLightAveragesEachColumn() {
        // 4 × 2 pixels: left half white, right half black, in 2 columns.
        int w = 0xFFFFFFFF, b = 0xFF000000;
        float[] l = SkyMath.skyLight(new int[]{w, w, b, b, w, w, b, b}, 4, 2, 2);
        assertEquals(2, l.length);
        assertEquals(1f, l[0], 0.001f);
        assertEquals(0f, l[1], 0.001f);
        assertNull(SkyMath.skyLight(new int[0], 4, 2, 2));
        // Green counts most, blue least (Rec. 709).
        float g = SkyMath.skyLight(new int[]{0xFF00FF00}, 1, 1, 1)[0];
        float bl = SkyMath.skyLight(new int[]{0xFF0000FF}, 1, 1, 1)[0];
        assertTrue(g > 0.7f && bl < 0.1f);
    }

    @Test
    public void lightIsReadBetweenColumns() {
        float[] l = {0f, 1f};
        assertEquals(0.5f, SkyMath.lightAt(l, 0.5f), 0.001f);
        assertEquals(0f, SkyMath.lightAt(l, -1f), 0.001f);
        assertEquals(1f, SkyMath.lightAt(l, 2f), 0.001f);
        assertEquals(0f, SkyMath.lightAt(null, 0.3f), 0.001f);
    }

    @Test
    public void theMoonGetsANightPatchOnlyOverPalePhotos() {
        assertEquals(0f, SkyMath.moonBackdrop(0.2f), 0.001f);   // a dark photo: as before
        assertEquals(0f, SkyMath.moonBackdrop(0.40f), 0.001f);
        assertEquals(0.5f, SkyMath.moonBackdrop(0.60f), 0.001f);
        assertEquals(1f, SkyMath.moonBackdrop(0.80f), 0.001f);
        assertEquals(1f, SkyMath.moonBackdrop(0.95f), 0.001f);   // a white photo
        assertTrue(SkyMath.moonBackdrop(0.5f) < SkyMath.moonBackdrop(0.7f));
    }

    @Test
    public void fogHazeFadesOutAtBothEdges() {
        float[] at = SkyMath.HAZE_STOPS;
        int[] alpha = SkyMath.HAZE_ALPHA;
        assertEquals(at.length, alpha.length);
        assertEquals(0, alpha[0]);
        assertEquals(0, alpha[alpha.length - 1]);   // no hard line above the apps band
        assertEquals(1f, at[at.length - 1], 0f);
        for (int i = 1; i < at.length; i++) assertTrue(at[i] > at[i - 1]);
        for (int a : alpha) assertTrue(a >= 0 && a <= 110);
    }

    @Test
    public void fogSunAtHalfAndVeilSeeThrough() {
        assertEquals(0.5f, SkyMath.effects(WeatherCode.Kind.FOG).bodyAlpha, 0f);
        assertTrue(SkyMath.effects(WeatherCode.Kind.FOG).veil());
        // The cloud in front is lighter than the drifting clouds (205 white, 225 grey).
        assertTrue(SkyMath.veilAlpha(false) < 205);
        assertTrue(SkyMath.veilAlpha(true) < 225);
        assertTrue(SkyMath.veilAlpha(false) > 0);
    }
}
