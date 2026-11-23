package com.chakriau.tvhome.v2.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.chakriau.tvhome.v2.weather.TimeFormats;

import org.junit.Test;

public class ClockMathTest {

    private static final float E = 0.001f;

    // ---- Which characters roll ------------------------------------------------------------------------------------

    @Test
    public void onlyTheLastDigitRollsMostMinutes() {
        assertArrayEquals(new boolean[]{false, false, false, false, true}, ClockMath.changed("11:36", "11:37"));
    }

    @Test
    public void tensAndUnitsRollAtTheTopOfTheTenMinutes() {
        assertArrayEquals(new boolean[]{false, false, false, true, true}, ClockMath.changed("11:39", "11:40"));
    }

    @Test
    public void aLongerTimeLinesUpFromTheRight() {
        // 9:59 → 10:00: every position changes, and the new leading digit has no old one.
        assertArrayEquals(new boolean[]{true, true, false, true, true}, ClockMath.changed("9:59", "10:00"));
        assertArrayEquals(new boolean[]{true, true, false, true, true}, ClockMath.changed("12:59", "1:00"));
    }

    @Test
    public void amToPmRollsOnlyTheFirstLetter() {
        assertArrayEquals(new boolean[]{true, false}, ClockMath.changed("AM", "PM"));
        assertArrayEquals(new boolean[]{false, false}, ClockMath.changed("PM", "PM"));
    }

    @Test
    public void firstShowRollsEverything() {
        assertArrayEquals(new boolean[]{true, true}, ClockMath.changed("", "AM"));
    }

    // ---- Timing ---------------------------------------------------------------------------------------------------

    @Test
    public void rightmostRollsFirstAndEachToItsLeftFollows() {
        boolean[] c = ClockMath.changed("11:59", "12:00");
        long[] d = ClockMath.delays(c);
        assertEquals(0, d[4]);
        assertEquals(ClockMath.STAGGER_MS, d[3]);
        assertEquals(0, d[2]);   // ':' doesn't change
        assertEquals(2 * ClockMath.STAGGER_MS, d[1]);
        assertEquals(ClockMath.ROLL_MS + 2 * ClockMath.STAGGER_MS, ClockMath.rollLength(c));
    }

    @Test
    public void nothingChangedMeansNoRoll() {
        assertEquals(0, ClockMath.rollLength(ClockMath.changed("11:37", "11:37")));
    }

    @Test
    public void rollStartsAtZeroAndEndsAtOne() {
        assertEquals(0f, ClockMath.rollAt(0, 100), E);
        assertEquals(0f, ClockMath.rollAt(100, 100), E);
        assertEquals(1f, ClockMath.rollAt(100 + ClockMath.ROLL_MS, 100), E);
        assertEquals(1f, ClockMath.rollAt(10_000, 0), E);
    }

    @Test
    public void easingOvershootsALittleThenSettles() {
        assertEquals(0f, ClockMath.easeOutBack(0f), E);
        assertEquals(1f, ClockMath.easeOutBack(1f), E);
        float max = 0f;
        for (int i = 0; i <= 100; i++) max = Math.max(max, ClockMath.easeOutBack(i / 100f));
        assertTrue(max > 1f && max < 1.15f);
    }

    // ---- Analog hands ---------------------------------------------------------------------------------------------

    @Test
    public void handsPointWhereAClocksWould() {
        assertEquals(0f, ClockMath.hourAngle(12, 0, 0), E);
        assertEquals(90f, ClockMath.hourAngle(15, 0, 0), E);
        assertEquals(348.5f, ClockMath.hourAngle(11, 37, 0), E);
        assertEquals(222f, ClockMath.minuteAngle(37, 0), E);
        assertEquals(225f, ClockMath.minuteAngle(37, 30), E);
        assertEquals(270f, ClockMath.secondAngle(45), E);
    }

    @Test
    public void handsTurnTheShortWay() {
        assertEquals(6f, ClockMath.sweep(354f, 0f), E);
        assertEquals(-6f, ClockMath.sweep(0f, 354f), E);   // clock set back a minute
        assertEquals(357f, ClockMath.turn(354f, 0f, 0.5f), E);
        assertEquals(357f, ClockMath.turn(0f, 354f, 0.5f), E);
        assertEquals(0f, ClockMath.turn(354f, 0f, 1f), E);
    }

    // ---- Settings ---------------------------------------------------------------------------------------------------

    @Test
    public void clockFaceDefaultsToDigital() {
        assertEquals(TimeFormats.Face.DIGITAL, TimeFormats.Face.parse(null));
        assertEquals(TimeFormats.Face.DIGITAL, TimeFormats.Face.parse("nonsense"));
        assertEquals(TimeFormats.Face.ANALOG, TimeFormats.Face.parse("ANALOG"));
        assertTrue(TimeFormats.Face.ANALOG_SECONDS.analog());
        assertTrue(TimeFormats.Face.ANALOG_SECONDS.seconds());
        assertTrue(!TimeFormats.Face.ANALOG.seconds());
        assertTrue(!TimeFormats.Face.DIGITAL.analog());
    }
}
