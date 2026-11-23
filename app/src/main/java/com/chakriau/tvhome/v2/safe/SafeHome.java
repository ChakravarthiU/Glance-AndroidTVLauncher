package com.chakriau.tvhome.v2.safe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.home.HomeKit;
import com.chakriau.tvhome.v2.home.HomeTheme;
import com.chakriau.tvhome.v2.ui.Fonts;
import com.chakriau.tvhome.v2.watching.WatchingRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Home in safe mode (Requirements.md 5.12): after the launcher crashed {@link SafeMode#CRASHES} times in a row. A
 * plain page that uses none of the parts that could be failing: no wallpaper photos, banner, sky, eyes, lights,
 * weather, Continue watching or app pictures. It says what happened, offers "Try normal Home again", the launcher's
 * Settings and the TV's settings, and lists the Apps bar and Home apps as text tiles (OK opens, hold OK: App info).
 */
public final class SafeHome implements HomeTheme {

    private static final int COLUMNS = 4;
    private static final int BG = Color.rgb(18, 22, 34);

    private HomeKit kit;
    private LinearLayout grid;
    private View tryAgain;
    private boolean focused;

    @Override
    public View createView(Activity activity, Actions actions, AppArt art) {
        kit = new HomeKit(activity, actions);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, HomeKit.match());
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(kit.px(110), kit.px(70), kit.px(110), kit.px(70));
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(kit.text("Safe mode", 64, HomeKit.TEXT, Fonts.Weight.BOLD));
        TextView why = kit.text("The launcher stopped several times in a row, so Home is showing just your apps. "
                + "Normal Home comes back by itself when a new version of the launcher is installed.", 30,
                HomeKit.TEXT_2, Fonts.Weight.REGULAR);
        why.setPadding(0, kit.px(18), 0, 0);
        page.addView(why);
        CrashLog.Problem p = CrashLog.last(activity);
        if (p != null) {
            TextView last = kit.text("Last problem, " + p.when + ": " + p.headline(), 24, HomeKit.TEXT_2,
                    Fonts.Weight.REGULAR);
            last.setSingleLine(true);
            last.setEllipsize(TextUtils.TruncateAt.END);
            last.setPadding(0, kit.px(12), 0, 0);
            page.addView(last);
            TextView more = kit.text("The full report is on the phone page (Settings › Phone, then Backup).", 24,
                    HomeKit.TEXT_2, Fonts.Weight.REGULAR);
            page.addView(more);
        }

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = kit.px(36);
        page.addView(buttons, blp);
        tryAgain = button("Try normal Home again", () -> {
            CrashLog.leave(activity);
            activity.recreate();
        });
        buttons.addView(tryAgain);
        View settings = button("Launcher settings", actions::openSettings);
        View tv = button("TV settings", actions::openSystemSettings);
        for (View v : new View[]{settings, tv}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = kit.px(20);
            buttons.addView(v, lp);
        }

        TextView apps = kit.text("Apps", 36, HomeKit.TEXT, Fonts.Weight.BOLD);
        apps.setPadding(0, kit.px(56), 0, kit.px(20));
        page.addView(apps);
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        page.addView(grid);
        return root;
    }

    private View button(String label, Runnable onClick) {
        TextView b = kit.text(label, 30, HomeKit.TEXT, Fonts.Weight.MEDIUM);
        b.setShadowLayer(0, 0, 0, 0);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(kit.px(32), kit.px(18), kit.px(32), kit.px(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(kit.px(16));
        b.setBackground(bg);
        style(b, bg, false);
        b.setOnFocusChangeListener((v, has) -> style(b, bg, has));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void style(TextView b, GradientDrawable bg, boolean has) {
        bg.setColor(has ? Color.WHITE : Color.argb(40, 255, 255, 255));
        b.setTextColor(has ? HomeKit.TEXT_ON_WHITE : HomeKit.TEXT);
    }

    @Override
    public void showApps(AppsModel.Snapshot apps) {
        grid.removeAllViews();
        List<AppInfo> all = new ArrayList<>(apps.appsBar);
        all.addAll(apps.home);
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(kit.activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = kit.px(16);
                grid.addView(row, rlp);
            }
            AppInfo app = all.get(i);
            View tile = button(app.label, () -> kit.actions.open(app));
            tile.setOnLongClickListener(v -> {
                kit.actions.showAppInfo(app);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, kit.px(100), 1);
            if (i % COLUMNS > 0) lp.leftMargin = kit.px(16);
            row.addView(tile, lp);
            if (first == null) first = tile;
        }
        if (all.isEmpty()) {
            grid.addView(kit.text("No apps on Home. Choose them in Launcher settings › Apps.", 28, HomeKit.TEXT_2,
                    Fonts.Weight.REGULAR));
        }
        // Focus starts on the first app (the point of Home), or on "Try normal Home again".
        if (!focused) {
            focused = true;
            View target = first != null ? first : tryAgain;
            target.post(target::requestFocus);
        }
    }

    @Override
    public void showWatching(WatchingRow.Row row) {
        // Not in safe mode.
    }

    @Override
    public void onShown() {
    }

    @Override
    public void onHidden() {
    }

    @Override
    public void onHomePressedAgain() {
        if (tryAgain != null) tryAgain.requestFocus();
    }

    @Override
    public void onTrimMemory() {
    }

    @Override
    public void onDestroy() {
        if (kit != null) kit.shutdown();
    }

    @Override
    public boolean onBack() {
        return false;
    }
}
