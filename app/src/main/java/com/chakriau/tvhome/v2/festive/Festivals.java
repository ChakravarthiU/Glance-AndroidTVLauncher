package com.chakriau.tvhome.v2.festive;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The built-in festival calendar (Requirements.md 5.3): suggestions for special days ({@link SpecialDays}), which the
 * owner adds on the phone; a festival does nothing by itself. Lunar festivals move every year, so their days ship as
 * a table rather than being calculated; fixed-date ones repeat every year. Plain Java.
 *
 * <p>Each festival covers a short window rather than one day: festivals run over several days (Dhanteras to Bhai
 * Dooj, Navratri to Dussehra), and published dates differ by a day between regions and sources, so the window takes
 * in both. Lunar dates are from the Government of India's holiday lists for 2026 and 2027 and Indian holiday
 * calendars for 2028 to 2030; Eid moves a day with the moon sighting. Extend {@link #LAST_YEAR} and the table every
 * few years.
 */
public final class Festivals {

    /** The last year the table has lunar dates for. */
    public static final int LAST_YEAR = 2030;

    /** A festival: its id, name, greeting and wish (the banner's two lines), and its days. */
    public static final class Festival {
        public final String id;
        public final String name;
        public final String greeting;
        public final String wish;
        /** Lunar festivals: dated windows "from..to"; empty for fixed ones. */
        private final List<LocalDate[]> windows;
        /** Fixed festivals: the same days every year (a window may cross New Year). */
        private final MonthDay fixedFrom, fixedTo;

        Festival(String id, String name, String greeting, String wish, List<LocalDate[]> windows, MonthDay from,
                 MonthDay to) {
            this.id = id;
            this.name = name;
            this.greeting = greeting;
            this.wish = wish;
            this.windows = windows;
            this.fixedFrom = from;
            this.fixedTo = to;
        }

        public boolean fixed() {
            return fixedFrom != null;
        }

        /** Whether {@code d} falls on one of the festival's days. */
        public boolean on(LocalDate d) {
            if (fixed()) return inFixed(MonthDay.from(d));
            for (LocalDate[] w : windows) if (!d.isBefore(w[0]) && !d.isAfter(w[1])) return true;
            return false;
        }

        private boolean inFixed(MonthDay m) {
            if (!fixedFrom.isAfter(fixedTo)) return !m.isBefore(fixedFrom) && !m.isAfter(fixedTo);
            return !m.isBefore(fixedFrom) || !m.isAfter(fixedTo);   // crosses New Year (31 Dec to 1 Jan)
        }

        /** The first day of the festival's next window that ends on or after {@code from}; null past the table. */
        public LocalDate next(LocalDate from) {
            LocalDate[] w = window(from);
            return w == null ? null : w[0];
        }

        /** The festival's next window {first, last} that ends on or after {@code from}; null past the table. */
        public LocalDate[] window(LocalDate from) {
            if (fixed()) {
                for (int y = from.getYear() - 1; y <= from.getYear() + 1; y++) {
                    LocalDate start = fixedFrom.atYear(y);
                    LocalDate end = fixedFrom.isAfter(fixedTo) ? fixedTo.atYear(y + 1) : fixedTo.atYear(y);
                    if (!end.isBefore(from)) return new LocalDate[]{start, end};
                }
                return null;
            }
            for (LocalDate[] w : windows) if (!w[1].isBefore(from)) return new LocalDate[]{w[0], w[1]};
            return null;
        }
    }

    public static final List<Festival> ALL = Collections.unmodifiableList(Arrays.asList(
            lunar("diwali", "Diwali", "Happy Diwali", "Wishing you light and joy",
                    "2026-11-06..2026-11-11,2027-10-26..2027-10-31,2028-10-15..2028-10-19,"
                            + "2029-11-03..2029-11-07,2030-10-24..2030-10-28"),
            lunar("dussehra", "Navratri & Dussehra", "Happy Dussehra", "Victory of good over evil",
                    "2026-10-11..2026-10-20,2027-09-30..2027-10-09,2028-09-18..2028-09-27,"
                            + "2029-10-07..2029-10-16,2030-09-26..2030-10-06"),
            lunar("holi", "Holi", "Happy Holi", "Wishing you a colourful Holi",
                    "2026-03-03..2026-03-04,2027-03-21..2027-03-23,2028-03-09..2028-03-11,"
                            + "2029-02-27..2029-03-01,2030-03-18..2030-03-20"),
            fixed("pongal", "Pongal & Sankranti", "Happy Pongal", "Wishing you a bountiful harvest", "01-13", "01-16"),
            lunar("ganesh", "Ganesh Chaturthi", "Happy Ganesh Chaturthi", "May Ganesha bless your home",
                    "2026-09-14..2026-09-15,2027-09-03..2027-09-04,2028-08-23..2028-08-24,"
                            + "2029-09-11..2029-09-12,2030-08-31..2030-09-01"),
            lunar("onam", "Onam", "Happy Onam", "Wishing you joy and plenty",
                    "2026-08-25..2026-08-26,2027-09-11..2027-09-12,2028-08-31..2028-09-01,"
                            + "2029-08-21..2029-08-22,2030-09-07..2030-09-08"),
            lunar("ugadi", "Ugadi & Gudi Padwa", "Happy Ugadi", "Wishing you a joyful new year",
                    "2026-03-19..2026-03-19,2027-04-07..2027-04-07,2028-03-26..2028-03-27,"
                            + "2029-04-14..2029-04-14,2030-04-03..2030-04-04"),
            lunar("rakhi", "Raksha Bandhan", "Happy Raksha Bandhan", "Celebrating the bond of love",
                    "2026-08-28..2026-08-28,2027-08-16..2027-08-17,2028-08-04..2028-08-05,"
                            + "2029-08-23..2029-08-23,2030-08-12..2030-08-12"),
            lunar("janmashtami", "Janmashtami", "Happy Janmashtami", "May Krishna bless your home",
                    "2026-09-03..2026-09-04,2027-08-24..2027-08-25,2028-08-12..2028-08-13,"
                            + "2029-08-31..2029-09-01,2030-08-21..2030-08-21"),
            lunar("eid_fitr", "Eid al-Fitr", "Eid Mubarak", "Peace and blessings to all",
                    "2026-03-20..2026-03-21,2027-03-09..2027-03-10,2028-02-26..2028-02-27,"
                            + "2029-02-14..2029-02-15,2030-02-04..2030-02-05"),
            lunar("eid_adha", "Eid al-Adha", "Eid Mubarak", "Peace and blessings to all",
                    "2026-05-27..2026-05-28,2027-05-16..2027-05-17,2028-05-05..2028-05-06,"
                            + "2029-04-24..2029-04-25,2030-04-13..2030-04-14"),
            fixed("christmas", "Christmas", "Merry Christmas", "Peace and joy to all", "12-24", "12-25"),
            fixed("new_year", "New Year", "Happy New Year", "Wishing you a wonderful year", "12-31", "01-01"),
            fixed("republic", "Republic Day", "Happy Republic Day", "Jai Hind", "01-26", "01-26"),
            fixed("independence", "Independence Day", "Happy Independence Day", "Jai Hind", "08-15", "08-15")));

    private Festivals() {
    }

    public static Festival byId(String id) {
        for (Festival f : ALL) if (f.id.equals(id)) return f;
        return null;
    }

    // ---- Lights ---------------------------------------------------------------------------------------------------

    /** Settings › Theme › Festive lights. FESTIVAL_DAYS (the saved name) means the special days that want lights. */
    public enum LightsMode {
        OFF("Off"), ALWAYS("Always on"), FESTIVAL_DAYS("On special days");

        public final String label;

        LightsMode(String label) {
            this.label = label;
        }

        public LightsMode next() {
            LightsMode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static LightsMode parse(String s) {
            for (LightsMode m : values()) if (m.name().equals(s)) return m;
            return FESTIVAL_DAYS;
        }
    }

    private static Festival lunar(String id, String name, String greeting, String wish, String spans) {
        List<LocalDate[]> windows = new ArrayList<>();
        for (String span : spans.split(",")) {
            String[] p = span.split("\\.\\.");
            windows.add(new LocalDate[]{LocalDate.parse(p[0]), LocalDate.parse(p[1])});
        }
        return new Festival(id, name, greeting, wish, Collections.unmodifiableList(windows), null, null);
    }

    private static Festival fixed(String id, String name, String greeting, String wish, String from, String to) {
        return new Festival(id, name, greeting, wish, Collections.emptyList(), MonthDay.parse("--" + from),
                MonthDay.parse("--" + to));
    }
}
