package com.chakriau.tvhome.v2.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppStates;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.ui.Fonts;
import com.chakriau.tvhome.v2.watching.WatchingRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Theme 1's Home (Requirements.md section 4; mockups {@code design/theme1-home.png} and
 * {@code theme1-home-scrolled.png}). One vertical page over a fixed wallpaper ({@link WallpaperView}):
 * <ol>
 *   <li>festive lights along the top edge, when on ({@link HomeDecor});</li>
 *   <li>top bar: time, date and weather on the left ({@link ClockWeather}); Inputs, Network and Settings on the
 *       right ({@link TopButtons});</li>
 *   <li>the sky, the special day's banner and two pairs of eyes between the top bar and the apps bar
 *       ({@link HomeDecor});</li>
 *   <li>the apps bar on a glass band, where focus starts ({@link AppTiles});</li>
 *   <li>Continue watching: one row of up to 10 cards (or Recently opened), when on and not empty
 *       ({@link WatchingRowView});</li>
 *   <li>the Home apps grid, in the order set in Settings.</li>
 * </ol>
 * Pressing Down into Continue watching or the grid scrolls the page: the wallpaper dims, the top bar gives way to a
 * small clock in the corner and the apps bar peeks at the top edge. Up or Back returns to the apps bar.
 *
 * <p>This class only lays Theme 1 out and handles its focus and scrolling; the parts are shared classes another
 * theme can reuse (DeveloperNotes.md, "Adding a theme"). Sizes are in the mockups' 1920 × 1080 pixels.
 */
public final class Theme1Home implements HomeTheme {

    private static final int SIDE = 110;           // left and right margin
    private static final int TOP_PAD = 40;         // top bar to the screen top; the lights hang behind it
    private static final int TOP_BAR_H = 150;
    private static final int BAND_TOP = 620;       // where the apps bar band starts on the first screen
    private static final int BAND_PAD = 30;
    private static final int BAR_GAP = 26;
    private static final int BAR_SLOTS_MIN = 6;    // tiles are sized as if there were at least 6
    private static final int GRID_GAP = BAR_GAP;
    private static final int PEEK = 70;            // how much of the band shows when scrolled down
    // How much the focused app grows: 20% still leaves a gap to the next tile (half the growth, 10% of a tile
    // width of about 250 px, is less than the 26 px gap).
    private static final float BAR_GROW = 1.2f, GRID_GROW = 1.2f;

    /** Banner, eyes and sky between the top bar and the apps bar (mockup theme1-home.png). */
    private static final HomeDecor.Layout DECOR = new HomeDecor.Layout(
            new HomeKit.Box(360, 230, 360 + 1200, 230 + 340),         // banner: 1200 x 340
            new HomeKit.Box(SIDE, 210, 1920 - SIDE, BAND_TOP - 16),   // eyes: below the top bar, above the band
            38, 70,                                                   // eye radius; pairs 70 px apart in height
            new HomeKit.Box(0, 180, 1920, BAND_TOP - 8));             // sky: edge to edge, top bar to the band

    private HomeKit kit;
    private ThemeParts parts;
    private WallpaperView wallpaper;
    private HomeScroller scroller;
    private View dim;
    private LinearLayout content;
    private TextView corner;
    private ClockWeather clockWeather;
    private TopButtons buttons;
    private AppTiles tiles;
    private WatchingRowView watching;
    private HomeDecor decor;
    private MinuteTicker ticker;
    private FrameLayout band;
    private LinearLayout barRow;
    private TextView barHint;
    private LinearLayout grid;

    /** Tiles by package, to put focus back on the same app after the lists change. */
    private final Map<String, View> barTiles = new HashMap<>();
    private final Map<String, View> gridTiles = new HashMap<>();
    private String shownKey;
    private boolean focusPending = true;

    // ---- HomeTheme ------------------------------------------------------------------------------------------------

