package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chakriau.tvhome.v2.HomePreviewActivity;
import com.chakriau.tvhome.v2.home.ThemeStore;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.phone.PhoneApi;
import com.chakriau.tvhome.v2.wallpapers.Photos;
import com.chakriau.tvhome.v2.wallpapers.UploadServer;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's own Settings (Requirements.md section 8): a left menu of pages, the chosen page on the right. A page
 * opens as soon as its menu item is focused; Right or OK goes into it, Back comes out to the menu, Back again closes
 * Settings. The menu has Phone, then two groups: the active theme (Theme, and pages for parts only some themes have,
 * such as Eyes) and your content (shared by every theme).
 */
public final class SettingsActivity extends Activity implements PhonePage.Host, PhoneApi.Tv, ThemePage.Host {

    /** Optional extra: the title of the page to open first. Apps by default. */
    public static final String EXTRA_PAGE = "page";

    private Ui ui;
    private final List<SettingsPage> pages = new ArrayList<>();
    /** The menu group each page starts, by page index (null: continues the group above). */
    private final List<String> groupStarts = new ArrayList<>();
    private final List<TextView> menuItems = new ArrayList<>();
    private final List<View> pageViews = new ArrayList<>();
    private LinearLayout menu;
    private FrameLayout pageHolder;
    private TextView crumb;
    private int current = -1;
    private TimeWeatherPage timeWeather;
    private SystemPage systemPage;
    private ContinueWatchingPage watchingPage;
    private UploadServer server;
    /** Whether Settings is on screen (between onStart and onStop). */
    private boolean started;
    /** The Home preview is opening over Settings: keep the phone page running under it (Requirements.md 5.14). */
    private boolean keepServerForPreview;

    public static void open(Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        Themes.Theme theme = ThemeStore.active(this);
        startPhoneServer();

        // The pages, in menu order (Requirements.md section 8): Phone first, then the theme's group, then your content.
        PhonePage phone = new PhonePage(this, this);
        add(phone, null);
        add(new ThemePage(this, this), theme.name);
        if (theme.has(Themes.Part.EYES)) add(new EyesPage(this), null);
        add(new AppsPage(this), "Your content");
        add(new WallpapersPage(this), null);
        watchingPage = new ContinueWatchingPage(this);
        add(watchingPage, null);
        add(new SpecialDaysPage(this), null);
        timeWeather = new TimeWeatherPage(this);
        add(timeWeather, null);
        systemPage = new SystemPage(this);
        add(systemPage, null);
        for (int i = 0; i < pages.size(); i++) pageViews.add(null);

        setContentView(buildLayout(theme));

        int start = pages.indexOf(phone);   // Phone is first and opens by default
        String wanted = getIntent().getStringExtra(EXTRA_PAGE);
        for (int i = 0; wanted != null && i < pages.size(); i++) {
            if (pages.get(i).title().equalsIgnoreCase(wanted)) start = i;
        }
        show(start);
        menuItems.get(start).requestFocus();
    }

    private void add(SettingsPage page, String group) {
        pages.add(page);
        groupStarts.add(group);
    }

    /** Settings › Theme picked another theme: build Settings again, so the theme's own pages come and go. */
    @Override
    public void themeChanged() {
        Intent again = getIntent().putExtra(EXTRA_PAGE, "Theme");
        finish();
        startActivity(again);
        overridePendingTransition(0, 0);
    }

