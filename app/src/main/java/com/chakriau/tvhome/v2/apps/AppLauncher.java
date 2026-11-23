package com.chakriau.tvhome.v2.apps;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/** Opens apps, and their App info page (hold OK on Home, Requirements.md 5.6). */
public final class AppLauncher {

    private static final String TAG = "TvHomeApps";

    private AppLauncher() {
    }

    public static void open(Activity activity, AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(app.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "could not open " + app.component.flattenToShortString() + ": " + e);
            Toast.makeText(activity, app.label + " could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    public static void showAppInfo(Activity activity, AppInfo app) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (activity.getPackageManager().resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(activity, "This TV has no App info page", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(info);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, "App info could not be opened", Toast.LENGTH_SHORT).show();
        }
    }
}
