package com.chakriau.tvhome.v2;

import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.home.HomeTheme;
import com.chakriau.tvhome.v2.home.ThemeStore;
import com.chakriau.tvhome.v2.safe.CrashLog;
import com.chakriau.tvhome.v2.system.Inputs;
import com.chakriau.tvhome.v2.watching.WatchingRow;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home exactly as it looks, opened over Settings (Requirements.md 5.14): the active theme built the same way as
 * {@link MainActivity} builds it, with the real wallpaper, apps, Continue watching and decorations, so a test from the
 * phone page (sky, banner, lights) is seen where it really goes, not in a dialog. Settings keeps the phone page
 * running under it, so the phone can keep changing the test while this is on screen; each change calls
 * {@link #refresh()}.
 *
 * <p>Nothing opens from here: OK on an app only says it is a preview. Back (once Home is back at the apps bar), Menu or
 * the Settings button return to Settings; anything else that hides it closes it.
 */
public final class HomePreviewActivity extends Activity implements HomeTheme.Actions {

    private static final String TAG = "TvHome";

    /** The preview on screen, if any (main thread only). */
    private static WeakReference<HomePreviewActivity> showing = new WeakReference<>(null);
    /** Told when the preview goes, so Settings can stop the phone page if it is no longer on screen either. */
    private static Runnable onGone;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppArt art;
    private HomeTheme theme;
    private WatchingRow watching;
    private boolean started, gone;

    /** Opens the preview over {@code from}; {@code whenGone} runs on the main thread when it closes. */
    public static void open(Activity from, Runnable whenGone) {
        onGone = whenGone;
        HomePreviewActivity open = showing.get();
        if (open != null && !open.isFinishing()) {
            open.theme.refreshDecor();
            return;
        }
        from.startActivity(new Intent(from, HomePreviewActivity.class));
        from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** A test changed: the open preview shows it. False if no preview is open. */
    public static boolean refresh() {
        HomePreviewActivity open = showing.get();
        if (open == null || open.isFinishing() || !open.started) return false;
        open.theme.refreshDecor();
        return true;
    }

    /** Closes the preview, if open. */
    public static void close() {
        HomePreviewActivity open = showing.get();
        if (open != null && !open.isFinishing()) open.finish();
    }

    public static boolean isOpen() {
        HomePreviewActivity open = showing.get();
        return open != null && !open.isFinishing();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (CrashLog.safeMode(this)) {
            // Safe mode means the theme itself may be what crashes: don't build it here either.
            Toast.makeText(this, "Safe mode is on, so Home can't be previewed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        showing = new WeakReference<>(this);
        art = new AppArt(this);
        theme = ThemeStore.active(this).create();
        setContentView(theme.createView(this, this, art));
        watching = new WatchingRow(this, AppsModel.get(this), row -> {
            if (!isDestroyed()) theme.showWatching(row);
        });
        Toast.makeText(this, "Home preview · Back returns to Settings", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (theme == null) return;
        started = true;
        theme.onShown();
        watching.start();
        worker.execute(() -> {
            AppsModel.Snapshot s;
            try {
                s = AppsModel.get(this).current();
            } catch (RuntimeException e) {
                Log.w(TAG, "preview: reading apps failed", e);
                return;
            }
            main.post(() -> {
                if (!isDestroyed()) theme.showApps(s);
            });
        });
    }

    @Override
    protected void onStop() {
        if (started) {
            started = false;
            watching.stop();
            theme.onHidden();
        }
        super.onStop();
        // A preview isn't somewhere to come back to (the Home key, an input switch): it closes.
        if (!isChangingConfigurations()) {
            if (!isFinishing()) finish();
            goneNow();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Like Home: Back first scrolls back up to the apps bar; once there, it leaves the preview.
        if (theme == null || !theme.onBack()) finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (theme != null && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            art.clear();
            theme.onTrimMemory();
        }
    }

    @Override
    protected void onDestroy() {
        if (theme != null) {
            watching.shutdown();
            theme.onDestroy();
            art.shutdown();
        }
        worker.shutdownNow();
        if (showing.get() == this) showing = new WeakReference<>(null);
        goneNow();
        super.onDestroy();
    }

    private void goneNow() {
        if (gone) return;
        gone = true;
        Runnable r = onGone;
        onGone = null;
        if (r != null) r.run();
    }

    // ---- HomeTheme.Actions: nothing opens from the preview -----------------------------------------------------

    private void previewOnly() {
        Toast.makeText(this, "This is a preview: nothing opens here. Back returns to Settings.",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void open(AppInfo app) {
        previewOnly();
    }

    @Override
    public void showAppInfo(AppInfo app) {
        previewOnly();
    }

    @Override
    public void openSettings() {
        finish();
    }

    @Override
    public void openSystemSettings() {
        previewOnly();
    }

    @Override
    public void openNetworkSettings() {
        previewOnly();
    }

    @Override
    public void openInput(Inputs.Source source) {
        previewOnly();
    }

    @Override
    public void resume(WatchingRow.Card card) {
        previewOnly();
    }
}
