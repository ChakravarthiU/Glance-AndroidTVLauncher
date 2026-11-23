package com.chakriau.tvhome.v2.festive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Festive lights along the top edge of Home (Requirements.md 5.3; mockup {@code design/theme1-home.png}): strings
 * draped in three swags across the screen, with coloured bulbs that blink on and off, each at its own pace. Decorative only:
 * never focusable, invisible to accessibility. Animates only between {@link #start} and {@link #stop}, at about 15
 * frames a second; the glows are made once per size, so a frame allocates nothing.
 */
public final class LightsView extends View {

    private static final long FRAME_MS = 66;
    private static final int[] COLORS = {
            0xFFFFD34D, 0xFFFF5C7A, 0xFF4BE3A0, 0xFF4FA8FF, 0xFFFF9A3D, 0xFFB98BFF};
    /** Design sizes, in the mockup's 1920-wide pixels. */
    private static final float SWAGS = 3, SAG = 72, SPACING = 56, BULB_W = 19, BULB_H = 27, GLOW = 36;
    /** Twinkling: each bulb is lit for this share of its own cycle, then off, fading over FADE_MS. */
    private static final float LIT = 0.68f;
    private static final float FADE_MS = 180;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cord = new Path();
    private final Random random = new Random();
    private float[] bx = new float[0], by = new float[0];
    private int[] color = new int[0];
    private float[] period = new float[0], phase = new float[0];
    private Shader[] glows = new Shader[0];
    private float scale = 1;
    private boolean active;

    public LightsView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void start() {
        if (active) return;
        active = true;
        invalidate();
    }

    public void stop() {
        active = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) return;
        scale = w / 1920f;
        build(w);
    }

    /** The cord's swags and the bulbs along it, spaced evenly by length. */
    private void build(int w) {
        cord.reset();
        float swagW = w / SWAGS;
        float top = 14 * scale, sag = SAG * scale;
        cord.moveTo(0, top + 6 * scale);
        for (int i = 0; i < SWAGS; i++) {
            float x0 = i * swagW, x1 = x0 + swagW;
            float yEnd = i == SWAGS - 1 ? top + 6 * scale : top;
            // A quadratic whose low point is the sag: the control point sits twice as deep.
            cord.quadTo((x0 + x1) / 2, top + sag * 2, x1, yEnd);
        }
        PathMeasure pm = new PathMeasure(cord, false);
        float length = pm.getLength();
        int n = Math.max(6, Math.round(length / (SPACING * scale)));
        bx = new float[n];
        by = new float[n];
        color = new int[n];
        period = new float[n];
        phase = new float[n];
        glows = new Shader[n];
        float[] pos = new float[2];
        float glow = GLOW * scale;
        for (int i = 0; i < n; i++) {
            pm.getPosTan((i + 0.5f) * length / n, pos, null);
            bx[i] = pos[0];
            by[i] = pos[1];
            color[i] = COLORS[i % COLORS.length];
            period[i] = 1300 + random.nextInt(1500);
            phase[i] = random.nextFloat();   // where in its cycle the bulb starts, so they blink out of step
            float cy = by[i] + (6 + BULB_H / 2) * scale;
            int c = color[i];
            glows[i] = new RadialGradient(bx[i], cy, glow,
                    new int[]{(c & 0x00FFFFFF) | 0xBB000000, (c & 0x00FFFFFF) | 0x44000000, c & 0x00FFFFFF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bx.length == 0) return;
        long now = SystemClock.uptimeMillis();

        // The cord.
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f * scale);
        paint.setColor(0xCC2A2F3A);
        canvas.drawPath(cord, paint);
        paint.setStyle(Paint.Style.FILL);

        float bw = BULB_W * scale, bh = BULB_H * scale, cap = 6 * scale, glow = GLOW * scale;
        for (int i = 0; i < bx.length; i++) {
            // Each bulb blinks on and off at its own pace: lit for most of its cycle, then dark for a moment.
            float bright = lit(now, period[i], phase[i]);
            float x = bx[i], y = by[i], cy = y + cap + bh / 2;

            if (bright > 0.02f) {
                glowPaint.setShader(glows[i]);
                glowPaint.setAlpha(Math.round(255 * bright));
                canvas.drawCircle(x, cy, glow, glowPaint);
            }

            paint.setColor(0xFF3A3F4A);   // the socket on the cord
            canvas.drawRect(x - bw * 0.28f, y, x + bw * 0.28f, y + cap + 1, paint);
            // Off, the glass shows its colour darkly; lit, it shines in full.
            paint.setColor(mixDark(color[i], 0.55f * (1f - bright)));
            canvas.drawOval(x - bw / 2, y + cap, x + bw / 2, y + cap + bh, paint);
            paint.setColor(0xFFFFFFFF);   // a highlight
            paint.setAlpha(Math.round(40 + 140 * bright));
            canvas.drawOval(x - bw * 0.26f, y + cap + bh * 0.18f, x - bw * 0.02f, y + cap + bh * 0.48f, paint);
            paint.setAlpha(255);
        }
        if (active) postInvalidateDelayed(FRAME_MS);
    }

    /** 0 (off) to 1 (lit) for a bulb at {@code now}: on for {@link #LIT} of the cycle, with soft fades. */
    static float lit(long now, float periodMs, float phase) {
        float t = ((now / periodMs) + phase) % 1f;   // 0..1 through the cycle
        float off = LIT, fade = FADE_MS / periodMs;
        if (t < fade) return t / fade;                               // coming on
        if (t < off) return 1f;                                      // lit
        if (t < off + fade) return 1f - (t - off) / fade;            // going off
        return 0f;                                                   // dark
    }

    /** {@code c} darkened by {@code amount} (0 to 1). */
    private static int mixDark(int c, float amount) {
        float k = 1f - amount;
        return 0xFF000000 | (Math.round(((c >> 16) & 0xFF) * k) << 16) | (Math.round(((c >> 8) & 0xFF) * k) << 8)
                | Math.round((c & 0xFF) * k);
    }
}
