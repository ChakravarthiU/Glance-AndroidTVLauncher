package com.chakriau.tvhome.v2.festive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The phone page's banner and lights test on Home (Requirements.md 5.14). */
public class DecorTestTest {

    private static final long NOW = 1_000_000L, LATER = NOW + 15 * 60_000L;
    private static final Banners.Shown REAL = new Banners.Shown(Banners.Design.DIWALI, "Happy Diwali", "", "d1");

    private static DecorTest test(DecorTest.BannerChoice b, Banners.Design d, DecorTest.LightsChoice l) {
        return new DecorTest(b, d, "Happy Birthday, Alex!", "Have a lovely day", l, LATER);
    }

    @Test
    public void realChoicesChangeNothing() {
        DecorTest t = test(DecorTest.BannerChoice.REAL, null, DecorTest.LightsChoice.REAL);
        assertFalse(t.changesAnything());
        assertSame(REAL, DecorTest.banner(t, NOW, REAL));
        assertNull(DecorTest.banner(t, NOW, null));
        assertTrue(DecorTest.lightsOn(t, NOW, true));
        assertFalse(DecorTest.lightsOn(t, NOW, false));
    }

    @Test
    public void aDesignReplacesTodaysBanner() {
        DecorTest t = test(DecorTest.BannerChoice.DESIGN, Banners.Design.BIRTHDAY, DecorTest.LightsChoice.REAL);
        assertTrue(t.changesAnything());
        Banners.Shown s = DecorTest.banner(t, NOW, REAL);
        assertEquals(Banners.Design.BIRTHDAY, s.design);
        assertEquals("Happy Birthday, Alex!", s.line1);
        assertEquals("Have a lovely day", s.line2);
        assertNull(s.dayId);
        // Also on a day with no banner of its own.
        assertEquals(Banners.Design.BIRTHDAY, DecorTest.banner(t, NOW, null).design);
    }

    @Test
    public void noBannerHidesTodays() {
        DecorTest t = test(DecorTest.BannerChoice.NONE, null, DecorTest.LightsChoice.REAL);
        assertTrue(t.changesAnything());
        assertNull(DecorTest.banner(t, NOW, REAL));
    }

    @Test
    public void lightsForcedOnOrOff() {
        DecorTest on = test(DecorTest.BannerChoice.REAL, null, DecorTest.LightsChoice.ON);
        DecorTest off = test(DecorTest.BannerChoice.REAL, null, DecorTest.LightsChoice.OFF);
        assertTrue(DecorTest.lightsOn(on, NOW, false));
        assertFalse(DecorTest.lightsOn(off, NOW, true));
    }

    @Test
    public void anEndedTestShowsTheRealThings() {
        DecorTest t = test(DecorTest.BannerChoice.NONE, null, DecorTest.LightsChoice.ON);
        assertTrue(t.active(LATER - 1));
        assertFalse(t.active(LATER));
        assertSame(REAL, DecorTest.banner(t, LATER, REAL));
        assertFalse(DecorTest.lightsOn(t, LATER, false));
        assertSame(REAL, DecorTest.banner(null, NOW, REAL));
        assertTrue(DecorTest.lightsOn(null, NOW, true));
    }

    @Test
    public void aDesignWithoutTextGetsASampleLine() {
        DecorTest t = new DecorTest(DecorTest.BannerChoice.DESIGN, Banners.Design.SIMPLE, "  ", null,
                DecorTest.LightsChoice.REAL, LATER);
        assertEquals(DecorTest.SAMPLE_LINE1, t.line1);
        assertEquals("", t.line2);
    }

    @Test
    public void aDesignChoiceWithoutADesignIsReal() {
        DecorTest t = new DecorTest(DecorTest.BannerChoice.DESIGN, null, "x", "y", null, LATER);
        assertEquals(DecorTest.BannerChoice.REAL, t.banner);
        assertEquals(DecorTest.LightsChoice.REAL, t.lights);
        assertEquals("", t.line1);
        assertFalse(t.changesAnything());
    }

    @Test
    public void linesAreCutToTheBannersLimits() {
        String longLine = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        DecorTest t = new DecorTest(DecorTest.BannerChoice.DESIGN, Banners.Design.SIMPLE, longLine, longLine + longLine,
                DecorTest.LightsChoice.REAL, LATER);
        assertTrue(t.line1.length() <= Banners.LINE1_MAX);
        assertTrue(t.line2.length() <= Banners.LINE2_MAX);
    }

    @Test
    public void savedChoicesParseSafely() {
        assertEquals(DecorTest.LightsChoice.ON,
                DecorTest.parse(DecorTest.LightsChoice.class, "ON", DecorTest.LightsChoice.REAL));
        assertEquals(DecorTest.LightsChoice.REAL,
                DecorTest.parse(DecorTest.LightsChoice.class, "bogus", DecorTest.LightsChoice.REAL));
        assertEquals(DecorTest.BannerChoice.REAL,
                DecorTest.parse(DecorTest.BannerChoice.class, null, DecorTest.BannerChoice.REAL));
    }
}
