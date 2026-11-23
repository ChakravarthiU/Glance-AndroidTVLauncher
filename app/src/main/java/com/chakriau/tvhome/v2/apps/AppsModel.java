package com.chakriau.tvhome.v2.apps;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The installed apps and where each one lives, shared by Home and Settings (one per process, {@link #get}).
 * {@link #refresh()} re-reads the installed apps (new ones go to the end of Home); the move methods are how Settings
 * changes the lists. Every change is saved at once and reported to the listeners, so Home shows it when it returns.
 *
 * <p>All methods may block on the package manager: call them off the main thread.
 */
public final class AppsModel {

    /** Every installed app by state, each list in the owner's order. */
    public static final class Snapshot {
        public final List<AppInfo> appsBar;
        public final List<AppInfo> home;
        public final List<AppInfo> hidden;

        Snapshot(List<AppInfo> appsBar, List<AppInfo> home, List<AppInfo> hidden) {
            this.appsBar = appsBar;
            this.home = home;
            this.hidden = hidden;
        }

        public List<AppInfo> list(AppStates.State state) {
            switch (state) {
                case APPS_BAR:
                    return appsBar;
                case HOME:
                    return home;
                default:
                    return hidden;
            }
        }

        public boolean appsBarFull() {
            return appsBar.size() >= AppStates.APPS_BAR_MAX;
        }
    }

    /** Told after Settings changes the lists or new apps are found. Called on the thread that made the change. */
    public interface Listener {
        void onAppsChanged();
    }

    private static AppsModel instance;

    private final Context context;
    private final AppStore store;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private AppStates states;
    private Map<String, AppInfo> byPackage;

    public static synchronized AppsModel get(Context context) {
        if (instance == null) instance = new AppsModel(context.getApplicationContext());
        return instance;
    }

    private AppsModel(Context context) {
        this.context = context;
        this.store = new AppStore(context);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Re-reads the installed apps and returns where they live. */
    public Snapshot refresh() {
        boolean changed;
        Snapshot snapshot;
        synchronized (this) {
            if (states == null) states = store.load();
            List<AppInfo> installed = AppCatalog.query(context);
            Map<String, AppInfo> map = new HashMap<>();
            List<String> pkgs = new ArrayList<>(installed.size());
            for (AppInfo app : installed) {
                map.put(app.packageName, app);
                pkgs.add(app.packageName);
            }
            byPackage = map;
            changed = states.reconcile(pkgs);
            if (changed) store.save(states);
            snapshot = snapshotLocked();
        }
        if (changed) notifyListeners();
        return snapshot;
    }

    /** Where the apps live, without re-reading the package manager (refreshes the first time). */
    public Snapshot current() {
        synchronized (this) {
            if (byPackage != null) return snapshotLocked();
        }
        return refresh();
    }

    /** Moves an app to the end of another state. False if it is not allowed (for example the Apps bar is full). */
    public boolean moveTo(String pkg, AppStates.State target) {
        return change(s -> s.moveTo(pkg, target));
    }

    public boolean moveUp(String pkg) {
        return change(s -> s.moveUp(pkg));
    }

    public boolean moveDown(String pkg) {
        return change(s -> s.moveDown(pkg));
    }

    private interface Edit {
        boolean apply(AppStates states);
    }

    private boolean change(Edit edit) {
        current();   // makes sure the lists and installed apps are loaded
        synchronized (this) {
            if (!edit.apply(states)) return false;
            store.save(states);
        }
        notifyListeners();
        return true;
    }

    private void notifyListeners() {
        for (Listener l : listeners) l.onAppsChanged();
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(resolve(states.visible(AppStates.State.APPS_BAR)),
                resolve(states.visible(AppStates.State.HOME)),
                resolve(states.visible(AppStates.State.HIDDEN)));
    }

    private List<AppInfo> resolve(List<String> pkgs) {
        List<AppInfo> out = new ArrayList<>(pkgs.size());
        for (String p : pkgs) {
            AppInfo a = byPackage.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }
}
