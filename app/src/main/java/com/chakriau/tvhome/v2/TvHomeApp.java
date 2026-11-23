package com.chakriau.tvhome.v2;

import android.app.Application;

import com.chakriau.tvhome.v2.backup.Backup;
import com.chakriau.tvhome.v2.safe.CrashLog;

/**
 * The app's process. Two things happen before anything else: crashes start being recorded (safe mode,
 * Requirements.md 5.12), then a restore staged from the phone page is put in place (5.11), before any setting has
 * been read.
 */
public final class TvHomeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        Backup.applyPending(this);
    }
}
