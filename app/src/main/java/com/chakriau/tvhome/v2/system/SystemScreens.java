package com.chakriau.tvhome.v2.system;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Opens the TV's own settings pages: the first of a list of actions that something real answers. Fire OS answers
 * the standard actions with a compliance stub that opens nothing ("CTSDummy"), which counts as missing.
 */
public final class SystemScreens {

    private static final String TAG = "TvHomeSystem";

    private SystemScreens() {
    }

    /** The TV's system settings (the Settings button). */
    public static void settings(Activity a) {
        open(a, "The TV's settings could not be opened", Settings.ACTION_SETTINGS);
    }

    /** Network settings (the Network button): Wi-Fi, else the wireless page, else all settings. */
    public static void network(Activity a) {
        open(a, "The TV's network settings could not be opened", Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS);
    }

    /** Starts the first action that resolves to a real screen; a toast if none does. */
    public static boolean open(Activity a, String failure, String... actions) {
        PackageManager pm = a.getPackageManager();
        for (String action : actions) {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r == null || r.activityInfo == null || r.activityInfo.name.contains("CTSDummy")) continue;
            try {
                a.startActivity(i);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, action + " failed: " + e);
            }
        }
        Toast.makeText(a, failure, Toast.LENGTH_SHORT).show();
        return false;
    }
}
