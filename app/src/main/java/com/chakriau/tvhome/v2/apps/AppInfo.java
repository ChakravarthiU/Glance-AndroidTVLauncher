package com.chakriau.tvhome.v2.apps;

import android.content.ComponentName;

/** One launchable app as Home shows it. */
public final class AppInfo {
    public final String packageName;
    /** The activity to start: the app's TV (Leanback) launcher activity when it has one. */
    public final ComponentName component;
    public final String label;
    /** Whether the app declares a TV launcher activity (and so usually a TV banner). */
    public final boolean leanback;

    AppInfo(ComponentName component, String label, boolean leanback) {
        this.packageName = component.getPackageName();
        this.component = component;
        this.label = label;
        this.leanback = leanback;
    }
}
