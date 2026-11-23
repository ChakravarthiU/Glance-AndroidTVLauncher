package com.chakriau.tvhome.v2.home;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.ui.Fonts;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What every part of a Home theme shares: the activity, the design scale (themes are drawn in 1920 × 1080 design
 * pixels and scaled to the screen, Requirements.md 2), text in the theme's fonts, one background worker thread and
 * the main-thread handler. A theme makes one and hands it to the parts it uses ({@link ClockWeather},
 * {@link TopButtons}, {@link AppTiles}, {@link WatchingRowView}, {@link HomeDecor}).
 */
public final class HomeKit {

    /** Text on Home: white, and a softer white for notes. */
    public static final int TEXT = Color.WHITE;
    public static final int TEXT_2 = Color.argb(200, 220, 226, 240);
    /** Dark text on a focused (white) button or row. */
    public static final int TEXT_ON_WHITE = Color.rgb(16, 20, 36);

    public final Activity activity;
    public final HomeTheme.Actions actions;
    /** Screen pixels per design pixel. */
    public final float scale;
    public final Handler main = new Handler(Looper.getMainLooper());
    /** One thread for the parts' slow work (network status, weather); shut down in {@link #shutdown()}. */
    public final ExecutorService worker = Executors.newSingleThreadExecutor();

    public HomeKit(Activity activity, HomeTheme.Actions actions) {
        this.activity = activity;
        this.actions = actions;
        int w = activity.getResources().getDisplayMetrics().widthPixels;
        int h = activity.getResources().getDisplayMetrics().heightPixels;
        scale = Math.max(w, h) / 1920f;
    }

    /** Design pixels to screen pixels. */
    public int px(float designPx) {
        return Math.round(designPx * scale);
    }

    /** A line no thinner than one screen pixel. */
    public int line(float designPx) {
        return Math.max(1, px(designPx));
    }

    /** Text in the bundled Inter, sized in design pixels, with Home's soft shadow so it reads over any photo. */
    public TextView text(String s, float sizePx, int color, Fonts.Weight weight) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizePx));
        t.setTextColor(color);
        t.setTypeface(Fonts.get(activity, weight));
        t.setIncludeFontPadding(false);
        t.setShadowLayer(px(6), 0, px(1), Color.argb(90, 0, 0, 0));
        return t;
    }

    public static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Whether {@code v} is {@code ancestor} or inside it. */
    public static boolean isIn(View v, View ancestor) {
        for (View cur = v; cur != null; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            if (cur == ancestor) return true;
        }
        return false;
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    /** A box in design pixels (left, top, right, bottom), for placing parts on Home. */
    public static final class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }
    }
}
