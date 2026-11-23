package com.chakriau.tvhome.v2.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * Calls back once a minute, and when the TV's time or time zone changes, while started. Themes start it while Home
 * is visible and stop it when Home hides (Requirements.md principle 4): the clock, special days starting or ending,
 * and the sun and moon all move on it.
 */
public final class MinuteTicker {

    private final Context context;
    private final Runnable onMinute;
    private boolean running;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            onMinute.run();
        }
    };

    public MinuteTicker(Context context, Runnable onMinute) {
        this.context = context;
        this.onMinute = onMinute;
    }

    public void start() {
        if (running) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, f);
        }
        running = true;
    }

    public void stop() {
        if (!running) return;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Not registered.
        }
        running = false;
    }

    public boolean running() {
        return running;
    }
}
