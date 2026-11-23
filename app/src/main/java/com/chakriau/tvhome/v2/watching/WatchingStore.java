package com.chakriau.tvhome.v2.watching;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings › Continue watching (global: every theme uses it) and the Recently opened list: which Watch Next items
 * count, apps turned off, and whether Home has asked for the permission to read Play Next. Whether the row is drawn
 * is the theme's choice ({@code home/ThemeParts}).
 */
public final class WatchingStore {

    private final SharedPreferences prefs;

    public WatchingStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("continue_watching", Context.MODE_PRIVATE);
    }

    public ContinueWatching.Mode mode() {
        return ContinueWatching.Mode.parse(prefs.getString("mode", null));
    }

    public void setMode(ContinueWatching.Mode m) {
        prefs.edit().putString("mode", m.name()).apply();
    }

    /** At most this many items from one app. */
    public int perApp() {
        return ContinueWatching.validChoice(ContinueWatching.PER_APP_CHOICES, prefs.getInt("per_app", 0),
                ContinueWatching.PER_APP_DEFAULT);
    }

    public void setPerApp(int n) {
        prefs.edit().putInt("per_app", n).apply();
    }

    /** At most this many cards in the row. */
    public int total() {
        return ContinueWatching.validChoice(ContinueWatching.TOTAL_CHOICES, prefs.getInt("total", 0),
                ContinueWatching.TOTAL_DEFAULT);
    }

    public void setTotal(int n) {
        prefs.edit().putInt("total", n).apply();
    }

    /**
     * Apps the owner turned off for the row (a new app may appear until turned off). Always a new, changeable set:
     * an empty read-only set here made the first "Not shown" crash Settings.
     */
    public Set<String> blocked() {
        Set<String> s = prefs.getStringSet("blocked", null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    public void setBlocked(String pkg, boolean off) {
        Set<String> s = blocked();
        if (off) s.add(pkg);
        else s.remove(pkg);
        prefs.edit().putStringSet("blocked", s).apply();
    }

    /** Home asks once, at first run; later only from Settings. */
    public boolean askedPermission() {
        return prefs.getBoolean("asked_permission", false);
    }

    public void setAskedPermission() {
        prefs.edit().putBoolean("asked_permission", true).apply();
    }

    public synchronized List<ContinueWatching.Opened> recent() {
        return ContinueWatching.parseRecent(prefs.getString("recent", null));
    }

    /** An app was opened (or resumed) from Home. */
    public synchronized void recordOpened(String pkg) {
        // The largest row there can be, and a few spare for apps that are later uninstalled or turned off.
        int keep = ContinueWatching.TOTAL_CHOICES[ContinueWatching.TOTAL_CHOICES.length - 1] + 10;
        List<ContinueWatching.Opened> list = ContinueWatching.addRecent(recent(), pkg, System.currentTimeMillis(),
                keep);
        prefs.edit().putString("recent", ContinueWatching.formatRecent(list)).apply();
    }
}
