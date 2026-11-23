package com.chakriau.tvhome.v2.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The launchable apps installed on the TV: every app with a TV (Leanback) launcher activity, plus phone-style apps
 * that only have a regular launcher activity. One entry per package, preferring the TV activity. Run off the main
 * thread.
 */
public final class AppCatalog {

    private static final String TAG = "TvHomeApps";

    private AppCatalog() {
    }

    /** Installed apps sorted by label, so a first run fills Home alphabetically. Never includes this launcher. */
    public static List<AppInfo> query(Context context) {
        PackageManager pm = context.getPackageManager();
        String me = context.getPackageName();
        Map<String, AppInfo> byPackage = new LinkedHashMap<>();
        add(pm, me, byPackage, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        add(pm, me, byPackage, Intent.CATEGORY_LAUNCHER, false);
        List<AppInfo> apps = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    private static void add(PackageManager pm, String me, Map<String, AppInfo> out, String category,
                            boolean leanback) {
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "app query failed for " + category + ": " + e);
            return;
        }
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || me.equals(ai.packageName) || out.containsKey(ai.packageName)) continue;
            String label;
            try {
                label = String.valueOf(ri.loadLabel(pm)).trim();
            } catch (RuntimeException e) {
                label = ai.packageName;   // an app with broken resources still gets a tile
            }
            if (label.isEmpty()) label = ai.packageName;
            out.put(ai.packageName, new AppInfo(new ComponentName(ai.packageName, ai.name), label, leanback));
        }
    }
}
