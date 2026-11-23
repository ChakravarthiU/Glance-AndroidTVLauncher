package com.chakriau.tvhome.v2.home;

import android.app.Activity;
import android.view.View;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.system.Inputs;
import com.chakriau.tvhome.v2.watching.WatchingRow;

/**
 * A look for Home (Requirements.md section 7). A theme decides how things look, never what works: apps, their states
 * and what OK / hold OK do come from the shared code, through {@link Actions}. MainActivity stays small and talks to
 * the active theme only through this interface.
 */
public interface HomeTheme {

    /** What a theme can ask the launcher to do. */
    interface Actions {
        /** OK on an app. */
        void open(AppInfo app);

        /** Hold OK on an app: App info only (Requirements.md 5.6). */
        void showAppInfo(AppInfo app);

        /** Opens the launcher's own Settings. */
        void openSettings();

        /** Opens the TV's own system settings (Settings button, Requirements.md 5.9). */
        void openSystemSettings();

        /** Opens the TV's network settings (Network button). */
        void openNetworkSettings();

        /** Switches to an HDMI, AV or tuner source (Inputs button). */
        void openInput(Inputs.Source source);

        /** OK on a Continue watching card: resume the item in its app, or open the app (Requirements.md 5.7). */
        void resume(WatchingRow.Card card);
    }

    /** Builds the theme's views. Called once per activity. */
    View createView(Activity activity, Actions actions, AppArt art);

    /** The apps to show, in order. Called on the main thread whenever they change. */
    void showApps(AppsModel.Snapshot apps);

    /** The Continue watching row (or Recently opened, or nothing). Called on the main thread when it changes. */
    void showWatching(WatchingRow.Row row);

    /** Home became visible: start animations. */
    void onShown();

    /**
     * A test from the phone page started, changed or stopped while Home (or its preview) is showing: show the sky,
     * banner and lights again as they now should be (Requirements.md 5.14). Themes without decorations ignore it.
     */
    default void refreshDecor() {
    }

    /** Home was hidden: stop animations and free what can be rebuilt (Requirements.md principle 4). */
    void onHidden();

    /** Home pressed while Home is already showing: go back to the starting focus. */
    void onHomePressedAgain();

    /** Memory is short and Home is in the background: free what can be rebuilt (Requirements.md principle 4). */
    void onTrimMemory();

    /** The activity is going away. */
    void onDestroy();

    /** Back on Home. True if the theme used it (for example to scroll back up to the apps bar). */
    boolean onBack();
}
