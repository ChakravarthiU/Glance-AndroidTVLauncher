package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppLauncher;
import com.chakriau.tvhome.v2.apps.AppMenu;
import com.chakriau.tvhome.v2.apps.AppStates;
import com.chakriau.tvhome.v2.apps.AppStates.State;
import com.chakriau.tvhome.v2.apps.AppsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings › Apps (Requirements.md 5.5, 5.6, 8; mockup {@code design/theme1-settings-apps.png}). Three tabs: Apps bar,
 * Home and Hidden. OK on an app opens its menu (move up, move down, move to another tab, App info); on the Apps bar
 * tab, "Add an app to the bar" picks one from Home or Hidden. Every change is saved at once; Home shows it on return.
 */
final class AppsPage implements SettingsPage {

    private static final String TAG = "TvHomeSettings";
    private static final State[] TABS = {State.APPS_BAR, State.HOME, State.HIDDEN};

    private final Activity activity;
    private final AppsModel model;
    private final AppArt art;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Ui ui;
    private final TextView[] tabs = new TextView[TABS.length];
    private final int[] tabIds = new int[TABS.length];
    private RecyclerView list;
    private FrameLayout panelColumn;
    private FocusTrap panel;
    private View park;
    private final RowAdapter adapter = new RowAdapter();

    private AppsModel.Snapshot snapshot;
    private int tab = 0;
    /** The app whose menu is open, drawn as selected; null when no menu is open. */
    private String selectedPkg;
    private boolean busy;

    AppsPage(Activity activity) {
        this.activity = activity;
        this.model = AppsModel.get(activity);
        this.art = new AppArt(activity);
    }

    @Override
    public String title() {
        return "Apps";
    }

    // ---- Layout ---------------------------------------------------------------------------------------------------

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        Context c = ui.context;
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(ui.pageHeader("Apps",
                "Choose where each app appears. Home can't be rearranged directly; changes here apply instantly."));

