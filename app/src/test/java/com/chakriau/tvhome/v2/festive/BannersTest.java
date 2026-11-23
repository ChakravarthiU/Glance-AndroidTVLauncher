package com.chakriau.tvhome.v2.festive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BannersTest {

    @Test
    public void designsParseAndFestivalsPickOne() {
        assertEquals(Banners.Design.BIRTHDAY, Banners.Design.parse("birthday"));
        assertNull(Banners.Design.parse("nope"));
        assertEquals("banners/diwali.json", Banners.Design.DIWALI.asset());
        assertEquals(Banners.Design.DIWALI, Banners.designFor("diwali"));
        assertEquals(Banners.Design.CHRISTMAS, Banners.designFor("christmas"));
        assertEquals(Banners.Design.SIMPLE, Banners.designFor("holi"));
    }

    @Test
    public void textIsCleanedAndFitted() {
        assertEquals("one two", Banners.cleanLine(" one\ttwo\n", 40));
        assertEquals(40, Banners.cleanLine(String.format("%050d", 1), 40).length());
        assertEquals(1f, Banners.fitScale(500, 600), 0.001f);
        assertEquals(0.75f, Banners.fitScale(800, 600), 0.001f);
        assertEquals(0.4f, Banners.fitScale(5000, 600), 0.001f);
        String id = Banners.newId(1_700_000_000_000L, -77);
        assertTrue(id.matches("[0-9a-z]+"));
    }
}
