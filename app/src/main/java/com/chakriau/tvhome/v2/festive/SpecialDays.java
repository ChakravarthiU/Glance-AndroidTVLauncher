package com.chakriau.tvhome.v2.festive;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Special days (Requirements.md 5.13, 6.5; 8 Nov 2026): everything for a day in one place. Each day has a name, a
 * date or a range of dates (once, or every year), and what Home does on it: a banner (a design and two lines), the
 * festive lights, and wallpaper photos. The built-in festivals ({@link Festivals}) are only suggestions that fill in a
 * new special day; nothing happens on a festival unless it is added. Plain Java.
 */
public final class SpecialDays {

    public static final int NAME_MAX = 40;
    /** A yearly day may last up to this many days (Navratri to Dussehra is ten). */
    public static final int YEARLY_MAX_DAYS = 60;

    /** One special day, as set on the phone. */
    public static final class Day {
        public final String id;
        public final String name;
        /** First and last day; with {@link #yearly}, only their day and month count (the range may cross New Year). */
        public final LocalDate from, to;
        public final boolean yearly;
        /** The banner's design, or null for no banner. */
        public final Banners.Design design;
        public final String line1, line2;
        public final boolean lights;
        /** The day's own wallpaper photos (they take turns); empty for the usual wallpaper from Settings. */
        public final List<String> photos;
        /** When it was saved: among days on the same date, the newest banner shows. */
        public final long added;

        Day(String id, String name, LocalDate from, LocalDate to, boolean yearly, Banners.Design design, String line1,
            String line2, boolean lights, List<String> photos, long added) {
            this.id = id;
            this.name = name;
            this.from = from;
            this.to = to;
            this.yearly = yearly;
            this.design = design;
            this.line1 = line1;
            this.line2 = line2;
            this.lights = lights;
            this.photos = Collections.unmodifiableList(photos);
            this.added = added;
        }

        /** Whether {@code d} is one of its days. 29 February counts as the 28th in other years. */
        public boolean on(LocalDate d) {
            if (!yearly) return !d.isBefore(from) && !d.isAfter(to);
            long span = ChronoUnit.DAYS.between(from, to);
            for (int y = d.getYear() - 1; y <= d.getYear(); y++) {
                LocalDate start = inYear(from, y);
                if (!d.isBefore(start) && !d.isAfter(start.plusDays(span))) return true;
            }
            return false;
        }

        /** The first day of its next run that ends on or after {@code d}; null when it is over for good. */
        public LocalDate next(LocalDate d) {
            if (!yearly) return to.isBefore(d) ? null : from;
            long span = ChronoUnit.DAYS.between(from, to);
            for (int y = d.getYear() - 1; y <= d.getYear() + 1; y++) {
                LocalDate start = inYear(from, y);
                if (!start.plusDays(span).isBefore(d)) return start;
            }
            return null;
        }

        public boolean banner() {
            return design != null;
        }

        public Banners.Shown shown() {
            return design == null ? null : new Banners.Shown(design, line1, line2, id);
        }

        /** "On 8 Nov 2026", "6 Nov 2026 to 11 Nov 2026", "Every year on 12 Mar", "Every year, 13 Jan to 16 Jan". */
        public String describeWhen() {
            if (!yearly) return from.equals(to) ? "On " + LONG.format(from) : LONG.format(from) + " to " + LONG.format(to);
            return from.equals(to) ? "Every year on " + SHORT.format(from)
                    : "Every year, " + SHORT.format(from) + " to " + SHORT.format(to);
        }

        /** The same day without one of its photos. */
        public Day withoutPhoto(String p) {
            List<String> rest = new ArrayList<>(photos);
            rest.remove(p);
            return new Day(id, name, from, to, yearly, design, line1, line2, lights, rest, added);
        }
    }

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private SpecialDays() {
    }

    /** {@code d}'s day and month in year {@code y}; 29 February becomes the 28th in other years. */
    static LocalDate inYear(LocalDate d, int y) {
        if (d.getMonthValue() == 2 && d.getDayOfMonth() == 29 && !java.time.Year.isLeap(y)) return LocalDate.of(y, 2, 28);
        return d.withYear(y);
    }

    /**
     * Builds a day from its parts (also from the phone page), or null if a part is missing or wrong: a name, a first
     * date, and the first line when there is a banner. {@code to} may be null (one day); dates the wrong way round are
     * swapped. A yearly day may last at most {@link #YEARLY_MAX_DAYS}.
     */
    public static Day make(String id, String name, LocalDate from, LocalDate to, boolean yearly,
                           Banners.Design design, String line1, String line2, boolean lights, List<String> photos,
                           long added) {
        String n = cleanName(name);
        if (id == null || id.isEmpty() || n.isEmpty() || from == null) return null;
        if (to == null) to = from;
        if (to.isBefore(from)) {
            LocalDate t = from;
            from = to;
            to = t;
        }
        if (yearly && ChronoUnit.DAYS.between(from, to) >= YEARLY_MAX_DAYS) return null;
        String l1 = design == null ? "" : Banners.cleanLine(line1, Banners.LINE1_MAX);
        String l2 = design == null ? "" : Banners.cleanLine(line2, Banners.LINE2_MAX);
        if (design != null && l1.isEmpty()) return null;
        List<String> p = new ArrayList<>();
        if (photos != null) {
            for (String x : photos) if (x != null && x.matches("[0-9A-Za-z_-]+") && !p.contains(x)) p.add(x);
        }
        return new Day(id, n, from, to, yearly, design, l1, l2, lights, p, added);
    }