        LinearLayout tabRow = new LinearLayout(c);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView t = ui.text(AppMenu.tabLabel(TABS[i], 0), 28, Ui.TEXT, false);
            t.setFocusable(true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(ui.px(30), 0, ui.px(30), 0);
            tabIds[i] = View.generateViewId();
            t.setId(tabIds[i]);
            t.setOnFocusChangeListener((v, has) -> {
                if (has) selectTab(index);
                styleTabs();
            });
            tabs[i] = t;
            LinearLayout.LayoutParams lp = ui.lp(Ui.WRAP, ui.px(62));
            if (i > 0) lp.leftMargin = ui.px(16);
            tabRow.addView(t, lp);
        }
        page.addView(tabRow, ui.lp(Ui.WRAP, Ui.WRAP, ui.px(40)));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.HORIZONTAL);
        list = new RecyclerView(c);
        list.setId(View.generateViewId());
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(ui.px(4), ui.px(4), ui.px(4), ui.px(40));   // room for the focus outline
        adapter.setHasStableIds(true);
        list.setAdapter(adapter);
        body.addView(list, ui.lp(ui.px(868), Ui.MATCH));
        list.setFocusable(false);   // only its rows take focus

        panelColumn = new FrameLayout(c);
        panel = new FocusTrap(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(ui.round(Ui.PANEL, 22));
        panel.setPadding(ui.px(14), ui.px(26), ui.px(14), ui.px(16));
        panel.setVisibility(View.INVISIBLE);
        panelColumn.addView(panel, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        // Holds focus for a moment while the menu's rows are swapped, so removing the focused row never sends
        // focus to the Settings menu (which would switch pages).
        park = new View(c);
        park.setAlpha(0f);
        park.setOnFocusChangeListener((v, has) -> {
            if (!has) v.setFocusable(false);   // never reachable with the remote
        });
        panelColumn.addView(park, new FrameLayout.LayoutParams(1, 1));
        LinearLayout.LayoutParams plp = ui.lp(ui.px(420), Ui.MATCH);
        plp.leftMargin = ui.px(36);
        body.addView(panelColumn, plp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
        blp.topMargin = ui.px(26);
        page.addView(body, blp);
        styleTabs();
        return page;
    }

    private void styleTabs() {
        for (int i = 0; i < tabs.length; i++) {
            TextView t = tabs[i];
            boolean selected = i == tab;
            if (t.hasFocus()) {
                t.setBackground(ui.outlined(Ui.TEXT, 31, Ui.ACCENT, 4));
            } else {
                t.setBackground(ui.round(selected ? Ui.TEXT : Ui.CARD, 31));
            }
            t.setTextColor(selected || t.hasFocus() ? Ui.TEXT_DARK : Ui.TEXT);
            t.setTypeface(ui.font(selected));
            if (snapshot != null) t.setText(AppMenu.tabLabel(TABS[i], snapshot.list(TABS[i]).size()));
        }
    }

    private void selectTab(int index) {
        if (index == tab) return;
        tab = index;
        adapter.rebuild();
        list.scrollToPosition(0);
        styleTabs();
    }

    // ---- Data -----------------------------------------------------------------------------------------------------

    @Override
    public void onShown() {
        worker.execute(() -> {
            AppsModel.Snapshot s;
            try {
                s = model.refresh();
            } catch (RuntimeException e) {
                Log.w(TAG, "reading apps failed", e);
                return;
            }
            main.post(() -> {
                if (!activity.isDestroyed()) apply(s);
            });
        });
    }

    private void apply(AppsModel.Snapshot s) {
        snapshot = s;
        adapter.rebuild();
        styleTabs();
    }

    private State state() {
        return TABS[tab];
    }

    private List<AppInfo> apps() {
        return snapshot == null ? new ArrayList<>() : snapshot.list(state());
    }

    /** Runs a change off the main thread, then shows the new lists and calls {@code then} on the main thread. */
    private void edit(Change change, Runnable then) {
        if (busy) return;   // one change at a time; key repeat must not queue a dozen moves
        busy = true;
        worker.execute(() -> {
            boolean ok;
            AppsModel.Snapshot s;
            try {
                ok = change.run();
                s = model.current();
            } catch (RuntimeException e) {
                Log.w(TAG, "change failed", e);
                main.post(() -> busy = false);
                return;
            }
            main.post(() -> {
                busy = false;
                if (activity.isDestroyed()) return;
                apply(s);
                if (!ok) Toast.makeText(activity, "That change isn't possible", Toast.LENGTH_SHORT).show();
                then.run();
            });
        });
    }

    private interface Change {
        boolean run();
    }

    // ---- Focus ----------------------------------------------------------------------------------------------------

    @Override
    public boolean focusFirst() {
        if (adapter.getItemCount() > 0 && focusPosition(0)) return true;
        return tabs[tab].requestFocus();
    }

    private boolean focusPosition(int position) {
        if (position < 0 || position >= adapter.getItemCount()) return false;
        RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position);
        if (h != null) return h.itemView.requestFocus();
        list.scrollToPosition(position);
        list.post(() -> {
            RecyclerView.ViewHolder later = list.findViewHolderForAdapterPosition(position);
            if (later != null) later.itemView.requestFocus();
            else tabs[tab].requestFocus();
        });
        return true;
    }

    private void focusApp(String pkg) {
        int pos = adapter.positionOf(pkg);
        if (pos >= 0) focusPosition(pos);
    }

    @Override
    public boolean onBack() {
        if (panel.trapping) {
            closePanel(true);
            return true;
        }
        return false;
    }

    // ---- The per-app menu -----------------------------------------------------------------------------------------

    private void openMenu(AppInfo app, AppMenu.Action keepFocusOn) {
        List<AppInfo> apps = apps();
        int index = apps.indexOf(app);
        if (index < 0) {
            closePanel(false);
            return;
        }
        selectedPkg = app.packageName;
        adapter.notifyDataSetChanged();
        clearPanel();
        panel.addView(header(app.label));

        List<AppMenu.Item> items = AppMenu.itemsFor(state(), index, apps.size(), snapshot.appsBarFull());
        View focus = null;
        View firstEnabled = null;
        AppMenu.Action previous = null;
        for (AppMenu.Item item : items) {
            if (previous != null && groupOf(previous) != groupOf(item.action)) panel.addView(divider());
            previous = item.action;
            View row = menuRow(item.action.label, arrow(item.action), item.enabled, item.note);
            if (item.enabled) {
                row.setOnClickListener(v -> onMenuAction(app, item.action));
                if (firstEnabled == null) firstEnabled = row;
                if (item.action == keepFocusOn) focus = row;
            }
            panel.addView(row);
        }
        showPanel(focus != null ? focus : firstEnabled, app.packageName);
    }

    private static int groupOf(AppMenu.Action a) {
        switch (a) {
            case MOVE_UP:
            case MOVE_DOWN:
                return 0;
            case APP_INFO:
                return 2;
            default:
                return 1;
        }
    }

    private static String arrow(AppMenu.Action a) {
        return a == AppMenu.Action.MOVE_UP ? "▲" : a == AppMenu.Action.MOVE_DOWN ? "▼" : null;
    }

    private void onMenuAction(AppInfo app, AppMenu.Action action) {
        String pkg = app.packageName;
        switch (action) {
            case MOVE_UP:
            case MOVE_DOWN:
                // The menu stays open so the app can be moved several places in a row.
                edit(() -> action == AppMenu.Action.MOVE_UP ? model.moveUp(pkg) : model.moveDown(pkg),
                        () -> {
                            int pos = adapter.positionOf(pkg);
                            if (pos >= 0) list.scrollToPosition(pos);
                            list.post(() -> openMenu(app, action));
                        });
                break;
            case APP_INFO:
                closePanel(true);
                AppLauncher.showAppInfo(activity, app);
                break;
            default:
                State target = action.target();
                int oldPos = adapter.positionOf(pkg);
                edit(() -> model.moveTo(pkg, target), () -> {
                    closePanel(false);
                    Toast.makeText(activity, app.label + " moved to " + AppMenu.stateName(target),
                            Toast.LENGTH_SHORT).show();
                    if (adapter.getItemCount() > 0) {
                        focusPosition(Math.max(0, Math.min(oldPos, adapter.getItemCount() - 1)));
                    } else {
                        tabs[tab].requestFocus();
                    }
                });
        }
    }

    /** "Add an app to the bar": the Home apps, then the Hidden ones. */
    private void openPicker() {
        if (snapshot == null) return;
        if (snapshot.appsBarFull()) {
            Toast.makeText(activity, "The Apps bar is full", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedPkg = null;
        clearPanel();
        panel.addView(header("Add to the Apps bar"));
        List<AppInfo> candidates = new ArrayList<>(snapshot.home);
        candidates.addAll(snapshot.hidden);
        if (candidates.isEmpty()) {
            panel.addView(menuRow("Every app is already in the bar", null, false, null));
            showPanel(null, null);
            return;
        }
        LinearLayout column = new LinearLayout(ui.context);
        column.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (AppInfo app : candidates) {
            boolean hidden = snapshot.hidden.contains(app);
            View row = menuRow(app.label, hidden ? "Hidden" : null, true, null);
            row.setOnClickListener(v -> edit(() -> model.moveTo(app.packageName, State.APPS_BAR), () -> {
                closePanel(false);
                focusApp(app.packageName);
            }));
            if (first == null) first = row;
            column.addView(row);
        }
        ScrollView scroll = new ScrollView(ui.context);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(column);
        int maxHeight = panelColumn.getHeight() - ui.px(110);
        panel.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH,
                maxHeight > 0 && candidates.size() * ui.px(70) > maxHeight ? maxHeight : Ui.WRAP));
        showPanel(first, null);
    }

    private void showPanel(View focus, String nextToPkg) {
        panel.trapping = true;
        panel.setVisibility(View.VISIBLE);
        if (focus != null) focus.requestFocus();
        else panel.requestFocus();
        // Line the menu up with its app (as in the mockup), kept inside the column.
        panel.post(() -> {
            float y = 0;
            int pos = nextToPkg == null ? -1 : adapter.positionOf(nextToPkg);
            RecyclerView.ViewHolder h = pos < 0 ? null : list.findViewHolderForAdapterPosition(pos);
            if (h != null) y = h.itemView.getTop() - ui.px(18);
            float max = Math.max(0, panelColumn.getHeight() - panel.getHeight());
            panel.setTranslationY(Math.max(0, Math.min(y, max)));
        });
    }

    /** Empties the menu without letting focus escape to the Settings menu. */
    private void clearPanel() {
        if (panel.hasFocus()) {
            park.setFocusable(true);
            park.requestFocus();
        }
        panel.removeAllViews();
    }

    private void closePanel(boolean refocus) {
        String pkg = selectedPkg;
        clearPanel();
        panel.trapping = false;
        panel.setVisibility(View.INVISIBLE);
        selectedPkg = null;
        adapter.notifyDataSetChanged();
        if (refocus) {
            if (pkg != null) {
                focusApp(pkg);
            } else if (state() == State.APPS_BAR) {
                focusPosition(adapter.getItemCount() - 1);   // back on "Add an app to the bar"
            } else {
                focusFirst();
            }
        }
    }

    private View header(String text) {
        TextView t = ui.caps(text, 20, Ui.TEXT_2);
        t.setPadding(ui.px(22), 0, ui.px(22), ui.px(16));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private View divider() {
        View v = new View(ui.context);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = ui.lp(Ui.MATCH, Math.max(1, ui.px(1.5f)));
        lp.setMargins(ui.px(10), ui.px(10), ui.px(10), ui.px(10));
        v.setLayoutParams(lp);
        return v;
    }

    /** One menu row: label, an optional mark on the right, and a note under disabled rows. */
    private View menuRow(String label, String mark, boolean enabled, String note) {
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.px(22), ui.px(16), ui.px(22), ui.px(16));
        row.setMinimumHeight(ui.px(66));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout line = new LinearLayout(ui.context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = ui.text(label, 28, enabled ? Ui.TEXT : Ui.TEXT_3, false);
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        TextView m = null;
        if (mark != null) {
            m = ui.text(mark, mark.length() == 1 ? 24 : 20, enabled ? Ui.TEXT_2 : Ui.TEXT_3, false);
            line.addView(m, ui.lp(Ui.WRAP, Ui.WRAP));
        }
        row.addView(line);
        if (note != null) row.addView(ui.text(note, 20, Ui.TEXT_3, false), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(6)));
        if (enabled) {
            row.setFocusable(true);
            final TextView mark2 = m;
            row.setOnFocusChangeListener((v, has) -> {
                v.setBackground(has ? ui.round(Ui.TEXT, 12) : null);
                l.setTextColor(has ? Ui.TEXT_DARK : Ui.TEXT);
                l.setTypeface(ui.font(has));
                if (mark2 != null) mark2.setTextColor(has ? Ui.TEXT_DARK : Ui.TEXT_2);
            });
        }
        return row;
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        art.shutdown();
    }

    // ---- The list -------------------------------------------------------------------------------------------------

    private static final int TYPE_APP = 0, TYPE_ADD = 1, TYPE_EMPTY = 2;

    private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<AppInfo> rows = new ArrayList<>();
        private boolean addRow;
        private boolean emptyRow;

        void rebuild() {
            rows = apps();
            addRow = snapshot != null && state() == State.APPS_BAR && !snapshot.appsBarFull();
            emptyRow = snapshot != null && rows.isEmpty() && !addRow;
            notifyDataSetChanged();
        }

        int positionOf(String pkg) {
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).packageName.equals(pkg)) return i;
            return -1;
        }

        @Override
        public int getItemCount() {
            return rows.size() + (addRow || emptyRow ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            if (position < rows.size()) return TYPE_APP;
            return addRow ? TYPE_ADD : TYPE_EMPTY;
        }

        @Override
        public long getItemId(int position) {
            if (position < rows.size()) return rows.get(position).packageName.hashCode() & 0xffffffffL;
            return -1 - getItemViewType(position);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v;
            if (type == TYPE_APP) v = appRow(parent.getContext());
            else if (type == TYPE_ADD) v = addRow(parent.getContext());
            else v = emptyRow(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, ui.px(74));
            lp.bottomMargin = ui.px(10);
            v.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            v.setNextFocusUpId(position == 0 ? tabIds[tab] : View.NO_ID);
            int type = getItemViewType(position);
            if (type == TYPE_APP) {
                bindApp(v, rows.get(position), position);
            } else if (type == TYPE_ADD) {
                ((TextView) ((LinearLayout) v).getChildAt(0)).setText(String.valueOf(position + 1));
            } else {
                ((TextView) v).setText(state() == State.HIDDEN
                        ? "No hidden apps. Hide an app from the Apps bar or Home tab."
                        : "No apps here yet.");
            }
        }
    }

    private View appRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(ui.px(26), 0, ui.px(22), 0);
        TextView number = ui.text("", 26, Ui.TEXT_2, false);
        row.addView(number, ui.lp(ui.px(56), Ui.WRAP));
        FrameLayout box = new FrameLayout(c);
        box.setBackground(ui.round(Ui.CHIP, 8));
        box.setClipToOutline(true);
        ImageView image = new ImageView(c);
        box.addView(image, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        row.addView(box, ui.lp(ui.px(88), ui.px(50)));
        TextView label = ui.text("", 30, Ui.TEXT, false);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(ui.px(24), 0, ui.px(16), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        TextView chip = ui.text("", 22, Ui.TEXT, false);
        chip.setPadding(ui.px(16), ui.px(10), ui.px(16), ui.px(10));
        chip.setBackground(ui.round(Ui.CHIP, 10));
        row.addView(chip, ui.lp(Ui.WRAP, Ui.WRAP));
        return row;
    }

    private void bindApp(View v, AppInfo app, int position) {
        LinearLayout row = (LinearLayout) v;
        TextView number = (TextView) row.getChildAt(0);
        ImageView image = (ImageView) ((FrameLayout) row.getChildAt(1)).getChildAt(0);
        TextView label = (TextView) row.getChildAt(2);
        TextView chip = (TextView) row.getChildAt(3);
        // Hidden apps have no order, so no number.
        number.setText(state() == State.HIDDEN ? "" : String.valueOf(position + 1));
        label.setText(app.label);
        chip.setText(AppMenu.stateName(state()));
        image.setImageDrawable(null);
        art.load(app, image, a -> {
            image.setImageDrawable(a.drawable);
            image.setScaleType(a.isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            int inset = a.isBanner ? 0 : ui.px(8);
            image.setPadding(inset, inset, inset, inset);
        });
        boolean selected = app.packageName.equals(selectedPkg);
        styleRow(row, row.hasFocus() || selected);
        row.setOnFocusChangeListener((view, has) -> styleRow(view, has || app.packageName.equals(selectedPkg)));
        row.setOnClickListener(view -> openMenu(app, null));
        row.setOnLongClickListener(view -> {
            openMenu(app, null);
            return true;
        });
        row.setContentDescription(app.label);
    }

    private void styleRow(View row, boolean highlighted) {
        row.setBackground(highlighted ? ui.outlined(Ui.CARD_FOCUSED, 14, Ui.ACCENT, 4) : ui.round(Ui.CARD, 14));
    }

    private View addRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(ui.px(26), 0, ui.px(22), 0);
        row.addView(ui.text("", 26, Ui.TEXT_3, false), ui.lp(ui.px(56), Ui.WRAP));
        TextView label = ui.text("+  Add an app to the bar", 30, Ui.TEXT_2, false);
        row.addView(label, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        row.setBackground(ui.dashed(14));
        row.setOnFocusChangeListener((view, has) -> {
            view.setBackground(has ? ui.outlined(Ui.CARD_FOCUSED, 14, Ui.ACCENT, 4) : ui.dashed(14));
            label.setTextColor(has ? Ui.TEXT : Ui.TEXT_2);
        });
        row.setOnClickListener(view -> openPicker());
        return row;
    }

    private View emptyRow(Context c) {
        TextView t = ui.text("", 26, Ui.TEXT_3, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(ui.px(26), 0, 0, 0);
        return t;
    }

    // ---- Focus trap -------------------------------------------------------------------------------------------

    /** While a menu is open, the remote stays inside it; Back closes it. */
    private static final class FocusTrap extends LinearLayout {
        boolean trapping;

        FocusTrap(Context c) {
            super(c);
            setFocusable(false);
            setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            View next = super.focusSearch(focused, direction);
            if (!trapping) return next;
            return next != null && Ui.isInside(next, this) ? next : focused;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            // Left and Right have nowhere to go inside the menu: swallow them instead of leaving.
            int code = event.getKeyCode();
            if (trapping && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
            return super.dispatchKeyEvent(event);
        }
    }
}
