package com.chakriau.tvhome.v2.sky;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

import java.io.InputStream;

/**
 * The sun and the moon as pictures (Requirements.md 5.10), made once and kept until they change, so a frame only
 * draws two bitmaps (Requirements.md principle 4: the blur and gradients cost nothing while animating).
 *
 * <ul>
 *   <li><b>Sun</b>: a white-hot disc fading to gold at its edge, an inner bloom and a wide faint halo; and a separate
 *       picture of twelve soft rays, which {@link SkyView} turns slowly. Near the horizon it is larger, oranger and
 *       its rays shorter ({@link SkyMath#warmth}, in 8 steps, so it is remade at most 8 times a half-day).</li>
 *   <li><b>Moon</b>: the surface ({@code assets/sky/moon.webp}, made by {@code design/sky/make_moon.py}) with its
 *       phase: the unlit part fades almost clear, so the sky shows through with a faint earthshine, behind a soft
 *       terminator. Remade when the phase moves by 1% (about every 7 hours).</li>
 * </ul>
 * The recipes match {@code design/sky/preview.html}; keep the two in step.
 */
final class SkyArt {

    private static final String TAG = "TvHomeSky";
    /** Design sizes (1920-wide pixels). */
    static final float SUN_R = 46, MOON_R = 40;
    /** How much of the moon's surface still shows on its unlit side (earthshine). */
    private static final float EARTHSHINE = 0.16f;

    private final Context context;
    private final float scale;
    private Bitmap sun, rays, moon, moonSurface;
    private int sunStep = -1, moonStep = -1;

    SkyArt(Context context, float scale) {
        this.context = context.getApplicationContext();
        this.scale = scale;
    }

    /** The sun (disc, bloom and halo) for this warmth, centred on its middle. */
    Bitmap sun(float warmth) {
        int step = Math.round(warmth * 8);
        if (sun == null || step != sunStep) makeSun(step);
        return sun;
    }

    /** The sun's rays for the same warmth, to be turned around their middle. */
    Bitmap rays(float warmth) {
        sun(warmth);
        return rays;
    }

    /** The moon in this phase (0 new, 0.5 full), with a little room around it for the soft edge. */
    Bitmap moon(float phase) {
        int step = Math.round(phase * 100) % 100;
        if (moon == null || step != moonStep) makeMoon(step);
        return moon;
    }

    /** Lets every picture go (Home hidden and memory short); they are made again when next drawn. */
    void release() {
        sun = rays = moon = moonSurface = null;
        sunStep = moonStep = -1;
    }

    // ---- Sun ------------------------------------------------------------------------------------------------------

