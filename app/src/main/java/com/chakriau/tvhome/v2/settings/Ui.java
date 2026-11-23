package com.chakriau.tvhome.v2.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.chakriau.tvhome.v2.R;
import com.chakriau.tvhome.v2.ui.Fonts;

/**
 * Shared look of the launcher's Settings (mockup {@code design/theme1-settings-apps.png}). Sizes are given in the
 * mockup's 1920 × 1080 pixels and scaled to the real screen, so the screen matches the mockup on any TV.
 */
final class Ui {

    static final int BG = Color.rgb(14, 15, 19);
    static final int MENU_BG = Color.rgb(21, 22, 28);
    static final int CARD = Color.rgb(28, 30, 37);
    static final int CARD_FOCUSED = Color.rgb(37, 40, 51);
    static final int PANEL = Color.rgb(35, 37, 45);
    static final int CHIP = Color.rgb(42, 45, 54);
    static final int SELECTED_MENU = Color.rgb(38, 41, 53);
    static final int ACCENT = Color.rgb(108, 198, 245);
    static final int TEXT = Color.WHITE;
    static final int TEXT_DARK = Color.rgb(16, 17, 22);
    static final int TEXT_2 = Color.rgb(169, 173, 184);
    static final int TEXT_3 = Color.rgb(112, 116, 128);
    static final int LINE = Color.rgb(52, 55, 66);

    final Context context;
    private final float scale;

    Ui(Context context) {
        this.context = context;
        int w = context.getResources().getDisplayMetrics().widthPixels;
        int h = context.getResources().getDisplayMetrics().heightPixels;
        scale = Math.max(w, h) / 1920f;
    }

    /** Mockup pixels to screen pixels. */
    int px(float designPx) {
        return Math.round(designPx * scale);
    }

    /** Inter Bold or Regular. */
    Typeface font(boolean bold) {
        return Fonts.get(context, bold ? Fonts.Weight.BOLD : Fonts.Weight.REGULAR);
    }