    @Override
    public View createView(Activity activity, Actions actions, AppArt art) {
        kit = new HomeKit(activity, actions);
        parts = new ThemeParts(activity, Themes.THEME_1);
        tiles = new AppTiles(kit, art);
        decor = new HomeDecor(kit, parts, DECOR);
        ticker = new MinuteTicker(activity, this::onMinute);

        FrameLayout root = new FrameLayout(activity);
        wallpaper = new WallpaperView(activity);
        root.addView(wallpaper, HomeKit.match());
        decor.addSkyTo(root);
        dim = new View(activity);
        dim.setBackgroundColor(Color.BLACK);
        dim.setAlpha(0f);
        root.addView(dim, HomeKit.match());
        // Over the wallpaper, under the page (so tiles and buttons always draw over them); they scroll with it.
        decor.addTo(root);
        wallpaper.setSkyBand(DECOR.sky.top, DECOR.sky.bottom);
        wallpaper.setOnChange(() -> decor.onWallpaperChanged(wallpaper.skyLight()));
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> decor.follow(newFocus));

        scroller = new HomeScroller(activity);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(kit.px(SIDE), kit.px(TOP_PAD), kit.px(SIDE), kit.px(90));
        scroller.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, HomeKit.match());

        corner = kit.text("", 34, HomeKit.TEXT, Fonts.Weight.REGULAR);
        corner.setAlpha(0f);

        buildTopBar();
        buildBand();
        watching = new WatchingRowView(kit, art, SIDE);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = kit.px(64);
        content.addView(watching.view(), wlp);
        buildGridSection();

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.topMargin = kit.px(PEEK + 8);
        clp.rightMargin = kit.px(SIDE);
        root.addView(corner, clp);
        decor.bringTestLabelToFront();

        scroller.setPolicy(this::scrollFor);
        scroller.setListener(this::onScrolled);
        return root;
    }

    @Override
    public void showApps(AppsModel.Snapshot apps) {
        String key = key(apps);
        if (key.equals(shownKey)) return;   // nothing changed: keep the views (and the focus) as they are
        shownKey = key;

        View focused = content.findFocus();
        String focusedPkg = focused == null ? null : (String) focused.getTag();
        boolean wasInBar = focused != null && barTiles.containsValue(focused);
        int gridIndex = focused == null ? -1 : indexOfGridTile(focused);
        // On Settings only because the bar was empty: once the bar has apps, focus starts there instead.
        boolean onEmptyBarStart = focused == buttons.settingsButton() && barTiles.isEmpty();

        fillBar(apps.appsBar);
        fillGrid(apps.home, apps.appsBar.size());

        boolean first = focusPending;
        focusPending = false;
        content.post(() -> {
            if (first || onEmptyBarStart) {
                focusStart();
                return;
            }
            if (focusedPkg == null && content.findFocus() != null) return;   // focus was on a button: still there
            View again = wasInBar ? barTiles.get(focusedPkg) : gridTiles.get(focusedPkg);
            if (again == null) again = barTiles.get(focusedPkg);
            if (again == null) again = gridTiles.get(focusedPkg);
            if (again == null && gridIndex >= 0) again = gridTileAt(gridIndex);
            if (again != null) again.requestFocus();
            else focusStart();
        });
    }

    @Override
    public void showWatching(WatchingRow.Row row) {
        watching.show(row, content, this::focusStart);
    }

    @Override
    public void refreshDecor() {
        decor.onShown();
    }

    @Override
    public void onShown() {
        tiles.onShown();
        decor.onShown();
        wallpaper.start();
        buttons.onShown();
        clockWeather.onShown();
        ticker.start();
    }

    @Override
    public void onHidden() {
        tiles.onHidden();
        wallpaper.stop();
        decor.onHidden();
        buttons.onHidden();
        clockWeather.onHidden();
        ticker.stop();
    }

    @Override
    public void onTrimMemory() {
        wallpaper.release();
        watching.onTrimMemory();
        decor.onTrimMemory();
    }

    @Override
    public void onDestroy() {
        buttons.onDestroy();
        watching.onDestroy();
        wallpaper.shutdown();
        clockWeather.onDestroy();
        ticker.stop();
        kit.shutdown();
    }

    @Override
    public void onHomePressedAgain() {
        scroller.jumpTo(0);
        watching.scrollToStart();
        focusStart();
    }

    @Override
    public boolean onBack() {
        View f = content.findFocus();
        if (scroller.getScrollY() == 0 && f == startView()) return false;   // already where Home starts
        focusStart();
        return true;
    }

    private void onMinute() {
        clockWeather.onMinute();
        wallpaper.onMinute();   // a special day's photos may start or end
        decor.onMinute();
    }

    // ---- Focus and scrolling --------------------------------------------------------------------------------------

    /** Focus starts on the apps bar; with an empty bar, on the Settings button (the way to fill the bar). */
    private View startView() {
        View v = barRow.getChildCount() > 0 ? firstFocusable(barRow) : null;
        return v != null ? v : buttons.settingsButton();
    }

    private void focusStart() {
        startView().requestFocus();
    }

    private static View firstFocusable(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).isFocusable()) return g.getChildAt(i);
        return null;
    }

    /**
     * The first screen while focus is in the top bar or apps bar. Below the band the page scrolls down so the bar
     * peeks at the top: Continue watching sits just under it, the grid scrolls on as far as the focused row needs.
     */
    private int scrollFor(View focused, Rect rect, int viewport) {
        int base = band.getBottom() - kit.px(PEEK);
        if (watching.contains(focused)) return base;
        if (!HomeKit.isIn(focused, grid)) return 0;
        int current = scroller.getScrollY();
        int y = Math.max(base, current);
        if (rect.top - kit.px(150) < y) y = Math.max(base, rect.top - kit.px(150));
        if (rect.bottom + kit.px(100) > y + viewport) y = rect.bottom + kit.px(100) - viewport;
        return y;
    }

    /** Dims the wallpaper and swaps the top bar for the corner clock as the page scrolls down. */
    private void onScrolled(int y) {
        int base = Math.max(1, band.getBottom() - kit.px(PEEK));
        float p = Math.min(1f, y / (float) base);
        dim.setAlpha(0.5f * p);
        decor.setScroll(y);
        corner.setAlpha(Math.max(0f, (p - 0.6f) / 0.4f));
    }

    // ---- Top bar --------------------------------------------------------------------------------------------------

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(kit.activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        clockWeather = new ClockWeather(kit, weatherChanged -> {
            corner.setText(clockWeather.cornerText());
            if (weatherChanged) decor.onWeather(clockWeather.reading());
        });
        clockWeather.addTo(bar);
        buttons = new TopButtons(kit, parts);
        buttons.addTo(bar);
        content.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kit.px(TOP_BAR_H)));
    }

    // ---- Apps bar -------------------------------------------------------------------------------------------------

    /** Tile width for a bar of {@code count} apps: as if there were at least 6, so a short bar is not huge. */
    private int barTileWidth(int count) {
        int inner = kit.px(1920 - 2 * SIDE - 2 * BAND_PAD);
        int slots = Math.max(BAR_SLOTS_MIN, Math.min(count, AppStates.APPS_BAR_MAX));
        return (inner - (slots - 1) * kit.px(BAR_GAP)) / slots;
    }

    private void buildBand() {
        band = new FrameLayout(kit.activity);
        band.setClipChildren(false);
        band.setClipToPadding(false);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(kit.px(46));
        glass.setColor(Color.argb(34, 255, 255, 255));
        glass.setStroke(kit.line(2), Color.argb(56, 255, 255, 255));
        band.setBackground(glass);

        barRow = new LinearLayout(kit.activity);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER);
        barRow.setClipChildren(false);
        barRow.setClipToPadding(false);
        band.addView(barRow, HomeKit.match());

        barHint = kit.text("The Apps bar is empty. Hold OK on Settings (top right) and choose apps in Settings › Apps.",
                28, HomeKit.TEXT_2, Fonts.Weight.REGULAR);
        barHint.setGravity(Gravity.CENTER);
        barHint.setPadding(kit.px(60), 0, kit.px(60), 0);
        band.addView(barHint, HomeKit.match());

        // Fixed height (the tallest tiles, at 6 apps), so the page doesn't move when the bar changes.
        int bandH = barTileWidth(0) * 9 / 16 + 2 * kit.px(BAND_PAD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bandH);
        lp.topMargin = kit.px(BAND_TOP) - kit.px(TOP_PAD) - kit.px(TOP_BAR_H);   // the band stays put
        content.addView(band, lp);
    }

    private void fillBar(List<AppInfo> apps) {
        barRow.removeAllViews();
        barTiles.clear();
        barHint.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        int w = barTileWidth(apps.size());
        int h = w * 9 / 16;
        for (int i = 0; i < apps.size(); i++) {
            AppInfo app = apps.get(i);
            View t = tiles.tile(app, w, h, 24, BAR_GROW);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i > 0) lp.leftMargin = kit.px(BAR_GAP);
            barRow.addView(t, lp);
            barTiles.put(app.packageName, t);
        }
    }

    // ---- Home apps grid -------------------------------------------------------------------------------------------

    private void buildGridSection() {
        TextView header = kit.text("Apps", 36, HomeKit.TEXT, Fonts.Weight.BOLD);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = kit.px(64);
        content.addView(header, hlp);

        grid = new LinearLayout(kit.activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = kit.px(30);
        content.addView(grid, glp);

        TextView footer = kit.text("Hold OK on an app: App info  ·  Hold OK on Settings: the launcher's Settings, "
                + "where apps are arranged", 26, HomeKit.TEXT_2, Fonts.Weight.REGULAR);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = kit.px(56);
        content.addView(footer, flp);
    }

    /** As many to a row as the Apps bar has tiles (at least 6), at the same size, so the two line up. */
    private void fillGrid(List<AppInfo> apps, int barCount) {
        int columns = Math.max(BAR_SLOTS_MIN, Math.min(barCount, AppStates.APPS_BAR_MAX));
        grid.removeAllViews();
        gridTiles.clear();
        // The Apps bar's tile size, lined up under the bar's tiles inside its glass band.
        int w = barTileWidth(barCount);
        int h = w * 9 / 16;
        grid.setPadding(kit.px(BAND_PAD), 0, kit.px(BAND_PAD), 0);
        if (apps.isEmpty()) {
            grid.addView(kit.text("No apps on Home. Move apps here in Settings › Apps.", 28, HomeKit.TEXT_2,
                    Fonts.Weight.REGULAR));
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(kit.activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = kit.px(GRID_GAP);
                grid.addView(row, rlp);
            }
            AppInfo app = apps.get(i);
            View t = tiles.tile(app, w, h, 28, GRID_GROW);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            if (i % columns > 0) lp.leftMargin = kit.px(GRID_GAP);
            row.addView(t, lp);
            gridTiles.put(app.packageName, t);
        }
    }

    /** The position of a grid tile in reading order, or -1. */
    private int indexOfGridTile(View v) {
        int index = 0;
        for (int r = 0; r < grid.getChildCount(); r++) {
            View rowView = grid.getChildAt(r);
            if (!(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            for (int c = 0; c < row.getChildCount(); c++, index++) if (row.getChildAt(c) == v) return index;
        }
        return -1;
    }

    private View gridTileAt(int index) {
        int i = 0;
        View last = null;
        for (int r = 0; r < grid.getChildCount(); r++) {
            View rowView = grid.getChildAt(r);
            if (!(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            for (int c = 0; c < row.getChildCount(); c++, i++) {
                last = row.getChildAt(c);
                if (i == index) return last;
            }
        }
        return last;   // past the end: the last app
    }

    /** What the views show, so an unchanged list is not rebuilt. */
    private static String key(AppsModel.Snapshot s) {
        StringBuilder b = new StringBuilder();
        for (AppInfo a : s.appsBar) b.append(a.component.flattenToShortString()).append('|').append(a.label).append(',');
        b.append(';');
        for (AppInfo a : s.home) b.append(a.component.flattenToShortString()).append('|').append(a.label).append(',');
        return b.toString();
    }
}
