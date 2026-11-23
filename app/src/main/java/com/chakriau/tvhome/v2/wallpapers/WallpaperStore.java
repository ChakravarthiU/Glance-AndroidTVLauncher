package com.chakriau.tvhome.v2.wallpapers;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wallpaper settings (Requirements.md 5.1, global data: every theme uses them): on/off, which photos are ticked,
 * rotation interval, order, cross-fade, the upload PIN, and which photo is on screen since when.
 */
public final class WallpaperStore {

    private static final String FILE = "wallpapers";

    private final SharedPreferences prefs;

    public WallpaperStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean enabled() {
        return prefs.getBoolean("enabled", true);
    }

    public void setEnabled(boolean on) {
        prefs.edit().putBoolean("enabled", on).apply();
    }

    public int intervalMinutes() {
        return Rotation.validInterval(prefs.getInt("interval", Rotation.DEFAULT_INTERVAL));
    }

    public void setIntervalMinutes(int minutes) {
        prefs.edit().putInt("interval", Rotation.validInterval(minutes)).apply();
    }

    public boolean shuffle() {
        return prefs.getBoolean("shuffle", false);
    }

    public void setShuffle(boolean on) {
        prefs.edit().putBoolean("shuffle", on).apply();
    }

    public boolean crossFade() {
        return prefs.getBoolean("crossfade", true);
    }

    public void setCrossFade(boolean on) {
        prefs.edit().putBoolean("crossfade", on).apply();
    }

    /** The ticked photo ids (in no particular order; Home uses the photo list's order). */
    public Set<String> ticked() {
        String s = prefs.getString("ticked", "");
        Set<String> out = new LinkedHashSet<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    public void setTicked(Set<String> ids) {
        prefs.edit().putString("ticked", String.join(",", ids)).apply();
    }

    /** The ticked ids that still exist, in the photo list's order. */
    public List<String> tickedInOrder(List<String> photos) {
        Set<String> t = ticked();
        List<String> out = new ArrayList<>();
        for (String id : photos) if (t.contains(id)) out.add(id);
        return out;
    }

    public String current() {
        return prefs.getString("current", null);
    }

    public long shownAt() {
        return prefs.getLong("shownAt", 0);
    }

    public void setCurrent(String id, long shownAt) {
        prefs.edit().putString("current", id).putLong("shownAt", shownAt).apply();
    }

    /** The upload PIN, made the first time it is needed. */
    public synchronized String pin() {
        String pin = prefs.getString("pin", null);
        if (pin == null) {
            pin = PinGuard.newPin(new SecureRandom());
            prefs.edit().putString("pin", pin).apply();
        }
        return pin;
    }

    public synchronized String newPin() {
        String pin = PinGuard.newPin(new SecureRandom());
        prefs.edit().putString("pin", pin).apply();
        return pin;
    }
}