    private View buildLayout(Themes.Theme theme) {
        Root root = new Root(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(Ui.MENU_BG);
        menu.setPadding(ui.px(36), ui.px(82), ui.px(36), ui.px(40));
        TextView heading = ui.caps("Settings", 25, Ui.TEXT_2);
        heading.setPadding(ui.px(20), 0, 0, ui.px(28));
        menu.addView(heading);
        for (int i = 0; i < pages.size(); i++) {
            final int index = i;
            if (groupStarts.get(i) != null) {
                // A group's name: not focusable, so Up and Down skip it.
                TextView head = ui.caps(groupStarts.get(i), 20, Ui.TEXT_2);
                head.setAlpha(0.7f);
                head.setPadding(ui.px(25), ui.px(i == 1 ? 18 : 30), 0, ui.px(6));
                menu.addView(head, ui.lp(Ui.MATCH, Ui.WRAP));
            }
            TextView item = ui.text(pages.get(i).title(), 31, Ui.TEXT_2, false);
            item.setFocusable(true);
            item.setPadding(ui.px(25), ui.px(20), ui.px(25), ui.px(20));
            item.setMinHeight(ui.px(80));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setLineSpacing(ui.px(2), 1f);
            item.setOnFocusChangeListener((v, has) -> {
                if (has) show(index);
                styleMenu();
            });
            item.setOnClickListener(v -> enterPage());
            item.setOnKeyListener((v, code, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    enterPage();
                    return true;   // never wander into the page geometrically
                }
                return false;
            });
            menuItems.add(item);
            LinearLayout.LayoutParams lp = ui.lp(Ui.MATCH, Ui.WRAP, ui.px(6));
            menu.addView(item, lp);
        }
        // Scrolls only if the menu outgrows the screen (focus moving down scrolls it).
        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.setFillViewport(true);
        menuScroll.setBackgroundColor(Ui.MENU_BG);
        menuScroll.addView(menu, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(menuScroll, ui.lp(ui.px(420), Ui.MATCH));

        FrameLayout content = new FrameLayout(this);
        pageHolder = new FrameLayout(this);
        pageHolder.setPadding(ui.px(70), ui.px(72), ui.px(110), ui.px(100));
        content.addView(pageHolder, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        crumb = ui.caps("", 22, Ui.TEXT_2);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
        clp.rightMargin = ui.px(112);
        clp.bottomMargin = ui.px(46);
        content.addView(crumb, clp);
        crumb.setTag(theme.name);
        root.addView(content, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        return root;
    }

    private void show(int index) {
        if (index == current) return;
        if (current >= 0) pages.get(current).onHidden();
        current = index;
        SettingsPage page = pages.get(index);
        View v = pageViews.get(index);
        if (v == null) {
            v = page.createView(ui);
            pageViews.set(index, v);
        }
        pageHolder.removeAllViews();
        pageHolder.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        crumb.setText((crumb.getTag() + " · Settings › " + page.title()).toUpperCase());
        page.onShown();
        styleMenu();
    }

    private void styleMenu() {
        for (int i = 0; i < menuItems.size(); i++) {
            TextView item = menuItems.get(i);
            boolean focused = item.hasFocus();
            boolean selected = i == current;
            if (focused) {
                item.setBackground(ui.round(Ui.TEXT, 16));
                item.setTextColor(Ui.TEXT_DARK);
            } else if (selected) {
                item.setBackground(ui.round(Ui.SELECTED_MENU, 16));
                item.setTextColor(Ui.TEXT);
            } else {
                item.setBackground(null);
                item.setTextColor(Ui.TEXT_2);
            }
            item.setTypeface(ui.font(selected || focused));
        }
    }

    private void enterPage() {
        if (current >= 0) pages.get(current).focusFirst();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        View focus = getCurrentFocus();
        if (focus != null && current >= 0 && !Ui.isInside(focus, menu)) {
            if (!pages.get(current).onBack()) menuItems.get(current).requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == TimeWeatherPage.LOCATION_REQUEST && timeWeather != null) timeWeather.onLocationAnswer();
        if (requestCode == SystemPage.WIFI_NAME_REQUEST && systemPage != null) systemPage.onPermissionAnswer();
        if (requestCode == ContinueWatchingPage.WATCH_NEXT_REQUEST && watchingPage != null) {
            watchingPage.onPermissionAnswer();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        keepServerForPreview = false;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startPhoneServer();
        if (current >= 0) pages.get(current).onShown();
    }

    @Override
    protected void onStop() {
        started = false;
        if (current >= 0) pages.get(current).onHidden();
        if (!keepServerForPreview) stopPhoneServer();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        for (SettingsPage p : pages) p.onDestroy();
        stopPhoneServer();
        super.onDestroy();
    }

    // ---- The phone page (Settings › Phone) ------------------------------------------------------------------------

    /** The phone page is served while Settings is open, whichever page is on screen. */
    private void startPhoneServer() {
        if (server != null) return;
        server = new UploadServer(this, new Photos(this), new WallpaperStore(this), this::changed);
        server.setApi(new PhoneApi(this, this));
        server.setOnRestored(this::restored);
        if (!server.start()) server = null;
    }

    private void stopPhoneServer() {
        if (server != null) server.stop();
        server = null;
    }

    @Override
    public boolean phoneServerRunning() {
        return server != null;
    }

    @Override
    public void setPin(String pin) {
        if (server != null) server.setPin(pin);
    }

    /** Something changed from the phone: the page on screen shows the new values. */
    @Override
    public void changed() {
        if (isDestroyed()) return;
        if (current >= 0) pages.get(current).onShown();
    }

    // ---- Home preview (Requirements.md 5.14) ----------------------------------------------------------------------

    /**
     * Opens Home exactly as it looks, over Settings, with any test from the phone. The phone page keeps running under
     * it, so the phone can keep changing the test; Back returns here.
     */
    void openHomePreview() {
        if (isDestroyed() || isFinishing()) return;
        keepServerForPreview = true;
        HomePreviewActivity.open(this, () -> {
            // The preview went somewhere other than back to Settings (the Home key, say): nothing shows the phone
            // page any more, so it stops, as when Settings closes.
            keepServerForPreview = false;
            if (!started) stopPhoneServer();
        });
    }

    /** Opens the Home preview from a Settings page. */
    static void openHomePreview(Activity from) {
        if (from instanceof SettingsActivity) ((SettingsActivity) from).openHomePreview();
    }

    /** The phone asked to see Home (a test, or a special day's banner): open the preview, or show the change. */
    @Override
    public void showHome() {
        openHomePreview();
    }

    /** The phone changed or stopped a test: an open preview shows it (Home itself picks it up when next shown). */
    @Override
    public void testChanged() {
        HomePreviewActivity.refresh();
        changed();
    }

    /** A backup was restored from the phone: the launcher restarts to put it in place (Requirements.md 5.11). */
    private void restored() {
        if (isDestroyed()) return;
        android.widget.Toast.makeText(this, "Backup restored. Restarting the launcher\u2026",
                android.widget.Toast.LENGTH_LONG).show();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> com.chakriau.tvhome.v2.backup.Backup.restart(this), 1200);
    }


    /** Focus coming back to the menu from a page lands on the current page's item, not the nearest one. */
    private final class Root extends LinearLayout {
        Root(Context c) {
            super(c);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            View next = super.focusSearch(focused, direction);
            if (next != null && current >= 0 && Ui.isInside(next, menu) && !Ui.isInside(focused, menu)) {
                return menuItems.get(current);
            }
            return next;
        }
    }
}
