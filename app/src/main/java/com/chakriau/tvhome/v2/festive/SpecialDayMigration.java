package com.chakriau.tvhome.v2.festive;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moving what was set before special days (8 Nov 2026) into them, so nothing the owner typed is lost: own dates
 * (birthdays), banner messages and special-day wallpapers become special days. Things for the same days merge into
 * one: a birthday, its banner message and its photo make one special day. A festival's message or photo becomes a
 * special day for the festival's next days (every year for fixed festivals). "Always" banner messages have no day,
 * so they are not kept. Plain Java over the old saved strings; {@link SpecialDayStore} runs it once.
 */
public final class SpecialDayMigration {

    private SpecialDayMigration() {
    }

    /** One group of old things that fall on the same days. */
    private static final class Group {
        String name, nameFrom = "";   // "date" beats "festival" beats "message"
        LocalDate from, to;
        boolean yearly, lights;
        Banners.Design design;
        String line1 = "", line2 = "";
        long bannerAdded = -1, added;
        final List<String> photos = new ArrayList<>();
    }

    /**
     * @param custom   the old own dates: "MM-dd name" lines
     * @param messages the old banner messages: id, design, when, value, added, line 1, line 2 (tab-separated)
     * @param rules    the old special-day wallpapers: id, photo, when, value, added (tab-separated)
     */
    public static List<SpecialDays.Day> migrate(String custom, String messages, String rules, LocalDate today,
                                                long now) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (String line : lines(custom)) {
            if (line.length() < 7 || line.charAt(5) != ' ') continue;
            Group g = group(groups, "YEARLY", line.substring(0, 5), today);
            if (g == null) continue;
            String name = SpecialDays.cleanName(line.substring(6));
            if (name.isEmpty()) continue;
            g.name = name;
            g.nameFrom = "date";
            g.lights = true;   // own dates were festival days: the lights came on
        }
        for (String line : lines(messages)) {
            String[] f = line.split("\t", -1);
            if (f.length < 7) continue;
            Banners.Design design = Banners.Design.parse(f[1]);
            long added = number(f[4]);
            Group g = group(groups, f[2], f[3], today);
            if (g == null || design == null || f[5].trim().isEmpty() || added < g.bannerAdded) continue;
            g.design = design;
            g.line1 = f[5];
            g.line2 = f[6];
            g.bannerAdded = added;
            g.added = Math.max(g.added, added);
            if (g.name == null) g.name = f[5];
        }
        for (String line : lines(rules)) {
            String[] f = line.split("\t", -1);
            if (f.length < 5 || f[1].isEmpty()) continue;
            long added = number(f[4]);
            Group g = group(groups, f[2], f[3], today);
            if (g == null || g.photos.contains(f[1])) continue;
            g.photos.add(f[1]);   // several photos for one day took turns before too
            if (g.name == null) g.name = "Wallpaper";
        }
        List<SpecialDays.Day> out = new ArrayList<>();
        int n = 0;
        for (Group g : groups.values()) {
            if (g.name == null) continue;
            SpecialDays.Day d = SpecialDays.make(Banners.newId(now, n++), g.name, g.from, g.to, g.yearly, g.design,
                    g.line1, g.line2, g.lights, g.photos, g.added > 0 ? g.added : now);
            if (d != null) out.add(d);
        }
        return out;
    }

    /** The group for an old "when" and its value; null for Always, an unknown festival or a broken date. */
    private static Group group(Map<String, Group> groups, String when, String value, LocalDate today) {
        LocalDate from, to;
        boolean yearly = false, festival = false;
        String festivalName = null;
        try {
            switch (when) {
                case "DATES": {
                    String[] p = value.split("\\.\\.");
                    from = LocalDate.parse(p[0]);
                    to = p.length > 1 && !p[1].isEmpty() ? LocalDate.parse(p[1]) : from;
                    if (to.isBefore(from)) {
                        LocalDate t = from;
                        from = to;
                        to = t;
                    }
                    break;
                }
                case "YEARLY": {
                    MonthDay md = MonthDay.parse("--" + value);
                    int y = today.getYear();
                    if (md.getMonthValue() == 2 && md.getDayOfMonth() == 29) while (!Year.isLeap(y)) y++;
                    from = md.atYear(y);
                    to = from;
                    yearly = true;
                    break;
                }
                case "FESTIVAL": {
                    Festivals.Festival f = Festivals.byId(value);
                    LocalDate[] w = f == null ? null : f.window(today);
                    if (w == null) return null;
                    from = w[0];
                    to = w[1];
                    yearly = f.fixed();
                    festival = true;
                    festivalName = f.name;
                    break;
                }
                default:
                    return null;
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
        String key = (yearly ? "Y" : "D") + from.toString().substring(yearly ? 5 : 0) + ".."
                + to.toString().substring(yearly ? 5 : 0);
        Group g = groups.get(key);
        if (g == null) {
            g = new Group();
            g.from = from;
            g.to = to;
            g.yearly = yearly;
            g.added = 0;
            groups.put(key, g);
        }
        if (festival) {
            g.lights = true;   // festival days lit the lights
            if (!"date".equals(g.nameFrom)) {
                g.name = festivalName;
                g.nameFrom = "festival";
            }
        }
        return g;
    }

    private static String[] lines(String s) {
        return s == null || s.isEmpty() ? new String[0] : s.split("\n");
    }

    private static long number(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
