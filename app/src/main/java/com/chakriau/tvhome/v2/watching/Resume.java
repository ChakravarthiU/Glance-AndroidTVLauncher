package com.chakriau.tvhome.v2.watching;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.chakriau.tvhome.v2.apps.AppLauncher;

import java.net.URISyntaxException;

/** OK on a Continue watching card: resume the item in its app, or open the app when that isn't possible. */
public final class Resume {

    private static final String TAG = "TvHomeWatching";

    private Resume() {
    }

    public static void open(Activity activity, WatchingRow.Card card) {
        new WatchingStore(activity).recordOpened(card.app.packageName);
        Intent intent = card.item == null ? null : resumeIntent(activity, card);
        if (intent != null) {
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "resume failed for " + card.app.packageName + ": " + e);
            }
        }
        AppLauncher.open(activity, card.app);
    }

    /**
     * The item's own intent, only if it opens an activity of the app that wrote the item: the intent text comes from
     * another app, and must not be able to start some other app's (or this launcher's) private screens.
     */
    private static Intent resumeIntent(Activity activity, WatchingRow.Card card) {
        String uri = card.item.intentUri;
        if (uri == null || uri.isEmpty()) return null;
        Intent intent;
        try {
            intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            Log.w(TAG, "bad resume intent from " + card.app.packageName);
            return null;
        }
        intent.setSelector(null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   // drops any URI-grant flags the text asked for
        // Without a target, look only in the item's own app (no chooser between apps that could play it).
        if (intent.getComponent() == null && intent.getPackage() == null) intent.setPackage(card.app.packageName);
        ResolveInfo ri = activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) ri = activity.getPackageManager().resolveActivity(intent, 0);
        ActivityInfo ai = ri == null ? null : ri.activityInfo;
        if (ai == null || !card.app.packageName.equals(ai.packageName) || !ai.exported) return null;
        intent.setComponent(new ComponentName(ai.packageName, ai.name));
        return intent;
    }
}