    TextView text(String s, float sizePx, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizePx));
        t.setTextColor(color);
        t.setTypeface(font(bold));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small capitals with letter spacing, like "SETTINGS" and the breadcrumb. */
    TextView caps(String s, float sizePx, int color) {
        TextView t = text(s.toUpperCase(), sizePx, color, false);
        t.setLetterSpacing(0.14f);
        return t;
    }

    GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radiusPx));
        return d;
    }

    GradientDrawable outlined(int fill, float radiusPx, int stroke, float strokePx) {
        GradientDrawable d = round(fill, radiusPx);
        d.setStroke(px(strokePx), stroke);
        return d;
    }

    GradientDrawable dashed(float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setCornerRadius(px(radiusPx));
        d.setStroke(px(2), LINE, px(10), px(7));
        return d;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    /**
     * A plain setting row: title (and a note under it) on the left, a value on the right. Focusable when it has an
     * action. Used by the System page and the pages still to come.
     */
    View settingRow(String title, String note, String value, View.OnClickListener action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(30), px(18), px(26), px(18));
        row.setMinimumHeight(px(84));
        row.setBackground(round(CARD, 14));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 30, TEXT, false);
        texts.addView(t);
        TextView n = null;
        if (note != null) {
            n = text(note, 22, TEXT_2, false);
            texts.addView(n, lp(WRAP, WRAP, px(8)));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView v = null;
        if (value != null) {
            v = text(value, 22, TEXT, false);
            v.setPadding(px(16), px(9), px(16), px(9));
            v.setBackground(round(CHIP, 10));
            row.addView(v, lp(WRAP, WRAP));
        }
        row.setTag(v);   // the value, for pages that change it in place (valueOf)
        if (action != null) {
            row.setFocusable(true);
            row.setOnClickListener(action);
            row.setOnFocusChangeListener((view, has) ->
                    view.setBackground(has ? outlined(CARD_FOCUSED, 14, ACCENT, 4) : round(CARD, 14)));
        } else {
            t.setTextColor(TEXT_2);
            if (n != null) n.setTextColor(TEXT_3);
            if (v != null) v.setTextColor(TEXT_2);
        }
        return row;
    }

    // ---- Controls: radio-button toggles and dropdowns ------------------------------------------------------------

    interface BoolGet {
        boolean get();
    }

    interface BoolSet {
        void set(boolean on);
    }

    interface IntGet {
        int get();
    }

    interface IntSet {
        void set(int index);
    }

    /**
     * An on/off setting: title and note on the left, "On" or "Off" with a radio button on the right. OK flips it;
     * {@code after} runs once it has changed (null for nothing).
     */
    View toggleRow(String title, String note, BoolGet get, BoolSet set, Runnable after) {
        LinearLayout row = baseRow(title, note);
        TextView state = text("", 22, TEXT_2, false);
        state.setPadding(0, 0, px(16), 0);
        row.addView(state, lp(WRAP, WRAP));
        View dot = new View(context);
        FrameLayout ring = new FrameLayout(context);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(px(18), px(18), Gravity.CENTER);
        ring.addView(dot, dlp);
        row.addView(ring, lp(px(38), px(38)));
        Runnable update = () -> {
            boolean on = get.get();
            state.setText(on ? "On" : "Off");
            ring.setBackground(oval(Color.TRANSPARENT, on ? ACCENT : TEXT_2, 3));
            dot.setBackground(oval(on ? ACCENT : Color.TRANSPARENT, Color.TRANSPARENT, 0));
            row.setContentDescription(title + ", " + (on ? "on" : "off"));
        };
        update.run();
        row.setTag(R.id.tag_update, update);
        focusable(row, v -> {
            set.set(!get.get());
            update.run();
            if (after != null) after.run();
        });
        return row;
    }

    /**
     * A setting with several choices: title and note on the left, the current choice with a dropdown arrow on the
     * right. OK opens the list under the row; OK on a choice picks it, Back closes the list.
     */
    View choiceRow(String title, String note, String[] labels, IntGet get, IntSet set, Runnable after) {
        LinearLayout row = baseRow(title, note);
        TextView value = text("", 22, TEXT, false);
        value.setPadding(px(16), px(9), px(10), px(9));
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(round(CHIP, 10));
        chip.addView(value);
        ImageView arrow = new ImageView(context);
        arrow.setImageDrawable(new Chevron(TEXT_2));
        arrow.setPadding(0, 0, px(14), 0);
        chip.addView(arrow, lp(px(34), px(20)));
        row.addView(chip, lp(WRAP, WRAP));
        Runnable update = () -> {
            int i = get.get();
            String label = i >= 0 && i < labels.length ? labels[i] : "";
            value.setText(label);
            row.setContentDescription(title + ", " + label);
        };
        update.run();
        row.setTag(R.id.tag_update, update);
        focusable(row, v -> dropdown(row, labels, get.get(), i -> {
            set.set(i);
            update.run();
            if (after != null) after.run();
        }));
        return row;
    }

    /** Shows every control's current value again (after a change made elsewhere, such as the phone). */
    static void refreshAll(View v) {
        Object u = v.getTag(R.id.tag_update);
        if (u instanceof Runnable) ((Runnable) u).run();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refreshAll(g.getChildAt(i));
        }
    }

    /** The list of choices, opened under (or, near the bottom of the screen, over) the row. */
    void dropdown(View anchor, String[] labels, int selected, IntSet pick) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(10), px(10), px(10), px(10));
        list.setBackground(outlined(PANEL, 16, LINE, 2));
        PopupWindow[] popup = new PopupWindow[1];
        View focus = null;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(px(18), px(14), px(22), px(14));
            item.setFocusable(true);
            FrameLayout ring = new FrameLayout(context);
            View dot = new View(context);
            ring.addView(dot, new FrameLayout.LayoutParams(px(14), px(14), Gravity.CENTER));
            item.addView(ring, lp(px(30), px(30)));
            TextView t = text(labels[i], 26, TEXT, i == selected);
            t.setPadding(px(18), 0, 0, 0);
            item.addView(t, lp(WRAP, WRAP));
            boolean chosen = i == selected;
            item.setOnFocusChangeListener((v, has) -> {
                item.setBackground(has ? round(TEXT, 12) : null);
                t.setTextColor(has ? TEXT_DARK : TEXT);
                int c = has ? TEXT_DARK : chosen ? ACCENT : TEXT_2;
                ring.setBackground(oval(Color.TRANSPARENT, c, 3));
                dot.setBackground(oval(chosen ? c : Color.TRANSPARENT, Color.TRANSPARENT, 0));
            });
            ring.setBackground(oval(Color.TRANSPARENT, chosen ? ACCENT : TEXT_2, 3));
            dot.setBackground(oval(chosen ? ACCENT : Color.TRANSPARENT, Color.TRANSPARENT, 0));
            item.setOnClickListener(v -> {
                if (popup[0] != null) popup[0].dismiss();
                pick.set(index);
            });
            list.addView(item, lp(MATCH, WRAP, i == 0 ? 0 : px(4)));
            if (chosen || focus == null) focus = item;
        }
        int w = Math.max(px(380), anchor.getWidth() / 3);
        popup[0] = new PopupWindow(list, w, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup[0].setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // lets Back close it
        popup[0].setOutsideTouchable(true);
        popup[0].setElevation(px(20));
        popup[0].showAsDropDown(anchor, anchor.getWidth() - w, px(6));
        View f = focus;
        if (f != null) list.post(f::requestFocus);
    }

    private LinearLayout baseRow(String title, String note) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(30), px(18), px(26), px(18));
        row.setMinimumHeight(px(84));
        row.setBackground(round(CARD, 14));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 30, TEXT, false));
        if (note != null) texts.addView(text(note, 22, TEXT_2, false), lp(WRAP, WRAP, px(8)));
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1));
        return row;
    }

    private void focusable(View row, View.OnClickListener action) {
        row.setFocusable(true);
        row.setOnClickListener(action);
        row.setOnFocusChangeListener((view, has) ->
                view.setBackground(has ? outlined(CARD_FOCUSED, 14, ACCENT, 4) : round(CARD, 14)));
    }

    private GradientDrawable oval(int fill, int stroke, float strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (strokePx > 0) d.setStroke(px(strokePx), stroke);
        return d;
    }

    /** A small downward arrow for dropdowns (drawn, so it needs no font glyph). */
    private final class Chevron extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Chevron(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            path.reset();
            path.moveTo(cx - s * 0.32f, cy - s * 0.16f);
            path.lineTo(cx, cy + s * 0.18f);
            path.lineTo(cx + s * 0.32f, cy - s * 0.16f);
            c.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The value label of a {@link #settingRow} (null if it has none). */
    static TextView valueOf(View row) {
        Object t = row.getTag();
        return t instanceof TextView ? (TextView) t : null;
    }

    /** A page's title and one-line description. */
    LinearLayout pageHeader(String title, String description) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(title, 64, TEXT, true));
        if (description != null) box.addView(text(description, 26, TEXT_2, false), lp(WRAP, WRAP, px(22)));
        return box;
    }

    static boolean isInside(View v, View ancestor) {
        for (View cur = v; cur != null; ) {
            if (cur == ancestor) return true;
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return false;
    }
}