    private void makeSun(int step) {
        float w = step / 8f;
        float r = SUN_R * scale * (1 + 0.25f * w);
        int core = mix(Color.rgb(255, 252, 240), Color.rgb(255, 196, 140), w);
        int edge = mix(Color.rgb(255, 214, 120), Color.rgb(255, 120, 60), w);
        int halo = mix(Color.rgb(255, 214, 140), Color.rgb(255, 110, 60), w);

        int size = Math.max(4, Math.round(r * 9));
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float m = size / 2f;
        // The wide halo.
        p.setShader(new RadialGradient(m, m, r * 4.4f,
                new int[]{alpha(halo, 0f), alpha(halo, lerp(0.30f, 0.42f, w)), alpha(halo, lerp(0.10f, 0.18f, w)),
                        alpha(halo, 0f)},
                new float[]{0f, 0.8f / 4.4f, 0.8f / 4.4f + 0.35f * (1 - 0.8f / 4.4f), 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(m, m, r * 4.4f, p);
        // The inner bloom.
        p.setShader(new RadialGradient(m, m, r * 1.9f, new int[]{alpha(core, lerp(0.75f, 0.55f, w)),
                alpha(core, lerp(0.75f, 0.55f, w)), alpha(core, 0f)}, new float[]{0f, 0.9f / 1.9f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(m, m, r * 1.9f, p);
        // The disc: white-hot a little above and left of its centre, warmer towards its edge.
        p.setShader(new RadialGradient(m - r * 0.15f, m - r * 0.15f, r * 1.15f,
                new int[]{core, mix(core, edge, 0.35f), edge}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(m, m, r, p);
        p.setShader(null);
        sun = b;

        float len = r * lerp(3.6f, 2.4f, w);
        int rsize = Math.max(4, Math.round(len * 2 + 4));
        Bitmap rb = Bitmap.createBitmap(rsize, rsize, Bitmap.Config.ARGB_8888);
        Canvas rc = new Canvas(rb);
        float rm = rsize / 2f;
        int ray = mix(Color.rgb(255, 236, 190), Color.rgb(255, 160, 100), w);
        Path path = new Path();
        for (int i = 0; i < 12; i++) {
            float l = len * (i % 2 == 1 ? 0.7f : 1f), half = r * (i % 2 == 1 ? 0.16f : 0.22f);
            rc.save();
            rc.rotate(i * 30f, rm, rm);
            p.setShader(new LinearGradient(rm + r * 0.9f, rm, rm + l, rm, alpha(ray, lerp(0.22f, 0.14f, w)),
                    alpha(ray, 0f), Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(rm + r * 0.9f, rm - half);
            path.lineTo(rm + l, rm);
            path.lineTo(rm + r * 0.9f, rm + half);
            path.close();
            rc.drawPath(path, p);
            rc.restore();
        }
        rays = rb;
        sunStep = step;
    }

    // ---- Moon -----------------------------------------------------------------------------------------------------

    private void makeMoon(int step) {
        float r = MOON_R * scale, pad = r * 0.2f;
        int size = Math.max(4, Math.round((r + pad) * 2));
        float m = size / 2f;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap surface = surface();
        RectF disc = new RectF(m - r, m - r, m + r, m + r);
        if (surface != null) {
            c.drawBitmap(surface, null, disc, p);
        } else {   // no picture: a plain grey disc
            p.setColor(Color.rgb(200, 202, 208));
            c.drawCircle(m, m, r, p);
        }

        // The unlit part fades almost clear: a shadow layer with the lit part cut out (soft edge), taken off the moon.
        Bitmap shadow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(shadow);
        sc.drawColor(Color.argb(Math.round(255 * (1 - EARTHSHINE)), 0, 0, 0));
        float phase = step / 100f;
        boolean waxing = phase <= 0.5f;
        float f = waxing ? phase : 1f - phase;   // 0 new .. 0.5 full
        if (f >= 0.02f) {
            Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
            cut.setColor(Color.BLACK);   // fully opaque, so the lit part is fully cleared
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            cut.setMaskFilter(new BlurMaskFilter(Math.max(1f, r * 0.07f), BlurMaskFilter.Blur.NORMAL));
            sc.save();
            if (!waxing) sc.scale(-1f, 1f, m, m);   // waning: lit on the left
            sc.drawPath(litPath(m, m, r, f), cut);
            sc.restore();
        }
        Paint take = new Paint();
        take.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        c.drawBitmap(shadow, 0, 0, take);
        shadow.recycle();
        moon = b;
        moonStep = step;
    }

    /**
     * The lit part of a waxing moon (lit on the right): the right half of the disc and, back up along the terminator,
     * half an ellipse whose width follows the phase: bulging right for a crescent, left for a gibbous moon.
     */
    private static Path litPath(float cx, float cy, float r, float f) {
        float rx = r * (float) Math.abs(Math.cos(2 * Math.PI * f));
        Path path = new Path();
        path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 180, true);   // top to bottom on the right
        path.arcTo(new RectF(cx - rx, cy - r, cx + rx, cy + r), 90, f < 0.25f ? -180 : 180, false);
        path.close();
        return path;
    }

    private Bitmap surface() {
        if (moonSurface != null) return moonSurface;
        try (InputStream in = context.getAssets().open("sky/moon.webp")) {
            moonSurface = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.w(TAG, "no moon picture: " + e);
        }
        return moonSurface;
    }

    // ---- Colour helpers -------------------------------------------------------------------------------------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(lerp(Color.red(a), Color.red(b), t)),
                Math.round(lerp(Color.green(a), Color.green(b), t)),
                Math.round(lerp(Color.blue(a), Color.blue(b), t)));
    }

    private static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * Math.max(0f, Math.min(1f, a))), Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
