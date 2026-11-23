package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.festive.Banners;
import com.chakriau.tvhome.v2.festive.SpecialDayStore;
import com.chakriau.tvhome.v2.festive.SpecialDays;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.Themes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Settings › Special days (your content, every theme; Requirements.md 5.13, 8): whether today is a special day,
 * whether the lights are on, and the banner that shows today. The days themselves (name, dates, banner, lights,
 * wallpaper) are set on the phone (Settings › Phone), so a birthday surprise isn't listed on the TV for everyone to
 * see: the TV shows only today, and never names a day. Whether the active theme draws lights and a banner is on
 * Settings › Theme.
 */
final class SpecialDaysPage implements SettingsPage {

    private static final DateTimeFormatter NEXT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    private final Activity activity;
    private final SpecialDayStore store;
    private final ThemeParts parts;
    private Ui ui;
    private View page, todayRow, showingRow, phoneRow;

    SpecialDaysPage(Activity activity) {
        this.activity = activity;
        this.store = new SpecialDayStore(activity);
        this.parts = ThemeParts.active(activity);
    }

    @Override
    public String title() {
        return "Special days";
    }

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Special days",
                "Birthdays, festivals and other days, each with its own banner, lights and wallpaper, for every "
                        + "theme. Turn the lights and the banner on or off in Settings › Theme."));

        showingRow = add(box, ui.settingRow("Banner today", "", "", v -> previewToday()), 40);
        todayRow = add(box, ui.settingRow("Today", "", "", null), 12);

        box.addView(ui.caps("On the phone", 22, Ui.TEXT_2), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(48)));
        phoneRow = add(box, ui.settingRow("Add and change special days", "", null, null), 16);

        refresh();
        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        page = scroll;
        return scroll;
    }

    private View add(LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(top)));
        return row;
    }

    private static void set(View row, String value) {
        if (Ui.valueOf(row) != null) Ui.valueOf(row).setText(value);
    }

    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
        }
    }

    private void refresh() {
        if (ui == null) return;
        if (page != null) Ui.refreshAll(page);
        LocalDate today = LocalDate.now();
        List<SpecialDays.Day> days = store.days();

        boolean special = !SpecialDays.on(today, days).isEmpty();
        set(todayRow, special ? "Special day" : "Not a special day");
        String lightsNote = !parts.has(Themes.Part.FESTIVE_LIGHTS) ? parts.theme().name + " has no lights"
                : SpecialDays.lightsOn(parts.lights().mode(), today, days) ? "Lights on" : "Lights off";
        note(todayRow, lightsNote);

        Banners.Shown shown = SpecialDays.banner(today, days);
        set(showingRow, shown == null ? "Nothing" : shown.design.label);
        String drawn = !parts.has(Themes.Part.BANNER) ? parts.theme().name + " has no banner"
                : !parts.on(Themes.Part.BANNER) ? "The banner is off in Settings › Theme" : null;
        note(showingRow, shown == null ? (drawn != null ? drawn : "No banner for today")
                : shown.line1 + (shown.line2.isEmpty() ? "" : " · " + shown.line2) + "  ·  OK to preview"
                + (drawn != null ? "  ·  " + drawn : ""));

        LocalDate next = SpecialDays.nextAfter(today, days);
        int n = days.size();
        note(phoneRow, (n == 0 ? "None yet" : n + (n == 1 ? " special day" : " special days"))
                + (next == null ? "" : " · next on " + NEXT.format(next)) + " · Settings › Phone");
    }

    /** Today's banner where it really goes: Home, opened over Settings (Requirements.md 5.14). */
    private void previewToday() {
        Banners.Shown s = SpecialDays.banner(LocalDate.now(), store.days());
        if (s == null) {
            Toast.makeText(activity, "No banner today", Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsActivity.openHomePreview(activity);
    }

    // ---- SettingsPage ---------------------------------------------------------------------------------------------

    @Override
    public boolean focusFirst() {
        return showingRow != null && showingRow.requestFocus();
    }

    @Override
    public boolean onBack() {
        return false;
    }

    /** Also called when something changes from the phone. */
    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void onHidden() {
    }

    @Override
    public void onDestroy() {
        onHidden();
    }
}
