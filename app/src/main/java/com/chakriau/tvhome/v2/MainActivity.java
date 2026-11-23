package com.chakriau.tvhome.v2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppLauncher;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.home.HomeTheme;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.ThemeStore;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.safe.CrashLog;
import com.chakriau.tvhome.v2.safe.SafeHome;
import com.chakriau.tvhome.v2.settings.SettingsActivity;
import com.chakriau.tvhome.v2.system.Inputs;
import com.chakriau.tvhome.v2.system.SystemScreens;
import com.chakriau.tvhome.v2.watching.Resume;
import com.chakriau.tvhome.v2.watching.WatchNext;
import com.chakriau.tvhome.v2.watching.WatchingRow;
import com.chakriau.tvhome.v2.watching.WatchingStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Home activity. Kept small on purpose (V1's 3,700-line MainActivity is what to avoid): it wires the shared
 * apps model to the active theme and handles the activity's life. Looks live in {@link HomeTheme} classes.
 *
 * <p>After repeated crashes it shows {@link SafeHome} instead of the theme (safe mode, Requirements.md 5.12), and
 * leaves Continue watching alone.
 */
public final class MainActivity extends Activity implements HomeTheme.Actions {

    private static final String TAG = "TvHome";
    private static final int WATCH_NEXT_REQUEST = 91;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppsModel apps;
    private AppArt art;
    private HomeTheme theme;
    /** The theme {@link #theme} was built from; Home is rebuilt when Settings › Theme picks another. */
    private Themes.Theme themeChoice;
    /** Whether {@link #theme} is the safe-mode Home. */
    private boolean safe;
    private WatchingRow watching;
    private WatchingStore watchingStore;
    private boolean started;
    private volatile boolean appsStale = true;

    /** Settings changed the apps (or new ones were found): Home shows the change when it is next started. */
    private final AppsModel.Listener appsListener = () -> appsStale = true;

    /** Installs, updates and removals while Home is in the background mark the apps stale for the next return. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsStale = true;
            if (started) refreshApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apps = AppsModel.get(this);
        apps.addListener(appsListener);
        art = new AppArt(this);
        themeChoice = ThemeStore.active(this);
        safe = CrashLog.safeMode(this);
        theme = safe ? new SafeHome() : themeChoice.create();
        setContentView(theme.createView(this, this, art));
        watchingStore = new WatchingStore(this);
        if (!safe) {
            watching = new WatchingRow(this, apps, row -> {
                if (!isDestroyed()) theme.showWatching(row);
            });
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        // For the activity's whole life, not just while started, so changes made while an app is in front count.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Settings › Theme picked another theme, or safe mode started or ended: build Home again.
        if (CrashLog.safeMode(this) != safe || (!safe && ThemeStore.active(this) != themeChoice)) {
            recreate();
            return;
        }
        started = true;
        theme.onShown();
        if (appsStale) refreshApps();
        if (watching != null) {
            watching.start();
            askForWatchNextOnce();
        }
    }

    /**
     * Continue watching needs the permission to read other apps' Play Next items (Requirements.md 5.7): asked once,
     * the first time Home shows; after that only from Settings › Continue watching (or granted with adb).
     */
    private void askForWatchNextOnce() {
        if (!ThemeParts.active(this).shows(Themes.Part.CONTINUE_WATCHING) || watchingStore.askedPermission() || WatchNext.allowed(this)) return;
        watchingStore.setAskedPermission();
        try {
            requestPermissions(new String[]{WatchNext.PERMISSION}, WATCH_NEXT_REQUEST);
        } catch (RuntimeException e) {
            Log.w(TAG, "can't ask for " + WatchNext.PERMISSION + ": " + e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == WATCH_NEXT_REQUEST && watching != null) watching.reload();
    }

    @Override
    protected void onStop() {
        if (started) {   // not when onStart only asked to be recreated with another theme
            started = false;
            if (watching != null) watching.stop();
            theme.onHidden();
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button while Home is showing
        if (started && Intent.ACTION_MAIN.equals(intent.getAction())) theme.onHomePressedAgain();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // The remote's Menu key (or the Settings key some remotes have) opens the launcher's Settings, like a long
        // press on the top bar's Settings button.
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Home is the bottom of the stack: Back only moves around inside Home (back up to the apps bar).
        theme.onBack();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            art.clear();
            theme.onTrimMemory();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
            // Not registered.
        }
        if (watching != null) watching.shutdown();
        theme.onDestroy();
        apps.removeListener(appsListener);
        worker.shutdownNow();
        art.shutdown();
        super.onDestroy();
    }

    private void refreshApps() {
        appsStale = false;
        worker.execute(() -> {
            AppsModel.Snapshot snapshot;
            try {
                snapshot = apps.refresh();
                appsStale = false;   // this refresh's own "new apps found" notice is already shown
            } catch (RuntimeException e) {
                Log.w(TAG, "reading apps failed", e);
                appsStale = true;
                return;
            }
            main.post(() -> {
                if (isDestroyed()) return;
                theme.showApps(snapshot);
                if (watching != null) watching.reload();   // an app installed or removed can add or take away a card
            });
        });
    }

    // ---- HomeTheme.Actions -----------------------------------------------------------------------------------------

    @Override
    public void open(AppInfo app) {
        watchingStore.recordOpened(app.packageName);   // for Recently opened, when Watch Next can't be read
        AppLauncher.open(this, app);
    }

    @Override
    public void resume(WatchingRow.Card card) {
        Resume.open(this, card);
    }

    @Override
    public void showAppInfo(AppInfo app) {
        AppLauncher.showAppInfo(this, app);
    }

    @Override
    public void openSettings() {
        SettingsActivity.open(this);
    }

    @Override
    public void openSystemSettings() {
        SystemScreens.settings(this);
    }

    @Override
    public void openNetworkSettings() {
        SystemScreens.network(this);
    }

    @Override
    public void openInput(Inputs.Source source) {
        Inputs.open(this, source);
    }
}
