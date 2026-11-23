package com.chakriau.tvhome.v2.safe;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Records crashes and decides safe mode (Requirements.md 5.12; the rule is in {@link SafeMode}). Kept in
 * {@code safe_mode}, which is never backed up. The last problem (what failed, when, in which version, and where in
 * the code) is shown on the phone page's Backup tab and in Settings › System, so a problem on the TV can be
 * looked into without a laptop.
 */
public final class CrashLog {

    private static final String FILE = "safe_mode";

    /** The last problem recorded. */
    public static final class Problem {
        public final String when, version, text;

        Problem(String when, String version, String text) {
            this.when = when;
            this.version = version;
            this.text = text;
        }

        /** The first line: what went wrong. */
        public String headline() {
            int nl = text.indexOf('\n');
            return nl < 0 ? text : text.substring(0, nl);
        }
    }

    private CrashLog() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Records every crash before Android's own handler ends the process. Called first thing when the app starts;
     * everything here writes synchronously ({@code commit}), as the process is about to die.
     */
    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                recordCrash(app, thread, e);
            } catch (Throwable ignored) {
                // Never let recording a crash hide it.
            }
            if (previous != null) previous.uncaughtException(thread, e);
        });
    }

    static void recordCrash(Context c, Thread thread, Throwable e) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        long[] times = SafeMode.add(SafeMode.parse(p.getString("crashes", "")), now);
        SharedPreferences.Editor ed = p.edit()
                .putString("crashes", SafeMode.format(times))
                .putString("last_text", SafeMode.trim("The launcher stopped (" + thread.getName() + "): "
                        + stack(e)))
                .putLong("last_at", now)
                .putString("last_version", versionName(c));
        if (!p.getBoolean("safe", false) && SafeMode.shouldEnter(times, now)) {
            ed.putBoolean("safe", true).putLong("safe_version", versionCode(c));
        }
        ed.commit();
    }

    /** A problem that didn't crash the launcher (a restore that failed, say): shown, but not counted for safe mode. */
    public static void recordProblem(Context c, String what, Throwable e) {
        prefs(c).edit().putString("last_text", SafeMode.trim(what + ": " + stack(e)))
                .putLong("last_at", System.currentTimeMillis()).putString("last_version", versionName(c)).commit();
    }

    /** Whether Home should start in safe mode. A newer launcher than the one that crashed tries normal Home again. */
    public static boolean safeMode(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("safe", false)) return false;
        if (SafeMode.newerVersion(p.getLong("safe_version", 0), versionCode(c))) {
            leave(c);
            return false;
        }
        return true;
    }

    /** "Try normal Home again": out of safe mode, with the crash count started afresh (the report stays). */
    public static void leave(Context c) {
        prefs(c).edit().putBoolean("safe", false).remove("safe_version").remove("crashes").commit();
    }

    /** Forgets the last problem. */
    public static void clear(Context c) {
        prefs(c).edit().remove("last_text").remove("last_at").remove("last_version").commit();
    }

    /** The last problem, or null. */
    public static Problem last(Context c) {
        SharedPreferences p = prefs(c);
        String text = p.getString("last_text", null);
        if (text == null) return null;
        String when = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(p.getLong(
                "last_at", 0)));
        return new Problem(when, p.getString("last_version", "?"), text);
    }

    private static String stack(Throwable e) {
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    @SuppressWarnings("deprecation")   // versionCode: getLongVersionCode() needs Android 9, V2 runs from 8
    private static long versionCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static String versionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return "?";
        }
    }
}
