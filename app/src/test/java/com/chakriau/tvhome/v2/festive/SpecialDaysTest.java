package com.chakriau.tvhome.v2.festive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SpecialDaysTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static SpecialDays.Day day(String id, String from, String to, boolean yearly, Banners.Design design,
                                       boolean lights, String photo, long added) {
        return SpecialDays.make(id, "Day " + id, d(from), to == null ? null : d(to), yearly, design,
                design == null ? "" : "Hello " + id, "", lights,
                photo == null ? null : Arrays.asList(photo.split(",")), added);
    }

    @Test
    public void onceRangesAndYearlyDays() {
        SpecialDays.Day trip = day("a", "2026-12-01", "2026-12-05", false, null, true, null, 1);
        assertTrue(trip.on(d("2026-12-01")));
        assertTrue(trip.on(d("2026-12-05")));
        assertFalse(trip.on(d("2027-12-03")));
        assertEquals(d("2026-12-01"), trip.next(d("2026-12-03")));
        assertNull(trip.next(d("2026-12-06")));

        SpecialDays.Day bday = day("b", "2026-03-12", null, true, null, true, null, 1);
        assertTrue(bday.on(d("2031-03-12")));
        assertFalse(bday.on(d("2031-03-13")));
        assertEquals(d("2027-03-12"), bday.next(d("2026-03-13")));
        assertEquals("Every year on 12 Mar", bday.describeWhen());
        assertEquals("1 Dec 2026 to 5 Dec 2026", trip.describeWhen());
    }

    @Test
    public void yearlyRangeCrossesNewYearAndLeapDayFallsBack() {
        SpecialDays.Day ny = day("n", "2026-12-31", "2027-01-01", true, null, true, null, 1);
        assertTrue(ny.on(d("2029-12-31")));
        assertTrue(ny.on(d("2030-01-01")));
        assertFalse(ny.on(d("2030-01-02")));
        assertEquals("Every year, 31 Dec to 1 Jan", ny.describeWhen());

        SpecialDays.Day leap = day("l", "2028-02-29", null, true, null, true, null, 1);
        assertTrue(leap.on(d("2027-02-28")));
        assertFalse(leap.on(d("2028-02-28")));
        assertTrue(leap.on(d("2028-02-29")));
    }

    @Test
    public void badPartsMakeNoDay() {
        assertNull(SpecialDays.make("x", " ", d("2026-01-01"), null, false, null, "", "", true, null, 1));
        assertTrue(SpecialDays.make("x", "Bad ids", d("2026-01-01"), null, false, null, "", "", true,
                Arrays.asList("a\tb", "", "p1", "p1"), 1).photos.equals(Collections.singletonList("p1")));
        assertNull(SpecialDays.make("x", "No line", d("2026-01-01"), null, false, Banners.Design.SIMPLE, " ", "",
                true, null, 1));
        assertNull(SpecialDays.make("x", "Too long", d("2026-01-01"), d("2026-04-01"), true, null, "", "", true,
                null, 1));
        SpecialDays.Day swapped = SpecialDays.make("x", "Swap", d("2026-01-05"), d("2026-01-01"), false, null,
                "ignored", "", true, Collections.emptyList(), 1);
        assertEquals(d("2026-01-01"), swapped.from);
        assertEquals("", swapped.line1);   // no banner, no lines
        assertTrue(swapped.photos.isEmpty());
    }

    @Test
    public void newestBannerShowsAndLightsComeFromAnyDay() {
        List<SpecialDays.Day> days = Arrays.asList(
                day("old", "2026-11-06", "2026-11-11", false, Banners.Design.DIWALI, true, null, 1),
                day("new", "2026-11-08", null, true, Banners.Design.BIRTHDAY, false, null, 2),
                day("quiet", "2026-11-20", null, false, null, false, null, 3));
        assertEquals("Hello new", SpecialDays.banner(d("2026-11-08"), days).line1);
        assertEquals("Hello old", SpecialDays.banner(d("2026-11-09"), days).line1);
        assertNull(SpecialDays.banner(d("2026-11-20"), days));
        assertTrue(SpecialDays.lightsOn(Festivals.LightsMode.FESTIVAL_DAYS, d("2026-11-08"), days));
        assertFalse(SpecialDays.lightsOn(Festivals.LightsMode.FESTIVAL_DAYS, d("2026-11-20"), days));
        assertTrue(SpecialDays.lightsOn(Festivals.LightsMode.ALWAYS, d("2026-11-20"), days));
        assertFalse(SpecialDays.lightsOn(Festivals.LightsMode.OFF, d("2026-11-08"), days));
        assertEquals(d("2026-11-20"), SpecialDays.nextAfter(d("2026-11-11"), days));
    }

    @Test
    public void photosTakeTurnsInTheOrderAddedAndOnlyIfOnTheTv() {
        List<SpecialDays.Day> days = Arrays.asList(
                day("b", "2026-11-08", null, false, null, true, "p2,p4", 5),
                day("a", "2026-11-06", "2026-11-11", false, null, true, "p1,p3", 1),
                day("c", "2026-11-08", null, false, null, true, "gone", 9));
        List<String> onTv = Arrays.asList("p1", "p2", "p3", "p4");
        assertEquals(Arrays.asList("p1", "p3", "p2", "p4"), SpecialDays.photos(d("2026-11-08"), days, onTv));
        assertTrue(SpecialDays.photos(d("2026-11-20"), days, onTv).isEmpty());
        List<SpecialDays.Day> after = SpecialDays.withoutPhoto(days, "p2");
        assertEquals(Collections.singletonList("p4"), after.get(0).photos);
        assertEquals(Arrays.asList("p1", "p3"), after.get(1).photos);
    }

    @Test
    public void festivalsBecomeSuggestions() {
        SpecialDays.Day diwali = SpecialDays.suggestion(Festivals.byId("diwali"), d("2026-11-08"));
        assertEquals("Diwali", diwali.name);
        assertEquals(d("2026-11-06"), diwali.from);
        assertEquals(d("2026-11-11"), diwali.to);
        assertFalse(diwali.yearly);   // lunar: moves every year
        assertEquals(Banners.Design.DIWALI, diwali.design);
        assertEquals("Happy Diwali", diwali.line1);
        assertTrue(diwali.lights);
        SpecialDays.Day pongal = SpecialDays.suggestion(Festivals.byId("pongal"), d("2026-11-08"));
        assertTrue(pongal.yearly);    // fixed: same days every year
        assertEquals(d("2027-01-13"), pongal.from);
        assertNull(SpecialDays.suggestion(Festivals.byId("holi"), d("2031-01-01")));
    }

    @Test
    public void savesAndLoadsAndSkipsBrokenLines() {
        List<SpecialDays.Day> days = Arrays.asList(
                SpecialDays.make("a1", "Amma's\tbirthday", d("2026-03-12"), null, true, Banners.Design.BIRTHDAY,
                        "Happy Birthday, Amma!", "Love you", true, Arrays.asList("p1", "p2"), 10),
                SpecialDays.make("b2", "Trip", d("2026-12-01"), d("2026-12-05"), false, null, "", "", false, null,
                        20));
        String saved = SpecialDays.format(days);
        List<SpecialDays.Day> back = SpecialDays.parse(saved + "\nbroken\nx\ty\tbad-date\t\t\t\t\t\t\t\t");
        assertEquals(2, back.size());
        assertEquals(saved, SpecialDays.format(back));
        assertEquals("Amma's birthday", back.get(0).name);
        assertEquals(Arrays.asList("p1", "p2"), back.get(0).photos);
        assertTrue(back.get(1).photos.isEmpty());
        // A line saved with one photo (before several were allowed) still reads.
        assertEquals(Collections.singletonList("p9"), SpecialDays.parse(
                "z1\tOld\t2026-01-01\t2026-01-01\t0\t-\t1\tp9\t5\t\t").get(0).photos);
        assertEquals("Love you", back.get(0).line2);
        assertFalse(back.get(1).lights);
        assertNull(back.get(1).design);
        assertTrue(SpecialDays.parse(null).isEmpty());
    }

    @Test
    public void oldSettingsMoveIn() {
        String custom = "03-12 Amma's birthday\n02-29 Leap\nbad";
        String messages = String.join("\n",
                "m1\tbirthday\tYEARLY\t03-12\t100\tHappy Birthday, Amma!\tLove you",
                "m2\tsimple\tALWAYS\t-\t200\tWelcome\t",
                "m3\tdiwali\tFESTIVAL\tdiwali\t300\tShubh Deepavali\tFrom all of us",
                "m4\tsimple\tDATES\t2026-12-01..2026-12-05\t400\tHoliday\t");
        String rules = String.join("\n",
                "r1\tp1\tYEARLY\t03-12\t150",
                "r2\tp2\tFESTIVAL\tdiwali\t160",
                "r4\tp4\tFESTIVAL\tdiwali\t180",
                "r3\tp3\tDATES\t2027-01-10..2027-01-10\t170");
        List<SpecialDays.Day> days = SpecialDayMigration.migrate(custom, messages, rules, d("2026-11-08"), 999);
        assertEquals(5, days.size());   // birthday, leap day, Diwali, holiday, the 10 Jan photo; Always is dropped

        SpecialDays.Day amma = find(days, "Amma's birthday");
        assertTrue(amma.yearly);
        assertEquals("Happy Birthday, Amma!", amma.line1);
        assertEquals(Collections.singletonList("p1"), amma.photos);
        assertTrue(amma.lights);

        SpecialDays.Day diwali = find(days, "Diwali");
        assertEquals(d("2026-11-06"), diwali.from);
        assertEquals("Shubh Deepavali", diwali.line1);
        assertEquals(Arrays.asList("p2", "p4"), diwali.photos);
        assertTrue(diwali.lights);

        SpecialDays.Day holiday = find(days, "Holiday");
        assertFalse(holiday.lights);   // dated messages never lit the lights
        assertTrue(find(days, "Leap").on(d("2027-02-28")));
        assertEquals(Collections.singletonList("p3"), find(days, "Wallpaper").photos);

        assertTrue(SpecialDayMigration.migrate(null, null, null, d("2026-11-08"), 1).isEmpty());
        assertEquals(Collections.emptyList(), SpecialDayMigration.migrate("", "", "", d("2026-11-08"), 1));
    }

    private static SpecialDays.Day find(List<SpecialDays.Day> days, String name) {
        for (SpecialDays.Day x : days) if (x.name.equals(name)) return x;
        throw new AssertionError("no " + name);
    }
}
