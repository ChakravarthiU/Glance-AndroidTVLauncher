package com.chakriau.tvhome.v2.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * One line of text whose changed characters roll up when it changes (Requirements.md 5.8): the old character slides
 * up and fades out while the new one comes up from below, rightmost first, like an odometer. Used for the top bar's
 * time and AM/PM. Which characters roll, and when, is {@link ClockMath}; this only draws.
 *
 * <p>Inter's digits are proportional ("1" is much narrower than "2"), so the last {@link #setFixedDigits fixed}
 * characters can sit in equal slots as wide as the widest digit, and {@link #reserve} keeps a minimum width: then
 * whatever follows (AM/PM, the date) doesn't shift each minute.
 *
 * <p>Animates for about half a second when the text changes, and only when asked to (Home visible, rolling on).
 * Looks like Home's other text: Inter, the same soft shadow, baseline exposed for baseline-aligned rows.
 */
public final class RollingText extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private String text = "";
    private String before = "";
    private boolean[] changed = new boolean[0];
    private long[] delays = new long[0];
    private long elapsed;
    private ValueAnimator anim;
    private final char[] one = new char[1];
    private int fixedDigits;
    private String[] reserved = new String[0];

    public RollingText(Context context, float sizePx, int color, Typeface typeface, float shadowRadius,
                       float shadowDy) {
        super(context);
        paint.setTextSize(sizePx);
        paint.setColor(color);
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(shadowRadius, 0, shadowDy, Color.argb(90, 0, 0, 0));
        paint.getFontMetrics(fm);
    }

    public void setLetterSpacing(float em) {
        paint.setLetterSpacing(em);
        requestLayout();
    }

    /** The last {@code n} characters each get a slot as wide as the widest digit, glyph centred (the minutes). */
    public void setFixedDigits(int n) {
        fixedDigits = Math.max(0, n);
        requestLayout();
    }

    /** Never narrower than the widest of these texts (e.g. "AM" and "PM"), so what follows stays put. */
    public void reserve(String... samples) {
        reserved = samples == null ? new String[0] : samples;
        requestLayout();
    }

    public String getText() {
        return text;
    }

    /** Shows {@code s}; rolls the characters that changed when {@code animate}, else shows it at once. */
    public void setText(String s, boolean animate) {
        if (s == null) s = "";
        if (s.equals(text)) {
            if (!animate) finish();
            return;
        }
        String old = text;
        boolean widthChanges = layoutWidth(s) != layoutWidth(old);
        cancel();
        before = old;
        text = s;
        setContentDescription(s);
        if (animate && !old.isEmpty() && isAttachedToWindow()) {
            changed = ClockMath.changed(old, s);
            delays = ClockMath.delays(changed);
            elapsed = 0;
            anim = ValueAnimator.ofInt(0, (int) ClockMath.rollLength(changed));
            anim.setDuration(ClockMath.rollLength(changed));
            anim.setInterpolator(new LinearInterpolator());   // easing is per character, in ClockMath
            anim.addUpdateListener(a -> {
                elapsed = (Integer) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        } else {
            finish();
        }
        if (widthChanges) requestLayout();
        invalidate();
    }

    /** Ends any roll and shows the current text still (Home hid, or the text was set without animating). */
    public void finish() {
        cancel();
        before = text;
        changed = new boolean[0];
        invalidate();
    }

    private void cancel() {
        if (anim != null) {
            anim.removeAllUpdateListeners();
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        finish();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float tw = layoutWidth(text);
        for (String r : reserved) if (r != null) tw = Math.max(tw, layoutWidth(r));
        int w = (int) Math.ceil(tw) + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil(fm.descent - fm.ascent) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    public int getBaseline() {
        return getPaddingTop() + Math.round(-fm.ascent);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (text.isEmpty() && before.isEmpty()) return;
        float baseline = getBaseline();
        int alpha = Color.alpha(paint.getColor());
        boolean rolling = anim != null && changed.length > 0;
        if (!rolling) {
            float[] x = centres(text, text.length());
            for (int i = 0; i < text.length(); i++) drawChar(canvas, text.charAt(i), x[i], baseline, alpha);
            return;
        }
        float h = fm.descent - fm.ascent;
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        int n = changed.length;
        float[] oldX = centres(before, n);
        float[] newX = centres(text, n);
        for (int i = 0; i < n; i++) {
            int bi = i - (n - before.length());
            int ai = i - (n - text.length());
            if (!changed[i]) {
                if (ai >= 0) drawChar(canvas, text.charAt(ai), newX[i], baseline, alpha);
                continue;
            }
            float p = ClockMath.rollAt(elapsed, delays[i]);
            float fade = ClockMath.clamp(p);
            if (bi >= 0 && fade < 1f) {
                drawChar(canvas, before.charAt(bi), oldX[i], baseline - p * h, Math.round(alpha * (1f - fade)));
            }
            if (ai >= 0) {
                drawChar(canvas, text.charAt(ai), newX[i], baseline + (1f - p) * h, Math.round(alpha * fade));
            }
        }
        canvas.restore();
        paint.setAlpha(alpha);
    }

    private void drawChar(Canvas canvas, char c, float x, float y, int alpha) {
        if (alpha <= 0 || c == ' ') return;
        paint.setAlpha(alpha);
        one[0] = c;
        canvas.drawText(one, 0, 1, x, y, paint);
    }

    /**
     * The centre x of each of {@code s}'s characters, right-aligned into {@code n} positions (positions before the
     * string's start get 0 and aren't drawn). Measured with the text's own spacing, so it lines up with a TextView.
     */
    private float[] centres(String s, int n) {
        float[] out = new float[n];
        int offset = n - s.length();
        float left = getPaddingLeft();
        int free = freeLength(s);
        for (int j = 0; j < free; j++) {
            float x0 = paint.measureText(s, 0, j);
            float x1 = paint.measureText(s, 0, j + 1);
            if (j + offset >= 0) out[j + offset] = left + (x0 + x1) / 2f;
        }
        float base = left + paint.measureText(s, 0, free);
        float slot = digitSlot();
        for (int j = free; j < s.length(); j++) {
            if (j + offset >= 0) out[j + offset] = base + (j - free + 0.5f) * slot;
        }
        return out;
    }

    /** How many leading characters are laid out naturally (the rest are fixed slots). */
    private int freeLength(String s) {
        return Math.max(0, s.length() - fixedDigits);
    }

    private float digitSlot() {
        float max = 0f;
        for (char c = '0'; c <= '9'; c++) {
            one[0] = c;
            max = Math.max(max, paint.measureText(one, 0, 1));
        }
        return max;
    }

    /** The width {@code s} takes: natural characters, then the fixed slots. */
    private float layoutWidth(String s) {
        if (s.isEmpty()) return 0f;
        int free = freeLength(s);
        return paint.measureText(s, 0, free) + (s.length() - free) * digitSlot();
    }
}