    /** One line, trimmed, at most {@link #NAME_MAX} characters. */
    public static String cleanName(String s) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() > NAME_MAX ? t.substring(0, NAME_MAX).trim() : t;
    }

    // ---- What Home shows on a day ---------------------------------------------------------------------------------

    /** The days that cover {@code d}, oldest first. */
    public static List<Day> on(LocalDate d, List<Day> days) {
        List<Day> out = new ArrayList<>();
        for (Day x : sorted(days)) if (x.on(d)) out.add(x);
        return out;
    }

    /** The banner for {@code d}: the newest day on it with a banner; null for none. */
    public static Banners.Shown banner(LocalDate d, List<Day> days) {
        Day best = null;
        for (Day x : days) {
            if (x.banner() && x.on(d) && (best == null || x.added >= best.added)) best = x;
        }
        return best == null ? null : best.shown();
    }

    /** Whether a day on {@code d} wants the lights. */
    public static boolean lightsWanted(LocalDate d, List<Day> days) {
        for (Day x : days) if (x.lights && x.on(d)) return true;
        return false;
    }

    /** The theme's lights mode and the day together. */
    public static boolean lightsOn(Festivals.LightsMode mode, LocalDate d, List<Day> days) {
        switch (mode) {
            case ALWAYS:
                return true;
            case FESTIVAL_DAYS:
                return lightsWanted(d, days);
            default:
                return false;
        }
    }

    /** The wallpaper photos for {@code d}, in the order the days were added; only photos still on the TV. */
    public static List<String> photos(LocalDate d, List<Day> days, Collection<String> onTv) {
        List<String> out = new ArrayList<>();
        for (Day x : on(d, days)) {
            for (String p : x.photos) if (onTv.contains(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    /** The first day of the next special day after {@code d}; null when there is none. */
    public static LocalDate nextAfter(LocalDate d, List<Day> days) {
        LocalDate best = null;
        for (Day x : days) {
            LocalDate n = x.next(d.plusDays(1));
            if (n != null && n.isAfter(d) && (best == null || n.isBefore(best))) best = n;
        }
        return best;
    }

    private static List<Day> sorted(List<Day> days) {
        List<Day> s = new ArrayList<>(days);
        s.sort((a, b) -> Long.compare(a.added, b.added));
        return s;
    }

    /** Without the photo, from every day that had it (the photo was removed from the TV). */
    public static List<Day> withoutPhoto(List<Day> days, String photo) {
        List<Day> out = new ArrayList<>();
        for (Day x : days) out.add(x.photos.contains(photo) ? x.withoutPhoto(photo) : x);
        return out;
    }

    // ---- Festivals as suggestions ---------------------------------------------------------------------------------

    /**
     * A festival's next days, ready to fill in a new special day: its name, its dates (every year for fixed ones;
     * this time only for lunar ones, which move), its greeting on a matching banner, and the lights on. Null when the
     * table has no more dates for it.
     */
    public static Day suggestion(Festivals.Festival f, LocalDate today) {
        LocalDate[] w = f.window(today);
        if (w == null) return null;
        return new Day("suggestion-" + f.id, f.name, w[0], w[1], f.fixed(), Banners.designFor(f.id), f.greeting,
                f.wish, true, Collections.emptyList(), 0);
    }

    // ---- Saving ---------------------------------------------------------------------------------------------------

    /** One day per line: id, name, from, to, yearly, design, lights, photos (comma-separated, "-" for none),
     * added, line 1, line 2 (tab-separated). */
    public static String format(List<Day> days) {
        StringBuilder b = new StringBuilder();
        for (Day x : days) {
            if (b.length() > 0) b.append('\n');
            b.append(x.id).append('\t').append(cleanName(x.name)).append('\t').append(x.from).append('\t')
                    .append(x.to).append('\t').append(x.yearly ? 1 : 0).append('\t')
                    .append(x.design == null ? "-" : x.design.id).append('\t').append(x.lights ? 1 : 0).append('\t')
                    .append(x.photos.isEmpty() ? "-" : String.join(",", x.photos)).append('\t').append(x.added).append('\t')
                    .append(Banners.cleanLine(x.line1, Banners.LINE1_MAX)).append('\t')
                    .append(Banners.cleanLine(x.line2, Banners.LINE2_MAX));
        }
        return b.toString();
    }

    public static List<Day> parse(String saved) {
        List<Day> out = new ArrayList<>();
        if (saved == null || saved.isEmpty()) return out;
        for (String line : saved.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 11) continue;
            try {
                Day x = make(f[0], f[1], LocalDate.parse(f[2]), LocalDate.parse(f[3]), "1".equals(f[4]),
                        Banners.Design.parse(f[5]), f[9], f[10], "1".equals(f[6]), "-".equals(f[7]) ? null : Arrays.asList(f[7].split(",")),
                        Long.parseLong(f[8]));
                if (x != null) out.add(x);
            } catch (DateTimeParseException | NumberFormatException ignored) {
                // Skip a broken line.
            }
        }
        return out;
    }
}
