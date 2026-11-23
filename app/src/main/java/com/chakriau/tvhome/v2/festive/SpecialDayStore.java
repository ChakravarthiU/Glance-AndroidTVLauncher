package com.chakriau.tvhome.v2.festive;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The special days (global: every theme uses them, Requirements.md section 7). The first time it is read, what was set
 * before special days (own dates, banner messages, special-day wallpapers) moves in ({@link SpecialDayMigration}).
 */
public final class SpecialDayStore {

    private static final String KEY = "days";

    private final Context app;
    private final SharedPreferences prefs;

    public SpecialDayStore(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences("special_days", Context.MODE_PRIVATE);
    }

    public List<SpecialDays.Day> days() {
        synchronized (SpecialDayStore.class) {
            if (!prefs.contains(KEY)) migrate();
            return SpecialDays.parse(prefs.getString(KEY, ""));
        }
    }

    public SpecialDays.Day byId(String id) {
        for (SpecialDays.Day d : days()) if (d.id.equals(id)) return d;
        return null;
    }

    /** Adds a day, or replaces the one with the same id. */
    public void save(SpecialDays.Day day) {
        synchronized (SpecialDayStore.class) {
            List<SpecialDays.Day> list = new ArrayList<>();
            for (SpecialDays.Day d : days()) if (!d.id.equals(day.id)) list.add(d);
            list.add(day);
            write(list);
        }
    }

    public boolean delete(String id) {
        synchronized (SpecialDayStore.class) {
            List<SpecialDays.Day> list = new ArrayList<>();
            boolean found = false;
            for (SpecialDays.Day d : days()) {
                if (d.id.equals(id)) found = true;
                else list.add(d);
            }
            if (found) write(list);
            return found;
        }
    }

    /** A photo was removed from the TV: the days that used it keep everything else, and their other photos. */
    public void forgetPhoto(String photo) {
        synchronized (SpecialDayStore.class) {
            write(SpecialDays.withoutPhoto(days(), photo));
        }
    }

    private void write(List<SpecialDays.Day> list) {
        prefs.edit().putString(KEY, SpecialDays.format(list)).commit();
    }

    /** Once: the old own dates, banner messages and special-day wallpapers become special days. */
    private void migrate() {
        SharedPreferences festive = app.getSharedPreferences("festive", Context.MODE_PRIVATE);
        SharedPreferences banners = app.getSharedPreferences("banners", Context.MODE_PRIVATE);
        SharedPreferences walls = app.getSharedPreferences("wallpapers", Context.MODE_PRIVATE);
        List<SpecialDays.Day> moved = SpecialDayMigration.migrate(festive.getString("custom", null),
                banners.getString("messages", null), walls.getString("day_rules", null), LocalDate.now(),
                System.currentTimeMillis());
        write(moved);
        festive.edit().remove("custom").remove("unticked").commit();
        banners.edit().remove("messages").remove("festival_greeting").commit();
        walls.edit().remove("day_rules").remove("special_days").commit();
    }
}
