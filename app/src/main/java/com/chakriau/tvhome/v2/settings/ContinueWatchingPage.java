package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.watching.ContinueWatching;
import com.chakriau.tvhome.v2.watching.WatchNext;
import com.chakriau.tvhome.v2.watching.WatchingRow;
import com.chakriau.tvhome.v2.watching.WatchingStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings › Continue watching (your content; Requirements.md 5.7, 8): which Play Next items count, the permission
 * to read them, what Home shows now, and which apps may appear. Whether the active theme draws the row is on
 * Settings › Theme; this page says which.
 */
final class ContinueWatchingPage implements SettingsPage {

    static final int WATCH_NEXT_REQUEST = 73;

    private final Activity activity;
    private final WatchingStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Ui ui;
    private View first;
    private View onRow, modeRow, perAppRow, totalRow, permissionRow, nowRow;
    private LinearLayout appsBox;
    private String appsShown;

    ContinueWatchingPage(Activity activity) {
        this.activity = activity;
        this.store = new WatchingStore(activity);
    }

    @Override
    public String title() {
        return "Continue watching";
    }

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Continue watching",
                "A row under the Apps bar: what you were watching in each app, to resume in one press."));

        onRow = add(box, ui.settingRow("On Home", "", "", null), 40);
        ContinueWatching.Mode[] modes = ContinueWatching.Mode.values();
        String[] modeNames = new String[modes.length];
        for (int i = 0; i < modes.length; i++) modeNames[i] = modes[i].label;
        modeRow = add(box, ui.choiceRow("Show", "", modeNames, () -> store.mode().ordinal(),
                i -> store.setMode(modes[i]), this::refresh), 12);
        first = modeRow;
        int[] perApp = ContinueWatching.PER_APP_CHOICES, total = ContinueWatching.TOTAL_CHOICES;
        perAppRow = add(box, ui.choiceRow("Items from each app", "", numbers(perApp),
                () -> indexOf(perApp, store.perApp()), i -> store.setPerApp(perApp[i]), this::refresh), 12);
        totalRow = add(box, ui.choiceRow("Items in the row", "The most cards Continue watching shows",
                numbers(total), () -> indexOf(total, store.total()), i -> store.setTotal(total[i]), this::refresh),
                12);
        permissionRow = add(box, ui.settingRow("Read Play Next", "Lets the launcher see what you watch in other "
                + "apps. Without it, the row shows the apps you opened last", "", v -> askPermission()), 12);
        nowRow = add(box, ui.settingRow("Now showing", "", "", v -> refresh()), 12);

        TextView appsHead = ui.caps("Apps that may appear", 22, Ui.TEXT_2);
        box.addView(appsHead, ui.lp(Ui.WRAP, Ui.WRAP, ui.px(48)));
        appsBox = new LinearLayout(ui.context);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(appsBox, ui.lp(Ui.MATCH, Ui.WRAP));

        refresh();
        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        return scroll;
    }

    private View add(LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(top)));
        return row;
    }

    private static String[] numbers(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = String.valueOf(values[i]);
        return out;
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    private static void set(View row, String value) {
        if (Ui.valueOf(row) != null) Ui.valueOf(row).setText(value);
    }

    /** The note under a row (second text of its text column). */
    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
        }
    }

    /** What the page knows from Play Next, read off the main thread. */
    private static final class State {
        WatchNext.Status status;
        WatchingRow.Kind kind;
        int cards;
        /** Apps with items in Play Next (or turned off), newest first, with how many items each has. */
        final Map<AppInfo, Integer> apps = new LinkedHashMap<>();
    }

    private void refresh() {
        if (ui == null) return;
        for (View row : new View[]{modeRow, perAppRow, totalRow}) Ui.refreshAll(row);
        ThemeParts parts = ThemeParts.active(activity);
        set(onRow, parts.shows(Themes.Part.CONTINUE_WATCHING) ? "Shown" : "Not shown");
        note(onRow, !parts.has(Themes.Part.CONTINUE_WATCHING) ? parts.theme().name + " has no Continue watching row"
                : parts.on(Themes.Part.CONTINUE_WATCHING) ? "In " + parts.theme().name + " · turn off in Settings › Theme"
                : "Off in " + parts.theme().name + " · turn on in Settings › Theme");
        Ui.refreshAll(appsBox);
        ContinueWatching.Mode mode = store.mode();
        note(modeRow, mode.note);
        int perApp = store.perApp();
        note(perAppRow, perApp == 1 ? "Only the newest item from each app" : "Up to " + perApp
                + " items from one app, newest first");
        set(permissionRow, WatchNext.allowed(activity) ? "Allowed" : "Not allowed");
        worker.execute(() -> {
            State st = read();
            main.post(() -> {
                if (!activity.isDestroyed()) show(st);
            });
        });
    }

    private State read() {
        State st = new State();
        Map<String, AppInfo> installed = WatchingRow.installedApps(AppsModel.get(activity).current());
        WatchNext.Result wn = WatchNext.read(activity);
        st.status = wn.status;
        Set<String> blocked = store.blocked();
        Map<String, Integer> counts = wn.status == WatchNext.Status.READABLE
                ? ContinueWatching.countByApp(wn.items, installed.keySet(), activity.getPackageName())
                : new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) st.apps.put(installed.get(e.getKey()), e.getValue());
        for (String pkg : blocked) {
            AppInfo app = installed.get(pkg);
            if (app != null && !st.apps.containsKey(app)) st.apps.put(app, 0);
        }
        if (!ThemeParts.active(activity).shows(Themes.Part.CONTINUE_WATCHING)) {
            st.kind = WatchingRow.Kind.NONE;
            return st;
        }
        List<ContinueWatching.Item> picked = wn.status == WatchNext.Status.READABLE
                ? ContinueWatching.pick(wn.items, installed.keySet(), blocked, activity.getPackageName(),
                store.mode(), store.perApp(), store.total())
                : new ArrayList<>();
        if (!picked.isEmpty()) {
            st.kind = WatchingRow.Kind.CONTINUE_WATCHING;
            st.cards = picked.size();
        } else {
            st.cards = ContinueWatching.recentRow(store.recent(), installed.keySet(), blocked,
                    store.total()).size();
            st.kind = st.cards == 0 ? WatchingRow.Kind.NONE : WatchingRow.Kind.RECENTLY_OPENED;
        }
        return st;
    }

    private void show(State st) {
        if (st.kind == WatchingRow.Kind.NONE && !ThemeParts.active(activity).shows(Themes.Part.CONTINUE_WATCHING)) {
            set(nowRow, "Off");
            note(nowRow, "The row isn't shown in this theme");
        } else if (st.kind == WatchingRow.Kind.CONTINUE_WATCHING) {
            set(nowRow, st.cards + (st.cards == 1 ? " item" : " items"));
            note(nowRow, "From Play Next · OK to check again");
        } else if (st.kind == WatchingRow.Kind.RECENTLY_OPENED) {
            set(nowRow, "Recently opened");
            note(nowRow, (st.status == WatchNext.Status.READABLE ? "Nothing in Play Next to show"
                    : st.status == WatchNext.Status.NO_PERMISSION ? "Play Next can't be read without the permission"
                    : "This TV's Play Next can't be read") + " · OK to check again");
        } else {
            set(nowRow, "Nothing yet");
            note(nowRow, "Watch something in an app, or open apps from Home · OK to check again");
        }
        set(permissionRow, WatchNext.allowed(activity) ? "Allowed" : "Not allowed");
        showApps(st);
    }

    /** Rebuilds the app rows only when the list changed, so focus on them isn't lost. */
    private void showApps(State st) {
        StringBuilder key = new StringBuilder();
        for (Map.Entry<AppInfo, Integer> e : st.apps.entrySet()) {
            key.append(e.getKey().packageName).append('=').append(e.getValue()).append(',');
        }
        if (key.toString().equals(appsShown)) return;
        appsShown = key.toString();
        boolean hadFocus = appsBox.findFocus() != null;
        appsBox.removeAllViews();
        if (st.apps.isEmpty()) {
            appsBox.addView(ui.settingRow(st.status == WatchNext.Status.READABLE
                            ? "No app has anything in Play Next yet"
                            : "Apps are listed once Play Next can be read",
                    "Apps add what you watch as you watch it", null, null), ui.lp(ui.px(1100), Ui.WRAP, ui.px(12)));
        }
        for (Map.Entry<AppInfo, Integer> e : st.apps.entrySet()) {
            AppInfo app = e.getKey();
            int n = e.getValue();
            String note = n == 0 ? "Nothing in Play Next now" : n + (n == 1 ? " item" : " items") + " in Play Next";
            View row = ui.toggleRow(app.label, note, () -> !store.blocked().contains(app.packageName),
                    on -> store.setBlocked(app.packageName, !on), this::refresh);
            appsBox.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(12)));
        }
        if (hadFocus) {
            View top = appsBox.getChildCount() > 0 ? appsBox.getChildAt(0) : null;
            if (top != null && top.isFocusable()) top.requestFocus();
            else if (first != null) first.requestFocus();
        }
    }

    private void askPermission() {
        if (WatchNext.allowed(activity)) {
            Toast.makeText(activity, "Already allowed. It can be turned off in the TV's Apps settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        activity.requestPermissions(new String[]{WatchNext.PERMISSION}, WATCH_NEXT_REQUEST);
    }

    /** The permission dialog closed (SettingsActivity passes it on). */
    void onPermissionAnswer() {
        if (!WatchNext.allowed(activity)) {
            // A TV that never shows the dialog (or "Don't ask again") says no at once; the laptop can still grant it.
            Toast.makeText(activity, "Not allowed. From the laptop: adb shell pm grant " + activity.getPackageName()
                    + " " + WatchNext.PERMISSION, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    // ---- SettingsPage ---------------------------------------------------------------------------------------------

    @Override
    public boolean focusFirst() {
        return first != null && first.requestFocus();
    }

    @Override
    public boolean onBack() {
        return false;
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
    }
}
