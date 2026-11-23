package com.chakriau.tvhome.v2.festive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class FestivalsTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    public void diwali2026RunsDhanterasToBhaiDooj() {
        Festivals.Festival diwali = Festivals.byId("diwali");
        assertFalse(diwali.on(d("2026-11-05")));
        assertTrue(diwali.on(d("2026-11-06")));
        assertTrue(diwali.on(d("2026-11-08")));
        assertTrue(diwali.on(d("2026-11-11")));
        assertFalse(diwali.on(d("2026-11-12")));
        assertEquals(d("2027-10-26"), diwali.next(d("2026-11-12")));
        assertEquals(d("2026-11-06"), diwali.next(d("2026-11-07")));   // still on: the current window
    }

    @Test
    public void lunarDatesStopAfterTheTableFixedOnesGoOn() {
        assertNull(Festivals.byId("holi").next(d("2031-01-01")));
        assertEquals(d("2031-12-24"), Festivals.byId("christmas").next(d("2031-01-01")));
        assertEquals(d("2031-01-13"), Festivals.byId("pongal").next(d("2031-01-01")));
    }

    @Test
    public void newYearCrossesTheYear() {
        Festivals.Festival ny = Festivals.byId("new_year");
        assertTrue(ny.on(d("2026-12-31")));
        assertTrue(ny.on(d("2027-01-01")));
        assertFalse(ny.on(d("2027-01-02")));
        assertFalse(ny.on(d("2026-12-30")));
        assertEquals(d("2026-12-31"), ny.next(d("2027-01-01")));   // the window that ends today
        assertEquals(d("2027-12-31"), ny.next(d("2027-01-02")));
    }

    @Test
    public void everyWindowIsInOrderAndInsideTheTable() {
        for (Festivals.Festival f : Festivals.ALL) {
            if (f.fixed()) continue;
            LocalDate prev = null;
            for (LocalDate day = d("2026-01-01"); day.getYear() <= Festivals.LAST_YEAR; day = day.plusDays(1)) {
                LocalDate start = f.next(day);
                if (prev != null && start != null) assertFalse(f.id, start.isBefore(prev));
                if (start != null) prev = start;
            }
            assertTrue(f.id, prev != null && prev.getYear() == Festivals.LAST_YEAR);
        }
    }

    @Test
    public void windowsGiveBothEnds() {
        LocalDate[] w = Festivals.byId("diwali").window(d("2026-11-08"));
        assertEquals(d("2026-11-06"), w[0]);
        assertEquals(d("2026-11-11"), w[1]);
        w = Festivals.byId("new_year").window(d("2027-01-02"));
        assertEquals(d("2027-12-31"), w[0]);
        assertEquals(d("2028-01-01"), w[1]);
        assertNull(Festivals.byId("holi").window(d("2031-01-01")));
    }

    @Test
    public void lightsModeParsesAndCycles() {
        assertEquals(Festivals.LightsMode.FESTIVAL_DAYS, Festivals.LightsMode.parse(null));
        assertEquals(Festivals.LightsMode.OFF, Festivals.LightsMode.FESTIVAL_DAYS.next());
        assertEquals("On special days", Festivals.LightsMode.FESTIVAL_DAYS.label);
    }
}
