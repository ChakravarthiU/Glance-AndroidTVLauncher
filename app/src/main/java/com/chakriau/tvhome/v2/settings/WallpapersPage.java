package com.chakriau.tvhome.v2.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chakriau.tvhome.v2.festive.SpecialDayStore;
import com.chakriau.tvhome.v2.festive.SpecialDays;
import com.chakriau.tvhome.v2.wallpapers.PhotoSize;
import com.chakriau.tvhome.v2.wallpapers.Photos;
import com.chakriau.tvhome.v2.wallpapers.Rotation;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings › Wallpapers (Requirements.md 5.1, 8): wallpaper on/off, rotation interval, order, cross-fade, and the
 * photos with their ticks. Photos are added from the phone (Settings › Phone serves the phone page while Settings is
 * open).
 */
final class WallpapersPage implements SettingsPage {

    private static final String TAG = "TvHomeSettings";
    // The controls and photos column (the page is 1320 px wide); five photo tiles across.
    private static final int LEFT_W = 1100;
    private static final int COLUMNS = 5;
    private static final int GAP = 18;

    private final Activity activity;
    private final Photos photos;
    private final WallpaperStore store;
    private final SpecialDayStore specialDays;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> thumbs = new HashMap<>();

    private Ui ui;
    private View first;
    private View enabledRow, intervalRow, orderRow, fadeRow;
    private TextView photosLabel;
    private LinearLayout grid;
    private View page;
    private View specialRow;
    private List<String> ids = new ArrayList<>();
    private Set<String> ticked;
    private boolean shown;

    WallpapersPage(Activity activity) {
        this.activity = activity;
        this.photos = new Photos(activity);
        this.store = new WallpaperStore(activity);
        this.specialDays = new SpecialDayStore(activity);
        this.ticked = store.ticked();
    }

    @Override
    public String title() {
        return "Wallpapers";
    }

    // ---- Layout ---------------------------------------------------------------------------------------------------

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        LinearLayout page = new LinearLayout(ui.context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(ui.pageHeader("Wallpapers",
                "Photos from your phone behind Home. One ticked photo stays; several take turns."));

        LinearLayout body = new LinearLayout(ui.context);
        body.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(body, ui.lp(Ui.MATCH, Ui.WRAP, ui.px(40)));

        LinearLayout left = new LinearLayout(ui.context);
        left.setOrientation(LinearLayout.VERTICAL);
        body.addView(left, ui.lp(ui.px(LEFT_W), Ui.WRAP));

        enabledRow = add(left, ui.toggleRow("Wallpaper", "Off shows the built-in picture instead",
                store::enabled, store::setEnabled, null), 0);
        first = enabledRow;
        String[] intervals = new String[Rotation.INTERVALS.length];
        for (int i = 0; i < intervals.length; i++) intervals[i] = minutes(Rotation.INTERVALS[i]);
        intervalRow = add(left, ui.choiceRow("Change every", "When two or more photos are ticked", intervals,
                () -> indexOf(Rotation.INTERVALS, store.intervalMinutes()),
                i -> store.setIntervalMinutes(Rotation.INTERVALS[i]), null), 12);
        orderRow = add(left, ui.choiceRow("Order", "In turn, or shuffled", new String[]{"In turn", "Shuffle"},
                () -> store.shuffle() ? 1 : 0, i -> store.setShuffle(i == 1), null), 12);
        fadeRow = add(left, ui.toggleRow("Cross-fade", "Fade softly from one photo to the next",
                store::crossFade, store::setCrossFade, null), 12);
        specialRow = add(left, ui.settingRow("Today", "", "", null), 12);
        add(left, ui.settingRow("Add photos from your phone", "Settings › Phone: scan its code, then Wallpapers",
                null, null), 12);

        photosLabel = ui.caps("Photos", 22, Ui.TEXT_2);
        left.addView(photosLabel, ui.lp(Ui.WRAP, Ui.WRAP, ui.px(44)));
        left.addView(ui.text("OK ticks or unticks a photo. Hold OK to remove it from the TV.", 22, Ui.TEXT_3,
                false), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(10)));
        grid = new LinearLayout(ui.context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        left.addView(grid, ui.lp(Ui.MATCH, Ui.WRAP, ui.px(18)));


        ScrollView scroll = new ScrollView(ui.context);
        this.page = scroll;
        scroll.setClipChildren(false);
        scroll.addView(page);
        return scroll;
    }

