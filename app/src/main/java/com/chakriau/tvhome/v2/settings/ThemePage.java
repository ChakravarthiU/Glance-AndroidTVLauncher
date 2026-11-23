package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chakriau.tvhome.v2.festive.Festivals;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.ThemeStore;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.system.TopBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Theme (Requirements.md 7, 8): which theme draws Home, and that theme's choices for its parts: the on/off
 * of each optional part it has (Continue watching, Banner, Festive lights, Eyes) and the top-button style. Saved per
 * theme. The content in the parts (apps, photos, messages) is on the "Your content" pages.
 */
final class ThemePage implements SettingsPage {

    /** Settings rebuilds its menu when another theme is chosen (its own pages come and go). */
    interface Host {
        void themeChanged();
    }

    private final Activity activity;
    private final Host host;
    private final ThemeParts parts;
    private Ui ui;
    private View page, first, lightsRow;

    ThemePage(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.parts = ThemeParts.active(activity);
    }

    @Override
    public String title() {
        return "Theme";
    }

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        Themes.Theme theme = parts.theme();
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Theme", "How Home looks. A theme draws your apps, photos and messages its own "
                + "way; switching keeps everything you set."));

        String[] names = new String[Themes.ALL.size()];
        for (int i = 0; i < names.length; i++) names[i] = Themes.ALL.get(i).name;
        first = add(box, ui.choiceRow("Theme", theme.description, names, () -> Themes.indexOf(theme), i -> {
            Themes.Theme picked = Themes.ALL.get(i);
            if (picked != theme) {
                ThemeStore.setActive(activity, picked);
                host.themeChanged();
            }
        }, null), 40);
        // Home exactly as it looks, over Settings: the place to check a change, or a test from the phone's Test tab.
        add(box, ui.settingRow("Preview Home", "See Home as it looks now, with any test from the phone's Test tab. "
                + "Back returns here.", "OK", v -> SettingsActivity.openHomePreview(activity)), 12);

        List<Themes.Part> optional = new ArrayList<>();
        for (Themes.Part p : Themes.Part.values()) if (p.optional && theme.has(p)) optional.add(p);
        if (!optional.isEmpty()) {
            box.addView(ui.caps("On Home", 22, Ui.TEXT_2), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(48)));
            int top = 16;
            for (Themes.Part p : optional) {
                if (p == Themes.Part.FESTIVE_LIGHTS) {
                    Festivals.LightsMode[] modes = Festivals.LightsMode.values();
                    String[] modeNames = new String[modes.length];
                    for (int i = 0; i < modes.length; i++) modeNames[i] = modes[i].label;
                    lightsRow = add(box, ui.choiceRow(p.label, "", modeNames, () -> parts.lights().mode().ordinal(),
                            i -> parts.lights().setMode(modes[i]), this::refresh), top);
                } else {
                    add(box, ui.toggleRow(p.label, p.note, () -> parts.on(p), on -> parts.setOn(p, on),
                            this::refresh), top);
                }
                top = 12;
            }
        }

        if (theme.has(Themes.Part.TOP_BAR)) {
            box.addView(ui.caps("Top bar", 22, Ui.TEXT_2), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(48)));
            TopBar.Style[] styles = TopBar.Style.values();
            String[] styleNames = new String[styles.length];
            for (int i = 0; i < styles.length; i++) styleNames[i] = styles[i].label;
            add(box, ui.choiceRow("Top-button style", "Icons, text or both, for Inputs, Network and Settings",
                    styleNames, () -> parts.topButtonStyle().ordinal(), i -> parts.setTopButtonStyle(styles[i]),
                    this::refresh), 16);
        }

        box.addView(ui.caps("Always in " + theme.name, 22, Ui.TEXT_2), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(48)));
        add(box, ui.settingRow(structural(theme), "What goes in them is set under Your content", null, null), 16);

        refresh();
        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        page = scroll;
        return scroll;
    }

    /** "Top bar, wallpaper, Apps bar (up to 8) and Home apps". */
    private static String structural(Themes.Theme theme) {
        List<String> names = new ArrayList<>();
        for (Themes.Part p : Themes.Part.values()) {
            if (p.optional || !theme.has(p)) continue;
            String n = names.isEmpty() ? p.label : p.label.substring(0, 1).toLowerCase() + p.label.substring(1);
            if (p == Themes.Part.APPS_BAR) n = "Apps bar (up to " + theme.appsBarMax + ")";
            names.add(n);
        }
        if (names.size() <= 1) return names.isEmpty() ? "Nothing else" : names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private View add(LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(top)));
        return row;
    }

    private void refresh() {
        if (ui == null) return;
        if (page != null) Ui.refreshAll(page);
        if (lightsRow != null) {
            Festivals.LightsMode mode = parts.lights().mode();
            note(lightsRow, mode == Festivals.LightsMode.OFF ? "No lights"
                    : mode == Festivals.LightsMode.ALWAYS ? "Lights every day"
                    : "On the special days that have their lights on (Special days)");
        }
    }

    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
        }
    }

    @Override
    public boolean focusFirst() {
        return first != null && first.requestFocus();
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
    public void onDestroy() {
    }
}
