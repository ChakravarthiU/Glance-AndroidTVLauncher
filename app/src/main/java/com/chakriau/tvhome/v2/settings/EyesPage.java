package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.chakriau.tvhome.v2.eyes.EyeSize;
import com.chakriau.tvhome.v2.eyes.EyesStore;
import com.chakriau.tvhome.v2.eyes.EyesView;
import com.chakriau.tvhome.v2.eyes.Personality;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.Themes;

/**
 * Settings › Eyes (in the Theme group, while the theme has eyes; Requirements.md 5.2, 8): the personality and the
 * size, with a live preview of the two pairs. Saved for the active theme; on/off is on Settings › Theme.
 */
final class EyesPage implements SettingsPage {

    private static final int PREVIEW_W = 1100, PREVIEW_H = 260, EYE_R = 34;

    private final Activity activity;
    private final EyesStore store;
    private final ThemeParts parts;
    private View first, onRow, personalityRow, sizeRow;
    private Ui ui;
    private EyesView preview;
    private View page;
    private final ViewTreeObserver.OnGlobalFocusChangeListener follow = (oldFocus, newFocus) -> look(newFocus);

    EyesPage(Activity activity) {
        this.activity = activity;
        this.parts = ThemeParts.active(activity);
        this.store = new EyesStore(activity, parts.theme().id);
    }

    @Override
    public String title() {
        return "Eyes";
    }

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Eyes", "Two pairs of eyes on Home that glance around, blink, look at each other "
                + "and follow the remote. They move to new spots when the wallpaper changes."));

        onRow = ui.settingRow("On Home", "Turn the eyes on or off in Settings › Theme", "", null);
        box.addView(onRow, ui.lp(ui.px(PREVIEW_W), Ui.WRAP, ui.px(40)));
        Personality[] all = Personality.values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].label;
        personalityRow = ui.choiceRow("Personality", "", names, () -> store.personality().ordinal(),
                i -> store.setPersonality(all[i]), this::refresh);
        box.addView(personalityRow, ui.lp(ui.px(PREVIEW_W), Ui.WRAP, ui.px(12)));
        first = personalityRow;
        EyeSize[] sizes = EyeSize.values();
        String[] sizeNames = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) sizeNames[i] = sizes[i].label;
        sizeRow = ui.choiceRow("Size", "How big the eyes are on Home", sizeNames, () -> store.size().ordinal(),
                i -> store.setSize(sizes[i]), this::refresh);
        box.addView(sizeRow, ui.lp(ui.px(PREVIEW_W), Ui.WRAP, ui.px(12)));

        // The preview: the real eyes, in a box the size of a slice of Home.
        box.addView(ui.caps("Preview · it follows the remote on this page too", 22, Ui.TEXT_2),
                ui.lp(Ui.WRAP, Ui.WRAP, ui.px(40)));
        FrameLayout frame = new FrameLayout(ui.context);
        frame.setBackground(ui.round(0xFF1D2C55, 20));
        preview = new EyesView(ui.context, ui.px(EYE_R));
        frame.addView(preview, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        box.addView(frame, ui.lp(ui.px(PREVIEW_W), ui.px(PREVIEW_H), ui.px(16)));
        preview.setPairs(ui.px(PREVIEW_W * 0.24f), ui.px(PREVIEW_H * 0.42f),
                ui.px(PREVIEW_W * 0.74f), ui.px(PREVIEW_H * 0.62f));

        refresh();
        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        page = scroll;
        return scroll;
    }

    private void refresh() {
        if (onRow == null) return;
        Ui.refreshAll(page != null ? page : onRow);
        boolean on = parts.on(Themes.Part.EYES);
        if (Ui.valueOf(onRow) != null) Ui.valueOf(onRow).setText(on ? "On" : "Off");
        Personality p = store.personality();
        note(personalityRow, p.note);
        preview.setPersonality(p);
        EyeSize size = store.size();
        preview.setRadius(ui.px(EYE_R * size.scale));
        preview.setAlpha(on ? 1f : 0.35f);
    }

    /** The preview's eyes follow focus on this page too, so the personality shows how it moves. */
    private void look(View focused) {
        if (focused == null || preview == null || !Ui.isInside(focused, page)) return;
        int[] f = new int[2], e = new int[2];
        focused.getLocationInWindow(f);
        preview.getLocationInWindow(e);
        preview.lookAt(f[0] - e[0] + focused.getWidth() / 2f, f[1] - e[1] + focused.getHeight() / 2f);
    }

    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((android.widget.TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
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

    @Override
    public void onShown() {
        refresh();
        if (preview != null) {
            preview.start();
            ViewTreeObserver o = page.getViewTreeObserver();
            if (o.isAlive()) {
                o.removeOnGlobalFocusChangeListener(follow);   // onShown also runs on changes from the phone
                o.addOnGlobalFocusChangeListener(follow);
            }
        }
    }

    @Override
    public void onHidden() {
        if (preview != null) {
            preview.stop();
            ViewTreeObserver o = page.getViewTreeObserver();
            if (o.isAlive()) o.removeOnGlobalFocusChangeListener(follow);
        }
    }

    @Override
    public void onDestroy() {
        onHidden();
    }
}
