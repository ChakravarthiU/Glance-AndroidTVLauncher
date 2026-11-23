package com.chakriau.tvhome.v2.settings;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.safe.CrashLog;
import com.chakriau.tvhome.v2.system.NetworkStatus;
import com.chakriau.tvhome.v2.system.TopBar;
import com.chakriau.tvhome.v2.system.SystemScreens;
import com.chakriau.tvhome.v2.system.TopBarStore;

/**
 * Settings › System (your content): what the Settings button opens, the Wi-Fi name permission, default Home app,
 * the TV's settings, and the version. The theme and the top-button style are on Settings › Theme.
 */
final class SystemPage implements SettingsPage {

    static final int WIFI_NAME_REQUEST = 72;

    private final Activity activity;
    private View first;
    private View pressRow, wifiRow, problemRow;

    SystemPage(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String title() {
        return "System";
    }

    @Override
    public View createView(Ui ui) {
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("System", "How the launcher fits into the TV."));

        TopBarStore bar = new TopBarStore(activity);
        TopBar.SettingsPress[] presses = TopBar.SettingsPress.values();
        String[] pressNames = new String[presses.length];
        for (int i = 0; i < presses.length; i++) pressNames[i] = "Press: " + presses[i].label;
        pressRow = add(ui, box, ui.choiceRow("Settings button", "", pressNames, () -> bar.settingsPress().ordinal(),
                i -> bar.setSettingsPress(presses[i]), this::refresh), 48);
        first = pressRow;
        wifiRow = add(ui, box, ui.settingRow("Show the Wi-Fi name", "Android shares the Wi-Fi name only with apps "
                + "allowed to use location", "", v -> askWifiName()), 12);
        refresh();
        add(ui, box, ui.settingRow("Default Home app", "Choose which launcher the Home button opens", null,
                v -> openHomeSettings()), 12);
        add(ui, box, ui.settingRow("TV settings", "The TV's own system settings", null,
                v -> SystemScreens.settings(activity)), 12);
        add(ui, box, ui.settingRow("About", "TV Home V2", version(), null), 12);
        // Safe mode and the last problem (Requirements.md 5.12). OK: try normal Home again, or forget the problem.
        problemRow = add(ui, box, ui.settingRow("Problems", "", "", v -> problemAction()), 12);
        refreshProblem();

        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        return scroll;
    }

    private static View add(Ui ui, LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(top)));
        return row;
    }

    @SuppressWarnings("deprecation")   // versionCode: getLongVersionCode() needs Android 9, the TV may be 8
    private String version() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return "Version " + info.versionName + " (build " + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "Version unknown";
        }
    }

    private void refreshProblem() {
        if (problemRow == null) return;
        boolean safe = CrashLog.safeMode(activity);
        CrashLog.Problem p = CrashLog.last(activity);
        if (safe) {
            note(problemRow, "Safe mode is on. OK: try normal Home again. The report is on the phone page (Backup).");
            set(problemRow, "Safe mode");
        } else if (p != null) {
            note(problemRow, p.when + ": " + p.headline() + ". OK forgets it; the report is on the phone page.");
            set(problemRow, "1 recorded");
        } else {
            note(problemRow, "Crashes are recorded here; after three in ten minutes Home starts in safe mode");
            set(problemRow, "None");
        }
    }

    private void problemAction() {
        if (CrashLog.safeMode(activity)) {
            CrashLog.leave(activity);
            toast("Normal Home is back when you go Home");
        } else if (CrashLog.last(activity) != null) {
            CrashLog.clear(activity);
            toast("Forgotten");
        }
        refreshProblem();
    }

    private void refresh() {
        refreshProblem();
        if (pressRow == null) return;
        TopBarStore bar = new TopBarStore(activity);
        Ui.refreshAll(pressRow);
        note(pressRow, bar.settingsPress().holdHint);
        set(wifiRow, NetworkStatus.nameAllowed(activity) ? "Allowed" : "Not allowed");
    }

    private static void set(View row, String value) {
        if (Ui.valueOf(row) != null) Ui.valueOf(row).setText(value);
    }

    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((android.widget.TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
        }
    }

    private void askWifiName() {
        if (NetworkStatus.nameAllowed(activity)) {
            toast("Already allowed. If Home still says Wi-Fi, turn on Location in the TV's settings.");
            return;
        }
        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, WIFI_NAME_REQUEST);
    }

    /** The permission dialog closed (SettingsActivity passes it on). */
    void onPermissionAnswer() {
        refresh();
    }

    private void openHomeSettings() {
        Intent home = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (activity.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            start(home);
        } else {
            // Most TVs have no Home app chooser; Scripts/Install-Package.ps1 -SetHome does it over ADB.
            toast("This TV has no Home app setting. Use Install-Package.ps1 -SetHome from a PC.");
        }
    }

    private void start(Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("That page could not be opened on this TV");
        }
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_LONG).show();
    }

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
    }
}
