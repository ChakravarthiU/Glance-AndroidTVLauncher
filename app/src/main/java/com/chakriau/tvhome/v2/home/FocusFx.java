package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The focused app's highlight on Home: a border whose colours (the app's own colour through lighter tints to white)
 * turn around the tile, and sparkles that burst from the centre of the tile and fade before its edge. Drawn in front
 * of the tile (its elevation is above the tile's face). Animates only while its tile is focused and Home is visible
 * ({@link #setPaused}); a frame allocates nothing.
 */
final class FocusFx extends View {

    /** Home hidden: every highlight stops (Requirements.md principle 4). */
    private static boolean paused;
    private static final long TURN_MS = 2600;   // one full turn of the border's colours
    private static final long SPARK_MS = 900;   // a sparkle's flight
    private static final long EMIT_MS = 110;    // one new sparkle this often
    private static final int MAX = 16;

    private final float pad, radius, stroke;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix turn = new Matrix();
    private final RectF box = new RectF();
    private final Path star = new Path();
    private final Random random = new Random();
    private SweepGradient sweep;
    private int color = 0xFF6CC6F5;
    private boolean on;
    private long lastEmit;

    // Sparkles: start time (0 = free), direction, how far, size, colour.
    private final long[] born = new long[MAX];
    private final float[] dx = new float[MAX], dy = new float[MAX], dist = new float[MAX], size = new float[MAX];
    private final int[] tint = new int[MAX];

    /**
     * @param pad    how far the layer reaches past the tile on each side (it must be laid out that much bigger)
     * @param radius the tile's corner radius
     * @param stroke the border's width
     */
    FocusFx(Context c, float pad, float radius, float stroke) {
        super(c);
        this.pad = pad;
        this.radius = radius;
        this.stroke = stroke;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        spark.setStyle(Paint.Style.FILL);
        setVisibility(INVISIBLE);
    }

    static void setPaused(boolean p) {
        paused = p;
    }

    /** The app's own colour, for the border and some of the sparkles. */
    void setColor(int c) {
        color = c | 0xFF000000;
        sweep = null;
        invalidate();
    }

    void setFocused(boolean f) {
        on = f;
        if (f) {
            for (int i = 0; i < MAX; i++) born[i] = 0;
            lastEmit = 0;
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sweep = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        boolean alive = false;

        if (on) {
            if (sweep == null) {
                int light = mix(color, Color.WHITE, 0.5f), pale = mix(color, Color.WHITE, 0.8f);
                sweep = new SweepGradient(cx, cy, new int[]{color, light, Color.WHITE, pale, color, light, Color.WHITE,
                        pale, color}, null);
            }
            turn.setRotate(360f * (now % TURN_MS) / TURN_MS, cx, cy);
            sweep.setLocalMatrix(turn);
            ring.setShader(sweep);
            float inset = pad + stroke / 2f;
            box.set(inset, inset, w - inset, h - inset);
            canvas.drawRoundRect(box, radius, radius, ring);

            // A new sparkle every so often, in a random direction, flying just past the tile's edge.
            if (!paused && now - lastEmit >= EMIT_MS) {
                lastEmit = now;
                for (int i = 0; i < MAX; i++) {
                    if (born[i] != 0) continue;
                    double a = random.nextDouble() * Math.PI * 2;
                    dx[i] = (float) Math.cos(a);
                    dy[i] = (float) Math.sin(a);
                    // How far the tile's edge is in that direction; the sparkle stops short of it (and the border).
                    float edge = Math.min(Math.abs((cx - pad) / Math.max(0.01f, Math.abs(dx[i]))),
                            Math.abs((cy - pad) / Math.max(0.01f, Math.abs(dy[i]))));
                    // Small (about 9 to 17 px across at 1080p), flying nearly to the edge as they fade.
                    size[i] = stroke * (0.45f + random.nextFloat() * 0.4f);
                    float room = Math.max(0, edge - size[i] * 2.4f - stroke * 1.2f);
                    dist[i] = room * (0.85f + random.nextFloat() * 0.15f);
                    tint[i] = random.nextInt(3) == 0 ? mix(color, Color.WHITE, 0.4f) : Color.WHITE;
                    born[i] = now;
                    break;
                }
            }
        }

        for (int i = 0; i < MAX; i++) {
            if (born[i] == 0) continue;
            float t = (now - born[i]) / (float) SPARK_MS;
            if (t >= 1f) {
                born[i] = 0;
                continue;
            }
            alive = true;
            float ease = 1f - (1f - t) * (1f - t);   // fast out of the centre, slowing down
            float x = cx + dx[i] * dist[i] * ease, y = cy + dy[i] * dist[i] * ease;
            float s = size[i] * (t < 0.2f ? t / 0.2f : 1f - (t - 0.2f) * 0.6f);
            float alpha = t < 0.15f ? 1f : 1f - (t - 0.15f) / 0.85f;   // fading all the way out
            // A darker halo first, so a white sparkle still shows over a light banner.
            spark.setColor(mix(color, Color.BLACK, 0.55f));
            spark.setAlpha(Math.round(150 * alpha));
            drawStar(canvas, x, y, s * 1.35f);
            spark.setColor(tint[i]);
            spark.setAlpha(Math.round(255 * alpha));
            drawStar(canvas, x, y, s);
        }

        if ((on && !paused) || alive) postInvalidateOnAnimation();
        else if (!on) setVisibility(INVISIBLE);
    }

    private void drawStar(Canvas canvas, float x, float y, float s) {
        star.reset();
        star.moveTo(x, y - s * 2);
        star.quadTo(x, y, x + s * 2, y);
        star.quadTo(x, y, x, y + s * 2);
        star.quadTo(x, y, x - s * 2, y);
        star.quadTo(x, y, x, y - s * 2);
        star.close();
        canvas.drawPath(star, spark);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /**
     * An app's own colour from its banner or icon: the average of its more colourful pixels, made bright enough to
     * read as a border; a soft blue when the art is grey. Small (24 × 14 samples), done once per tile.
     */
    static int colorOf(Drawable d) {
        if (d == null) return 0xFF6CC6F5;
        int w = 24, h = 14;
        Bitmap b;
        try {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Rect old = new Rect(d.getBounds());
            d.setBounds(0, 0, w, h);
            d.draw(c);
            d.setBounds(old);
        } catch (RuntimeException e) {
            return 0xFF6CC6F5;
        }
        long r = 0, g = 0, bl = 0, n = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue;   // skip greys, blacks and whites
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
        }
        b.recycle();
        if (n < 6) return 0xFF6CC6F5;
        int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.max(hsv[2], 0.85f);
        return Color.HSVToColor(hsv);
    }
}