    private View add(LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(Ui.MATCH, Ui.WRAP, ui.px(top)));
        return row;
    }

    private static String minutes(int m) {
        return m == 60 ? "1 hour" : m + " minutes";
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    // ---- Shown and hidden -----------------------------------------------------------------------------------------

    /** Also called when photos change from the phone (Settings › Phone serves the phone page). */
    @Override
    public void onShown() {
        shown = true;
        if (ui != null) Ui.refreshAll(page);
        showSpecialToday();
        reload(null);
    }

    /** Whether a special day's photo shows today; the day is never named on the TV (it may be a surprise). */
    private void showSpecialToday() {
        if (specialRow == null) return;
        TextView value = Ui.valueOf(specialRow);
        TextView note = (TextView) ((LinearLayout) ((LinearLayout) specialRow).getChildAt(0)).getChildAt(1);
        worker.execute(() -> {
            LocalDate today = LocalDate.now();
            List<String> all = photos.list();
            int n = SpecialDays.photos(today, specialDays.days(), all).size();
            String v = n == 0 ? "Ticked photos" : n + (n == 1 ? " special-day photo" : " special-day photos");
            String t = n == 0 ? "A special day's own photo shows on its days (Special days)"
                    : "For today's special day, instead of the ticked photos";
            main.post(() -> {
                if (value != null) value.setText(v);
                note.setText(t);
            });
        });
    }

    @Override
    public void onHidden() {
        shown = false;
    }

    // ---- Photos ---------------------------------------------------------------------------------------------------

    /** Re-reads the photos and redraws the grid; focus goes back to {@code focusId} if given. */
    private void reload(String focusId) {
        String keep = focusId != null ? focusId : focusedPhoto();
        worker.execute(() -> {
            List<String> list = photos.list();
            Set<String> t = store.ticked();
            // Thumbnails at half size: the tiles are about 165 px wide.
            Map<String, Bitmap> loaded = new HashMap<>();
            for (String id : list) {
                synchronized (thumbs) {
                    if (thumbs.containsKey(id)) continue;
                }
                Bitmap b = Photos.decode(photos.thumb(id), PhotoSize.THUMB_WIDTH / 2, PhotoSize.THUMB_HEIGHT / 2);
                if (b != null) loaded.put(id, b);
            }
            main.post(() -> {
                if (activity.isDestroyed() || grid == null) return;
                synchronized (thumbs) {
                    thumbs.putAll(loaded);
                    thumbs.keySet().retainAll(list);
                }
                ids = list;
                ticked = t;
                fillGrid(keep);
            });
        });
    }

    private String focusedPhoto() {
        if (grid == null) return null;
        View f = grid.findFocus();
        return f == null ? null : (String) f.getTag();
    }

    private void fillGrid(String focusId) {
        boolean hadFocus = grid.hasFocus();
        if (hadFocus) first.requestFocus();   // keep focus on the page while the tiles are replaced
        grid.removeAllViews();
        updateCount();
        if (ids.isEmpty()) {
            grid.addView(ui.text("No photos yet. Scan the code on the right with your phone to add some.", 24,
                    Ui.TEXT_2, false));
            return;
        }
        int w = (ui.px(LEFT_W) - (COLUMNS - 1) * ui.px(GAP)) / COLUMNS;
        int h = w * 9 / 16;
        LinearLayout row = null;
        View focusTarget = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(ui.context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row, ui.lp(Ui.MATCH, Ui.WRAP, i == 0 ? 0 : ui.px(GAP)));
            }
            String id = ids.get(i);
            View tile = photoTile(id, w, h);
            LinearLayout.LayoutParams lp = ui.lp(w, h);
            if (i % COLUMNS > 0) lp.leftMargin = ui.px(GAP);
            row.addView(tile, lp);
            if (id.equals(focusId)) focusTarget = tile;
        }
        if (hadFocus) {
            View target = focusTarget != null ? focusTarget : ((ViewGroup) grid.getChildAt(0)).getChildAt(0);
            target.requestFocus();
        }
    }

    private View photoTile(String id, int w, int h) {
        FrameLayout t = new FrameLayout(ui.context);
        t.setFocusable(true);
        t.setTag(id);
        t.setBackground(ui.round(Ui.CHIP, 12));
        t.setClipToOutline(true);
        ImageView img = new ImageView(ui.context);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        synchronized (thumbs) {
            img.setImageBitmap(thumbs.get(id));
        }
        t.addView(img, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        TextView badge = ui.text("", 22, Ui.TEXT_DARK, true);
        badge.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ui.px(34), ui.px(34), Gravity.TOP | Gravity.END);
        blp.topMargin = ui.px(8);
        blp.rightMargin = ui.px(8);
        t.addView(badge, blp);

        View ring = new View(ui.context);
        ring.setBackground(ui.outlined(Color.TRANSPARENT, 12, Ui.ACCENT, 4));
        ring.setVisibility(View.INVISIBLE);
        t.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        t.setOnFocusChangeListener((v, has) -> {
            ring.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            v.setZ(has ? 1 : 0);
        });
        t.setOnClickListener(v -> toggle(id));
        t.setOnLongClickListener(v -> {
            confirmRemove(id);
            return true;
        });
        styleTick(t, ticked.contains(id));
        return t;
    }

    /** Child 0 is the photo, child 1 the tick badge. */
    private void styleTick(FrameLayout tile, boolean on) {
        tile.getChildAt(0).setAlpha(on ? 1f : 0.45f);
        TextView badge = (TextView) tile.getChildAt(1);
        badge.setText(on ? "✓" : "");
        badge.setBackground(on ? ui.round(Ui.ACCENT, 17) : ui.outlined(Color.argb(90, 0, 0, 0), 17, Color.WHITE, 2));
        tile.setContentDescription(on ? "Ticked photo" : "Photo, not ticked");
    }

    private void toggle(String id) {
        Set<String> t = store.ticked();
        boolean on = !t.remove(id);
        if (on) t.add(id);
        store.setTicked(t);
        ticked = t;
        View tile = grid.findViewWithTag(id);
        if (tile instanceof FrameLayout) styleTick((FrameLayout) tile, on);
        updateCount();
    }

    private void updateCount() {
        int n = 0;
        for (String id : ids) if (ticked.contains(id)) n++;
        photosLabel.setText(("Photos · " + n + " ticked of " + ids.size()).toUpperCase());
    }

    private void confirmRemove(String id) {
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Remove this photo?")
                .setMessage("It is deleted from the TV. You can upload it again from the phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, which) -> worker.execute(() -> {
                    boolean ok = photos.delete(id);
                    if (ok) {
                        specialDays.forgetPhoto(id);
                        Set<String> t = store.ticked();
                        t.remove(id);
                        store.setTicked(t);
                    } else {
                        Log.w(TAG, "could not delete " + id);
                    }
                    main.post(() -> reload(neighbour(id)));
                }))
                .show();
    }

    /** The photo next to one being removed, to keep focus nearby. */
    private String neighbour(String id) {
        int i = ids.indexOf(id);
        if (i < 0) return null;
        if (i + 1 < ids.size()) return ids.get(i + 1);
        return i > 0 ? ids.get(i - 1) : null;
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
    public void onDestroy() {
        onHidden();
        worker.shutdownNow();
        synchronized (thumbs) {
            thumbs.clear();
        }
    }
}
