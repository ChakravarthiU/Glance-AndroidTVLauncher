package com.chakriau.tvhome.v2.eyes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class EyesLogicTest {

    // ---- Personalities --------------------------------------------------------------------------------------------

    @Test
    public void v1NamesStillParse() {
        assertEquals(Personality.SLEEPY, Personality.parse("sleepy"));
        assertEquals(Personality.MISCHIEVOUS, Personality.parse(" Mischievous "));
        assertEquals(Personality.CURIOUS, Personality.parse("grumpy"));
        assertEquals(Personality.CURIOUS, Personality.parse(null));
    }

    @Test
    public void nextAndPreviousWrapRound() {
        Personality last = Personality.values()[Personality.values().length - 1];
        assertEquals(Personality.CURIOUS, last.next());
        assertEquals(last, Personality.CURIOUS.previous());
        for (Personality p : Personality.values()) assertEquals(p, p.next().previous());
    }

    @Test
    public void timingsAreSane() {
        for (Personality p : Personality.values()) {
            assertTrue(p.name(), p.blinkGapMin <= p.blinkGapMax && p.blinkGapMin > p.blinkMs);
            assertTrue(p.name(), p.wanderGapMin <= p.wanderGapMax && p.wanderGapMin > p.glanceMs);
            assertTrue(p.name(), p.speed > 0 && p.speed < 1 && p.travel > 0 && p.travel < 0.6f);
            assertTrue(p.name(), p.lookAtEachOther >= 0 && p.lookAtEachOther <= 1);
        }
        assertEquals(10, Personality.between(10, 20, 0f));
        assertEquals(20, Personality.between(10, 20, 1.5f));
        assertEquals(15, Personality.between(10, 20, 0.5f));
    }

    @Test
    public void sizesCycleAndParse() {
        assertEquals(EyeSize.LARGE, EyeSize.MEDIUM.next());
        assertEquals(EyeSize.SMALL, EyeSize.EXTRA_LARGE.next());
        assertEquals(EyeSize.MEDIUM, EyeSize.parse(null));
        assertEquals(EyeSize.EXTRA_LARGE, EyeSize.parse("EXTRA_LARGE"));
    }

    @Test
    public void theLargestEyesStillFitTheHomeSpace() {
        float r = 38 * EyeSize.EXTRA_LARGE.scale;
        float[] s = EyeSpots.pick(new Random(5), AREA, Collections.emptyList(), r * 4.9f, r * 3.2f, 70);
        assertTrue(Math.abs(s[1] - s[3]) >= 70);
        assertTrue(s[0] + r * 4.9f / 2 <= 960 && s[2] - r * 4.9f / 2 >= 960);
    }

    // ---- Spots ----------------------------------------------------------------------------------------------------

    private static final EyeSpots.Box AREA = new EyeSpots.Box(110, 280, 1810, 600);
    private static final float W = 186, H = 122;

    @Test
    public void onePairInEachHalfInsideTheAreaAtDifferentHeights() {
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) {
            float[] s = EyeSpots.pick(r, AREA, Collections.emptyList(), W, H, 80);
            assertTrue(s[0] - W / 2 >= AREA.left && s[0] + W / 2 <= 960);
            assertTrue(s[2] - W / 2 >= 960 && s[2] + W / 2 <= AREA.right);
            for (float y : new float[]{s[1], s[3]}) assertTrue(y - H / 2 >= AREA.top && y + H / 2 <= AREA.bottom);
            assertTrue(Math.abs(s[1] - s[3]) >= 80);
        }
    }

    @Test
    public void keepsClearOfWhatItMustNotCover() {
        EyeSpots.Box banner = new EyeSpots.Box(400, 250, 1520, 580);
        List<EyeSpots.Box> avoid = Collections.singletonList(banner);
        Random r = new Random(7);
        for (int i = 0; i < 200; i++) {
            float[] s = EyeSpots.pick(r, AREA, avoid, W, H, 80);
            assertTrue(EyeSpots.clear(new float[]{s[0], s[1]}, avoid, W, H));
            assertTrue(EyeSpots.clear(new float[]{s[2], s[3]}, avoid, W, H));
        }
    }

    @Test
    public void tooTightGivesTheEdgesInsteadOfLoopingForever() {
        EyeSpots.Box everything = new EyeSpots.Box(0, 0, 1920, 1080);
        float[] s = EyeSpots.pick(new Random(3), AREA, Arrays.asList(everything), W, H, 80);
        assertEquals(AREA.left + W / 2, s[0], 0.01f);
        assertEquals(AREA.right - W / 2, s[2], 0.01f);
        assertFalse(s[1] == s[3]);
    }
}
