package com.chakriau.tvhome.v2.sky;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The sky over Home's wallpaper (Requirements.md 5.10): the sun or the moon (in its real phase) crossing from the
 * left edge, where it rises, to the right edge, where it sets; drifting clouds, rain, snow, lightning, fog haze and
 * stars as the weather says. Drawn in an area the theme gives ({@link #setArea}), under the page, never focusable.
 *
 * <p>Built for a 1 GB TV (Requirements.md principle 4): the sun, its rays and the moon are pictures made once
 * ({@link SkyArt}), three small cloud images likewise, plain lines and dots for rain, snow and stars, no blur while
 * animating. Frames only while started and only when something moves: about 30 a second for rain, snow or
 * lightning, 12 for drifting clouds, stars or the sun's slowly turning rays, none for a clear night (the moon moves
 * once a minute, on {@link #setState}).
 */
public final class SkyView extends View {

    private static final long FAST_FRAME_MS = 33, SLOW_FRAME_MS = 83;
    /** One turn of the sun's rays, and their gentle brightening and dimming. */
    private static final long RAYS_TURN_MS = 240_000, RAYS_BREATHE_MS = 12_000;
    private static final int MOON_GLOW = Color.rgb(220, 228, 255);

    private final float scale;
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SkyArt art;
    private float left, top, right, bottom;
    /** The fog haze's gradient, made once per sky area. */
    private LinearGradient hazeShader;
    private SkyState state;
    private boolean active;
    private long lastFrame;

    private Bitmap[] cloudArt = new Bitmap[0];
    private float[] cloudX = new float[0], cloudY = new float[0], cloudSpeed = new float[0];
    private int[] cloudShape = new int[0];
    private float[] dropX = new float[0], dropY = new float[0], dropSpeed = new float[0];
    private float[] flakeX = new float[0], flakeY = new float[0], flakeSpeed = new float[0], flakeR = new float[0],
            flakePhase = new float[0];
    private float[] starX = new float[0], starY = new float[0], starPhase = new float[0];
    private long nextFlash, flashUntil;
    private boolean cloudsGrey;
    /** The wallpaper's brightness across the sky box ({@link SkyMath#skyLight}), or null (dark / built-in). */
    private float[] light;
    private static final int NIGHT_PATCH = Color.rgb(12, 18, 40);
    /** Where the sun or moon was drawn this frame (centre, radius) and how visible, for the cloud in front of it. */
    private float bodyX, bodyY, bodyR, bodyFade;

    /** {@code scale}: screen pixels per design pixel (Home is designed at 1920 × 1080). */
    public SkyView(Context context, float scale) {
        super(context);
        this.scale = scale;
        this.art = new SkyArt(context, scale);
        setFocusable(false);
        setWillNotDraw(false);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** The sky's area in screen pixels: the sun rises at {@code left}, sets at {@code right}. */
    public void setArea(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        hazeShader = null;
        if (state != null) rebuild(state);
        invalidate();
    }

    /** What to show. Particles are made again only when the weather changes, not as the sun moves. */
    public void setState(SkyState s) {
        SkyState was = state;
        state = s;
        if (was == null || was.kind != s.kind || was.position.body != s.position.body) rebuild(s);
        invalidate();
        if (active) scheduleFrame();
    }

    /** The wallpaper changed: how bright it is behind the sky, so the moon can stand out on a pale photo. */
    public void setLight(float[] light) {
        this.light = light;
        invalidate();
    }

    public void start() {
        if (active) return;
        active = true;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    public void stop() {
        active = false;
    }

    /** Frees the cloud images (Home hidden and memory short); made again on the next {@link #setState}. */
    public void release() {
        cloudArt = new Bitmap[0];
        art.release();
        state = null;
    }

    // ---- Building -------------------------------------------------------------------------------------------------

    private float px(float design) {
        return design * scale;
    }

    private void rebuild(SkyState s) {
        if (right <= left || bottom <= top) return;
        SkyMath.Effects fx = s.effects;
        float w = right - left, h = bottom - top;
        if (cloudArt.length == 0 || cloudsGrey != fx.greyClouds) {
            cloudsGrey = fx.greyClouds;
            cloudArt = fx.clouds == 0 ? new Bitmap[0] : new Bitmap[]{
                    cloud(px(300), px(110), fx.greyClouds), cloud(px(220), px(84), fx.greyClouds),
                    cloud(px(380), px(130), fx.greyClouds)};
        }
        int n = fx.clouds;
        cloudX = new float[n];
        cloudY = new float[n];
        cloudSpeed = new float[n];
        cloudShape = new int[n];
        for (int i = 0; i < n; i++) {
            cloudShape[i] = i % 3;
            cloudX[i] = left - px(200) + (w + px(200)) * (i + random.nextFloat() * 0.6f) / n;
            // Kept clear of the top bar above the sky, so its white text never sits on a white cloud.
            cloudY[i] = top + px(40) + random.nextFloat() * h * (fx.haze ? 0.45f : 0.36f);
            cloudSpeed[i] = px(7 + random.nextInt(11));   // design px a second: a slow drift
        }
        n = fx.rain;
        dropX = new float[n];
        dropY = new float[n];
        dropSpeed = new float[n];
        for (int i = 0; i < n; i++) {
            dropX[i] = left + random.nextFloat() * (w + px(120));
            dropY[i] = top + random.nextFloat() * h;
            dropSpeed[i] = px(fx.drizzle ? 380 + random.nextInt(160) : 820 + random.nextInt(360));
        }
        n = fx.snow;
        flakeX = new float[n];
        flakeY = new float[n];
        flakeSpeed = new float[n];
        flakeR = new float[n];
        flakePhase = new float[n];
        for (int i = 0; i < n; i++) {
            flakeX[i] = left + random.nextFloat() * w;
            flakeY[i] = top + random.nextFloat() * h;
            flakeSpeed[i] = px(45 + random.nextInt(70));
            flakeR[i] = px(2.2f + random.nextFloat() * 3f);
            flakePhase[i] = random.nextFloat() * 6.28f;
        }
        n = fx.stars && s.position.body == SkyMath.Body.MOON ? 28 : 0;
        starX = new float[n];
        starY = new float[n];
        starPhase = new float[n];
        for (int i = 0; i < n; i++) {
            starX[i] = left + random.nextFloat() * w;
            starY[i] = top + random.nextFloat() * h * 0.7f;
            starPhase[i] = random.nextFloat();
        }
        nextFlash = SystemClock.uptimeMillis() + 4000 + random.nextInt(8000);
        flashUntil = 0;
    }

    /** A soft cloud: overlapping puffs on a flat base, blurred once into a small image. */
    private Bitmap cloud(float w, float h, boolean grey) {
        int bw = Math.max(1, Math.round(w)), bh = Math.max(1, Math.round(h));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(grey ? Color.rgb(150, 158, 172) : Color.WHITE);
        float blur = h * 0.08f;
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));   // once, on a software canvas
        float base = h * 0.78f;
        c.drawRoundRect(new RectF(w * 0.1f, h * 0.48f, w * 0.9f, base), h * 0.15f, h * 0.15f, p);
        c.drawCircle(w * 0.3f, h * 0.5f, h * 0.26f, p);
        c.drawCircle(w * 0.5f, h * 0.38f, h * 0.32f, p);
        c.drawCircle(w * 0.7f, h * 0.5f, h * 0.24f, p);
        return b;
    }

    // ---- Drawing --------------------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        SkyState s = state;
        if (s == null || right <= left) return;
        long now = SystemClock.uptimeMillis();
        float dt = active ? Math.min(0.2f, (now - lastFrame) / 1000f) : 0f;
        lastFrame = now;
        SkyMath.Effects fx = s.effects;

        drawStars(canvas, now);
        bodyFade = 0f;
        drawBody(canvas, s, now);
        if (fx.haze) drawHaze(canvas);
        if (fx.veil()) drawVeil(canvas, now, fx);
        drawClouds(canvas, dt, fx);
        canvas.save();
        canvas.clipRect(left, top, right, bottom);   // rain and snow start and end inside the sky
        drawRain(canvas, dt, fx);
        drawSnow(canvas, dt, now);
        canvas.restore();
        if (fx.lightning) drawLightning(canvas, now);

        if (active) scheduleFrame();
    }

    /** Whether the sun's rays are on show (they turn, so they need frames). */
    private static boolean raysShowing(SkyState s) {
        return s.position.body == SkyMath.Body.SUN && SkyMath.horizonFade(s.position.progress) * s.effects.bodyAlpha
                > 0.01f;
    }

    private void scheduleFrame() {
        SkyState s = state;
        if (s == null) return;
        if (s.effects.fast()) postInvalidateDelayed(FAST_FRAME_MS);
        else if (s.effects.animated() || raysShowing(s)) postInvalidateDelayed(SLOW_FRAME_MS);
    }

    private void drawBody(Canvas canvas, SkyState s, long now) {
        float p = s.position.progress;
        float horizon = bottom - px(24), peak = top + px(70);
        float inset = px(60);
        float[] at = SkyMath.arc(p, left + inset, right - inset, horizon, peak);
        float alpha = SkyMath.horizonFade(p) * s.effects.bodyAlpha;
        if (alpha <= 0.01f) return;
        bodyX = at[0];
        bodyY = at[1];
        bodyFade = SkyMath.horizonFade(p);
        if (s.position.body == SkyMath.Body.SUN) {
            float warmth = SkyMath.warmth(p);
            bodyR = px(SkyArt.SUN_R) * (1 + 0.25f * warmth);
            Bitmap sun = art.sun(warmth), rays = art.rays(warmth);
            artPaint.setAlpha(Math.round(255 * alpha));
            canvas.drawBitmap(sun, at[0] - sun.getWidth() / 2f, at[1] - sun.getHeight() / 2f, artPaint);
            float breathe = 0.85f + 0.15f * (float) Math.sin(2 * Math.PI * (now % RAYS_BREATHE_MS) / RAYS_BREATHE_MS);
            artPaint.setAlpha(Math.round(255 * alpha * breathe));
            canvas.save();
            canvas.rotate(360f * (now % RAYS_TURN_MS) / RAYS_TURN_MS, at[0], at[1]);
            canvas.drawBitmap(rays, at[0] - rays.getWidth() / 2f, at[1] - rays.getHeight() / 2f, artPaint);
            canvas.restore();
        } else {
            // Over a pale photo, a soft patch of night behind the moon first, so the moon, its dark side and its
            // halo read (Requirements.md 5.10); none over a dark photo.
            float lit = SkyMath.moonLit(s.moonPhase), r = px(SkyArt.MOON_R), glow = r * (2.2f + 1.4f * lit);
            bodyR = r;
            float k = SkyMath.moonBackdrop(SkyMath.lightAt(light, (at[0] - left) / Math.max(1f, right - left)))
                    * alpha;
            if (k > 0.01f) {
                float pr = r * 2.8f;
                paint.setShader(new RadialGradient(at[0], at[1], pr,
                        new int[]{withAlpha(NIGHT_PATCH, Math.round(255 * 0.92f * k)),
                                withAlpha(NIGHT_PATCH, Math.round(255 * 0.88f * k)),
                                withAlpha(NIGHT_PATCH, Math.round(255 * 0.45f * k)),
                                withAlpha(NIGHT_PATCH, Math.round(255 * 0.14f * k)), withAlpha(NIGHT_PATCH, 0)},
                        new float[]{0f, 1.02f / 2.8f, 1.6f / 2.8f, 2.2f / 2.8f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(at[0], at[1], pr, paint);
            }
            // A soft halo, brighter and wider towards full moon (fainter over the patch), then the moon in its phase.
            int haloA = Math.round(255 * (0.10f + 0.22f * lit) * alpha * (1f - 0.6f * k));
            paint.setShader(new RadialGradient(at[0], at[1], glow,
                    new int[]{withAlpha(MOON_GLOW, haloA), withAlpha(MOON_GLOW, haloA), withAlpha(MOON_GLOW, 0)},
                    new float[]{0f, 0.9f * r / glow, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(at[0], at[1], glow, paint);
            paint.setShader(null);
            Bitmap moon = art.moon(s.moonPhase);
            artPaint.setAlpha(Math.round(255 * alpha));
            canvas.drawBitmap(moon, at[0] - moon.getWidth() / 2f, at[1] - moon.getHeight() / 2f, artPaint);
        }
    }

    private void drawStars(Canvas canvas, long now) {
        for (int i = 0; i < starX.length; i++) {
            float t = (now / 2600f + starPhase[i]) % 1f;
            float a = 0.35f + 0.65f * (float) Math.abs(Math.sin(Math.PI * t));
            paint.setColor(withAlpha(Color.WHITE, Math.round(200 * a)));
            canvas.drawCircle(starX[i], starY[i], px(i % 4 == 0 ? 2.4f : 1.6f), paint);
        }
    }

    private void drawHaze(Canvas canvas) {
        if (hazeShader == null) {
            int[] colors = new int[SkyMath.HAZE_ALPHA.length];
            for (int i = 0; i < colors.length; i++) colors[i] = Color.argb(SkyMath.HAZE_ALPHA[i], 220, 226, 235);
            hazeShader = new LinearGradient(0, top, 0, bottom, colors, SkyMath.HAZE_STOPS, Shader.TileMode.CLAMP);
        }
        float h = bottom - top;
        paint.setShader(hazeShader);
        canvas.drawRect(left, top + h * SkyMath.HAZE_STOPS[0], right, bottom, paint);
        paint.setShader(null);
    }

    /**
     * One cloud that stays partly in front of the sun or moon when the weather dims it (17 Nov 2026: a dimmed sun in
     * open sky looked like a fault), riding with it and swaying gently ({@link SkyMath#veilAt}).
     */
    private void drawVeil(Canvas canvas, long now, SkyMath.Effects fx) {
        if (cloudArt.length == 0 || bodyFade <= 0.01f) return;
        Bitmap b = cloudArt[0];
        float[] at = SkyMath.veilAt(bodyX, bodyY, bodyR, b.getWidth(), b.getHeight(), now);
        boolean flash = SystemClock.uptimeMillis() < flashUntil;
        paint.setColor(Color.WHITE);
        paint.setAlpha(Math.round((flash ? 255 : SkyMath.veilAlpha(fx.greyClouds)) * bodyFade));
        canvas.drawBitmap(b, at[0], at[1], paint);
        paint.setAlpha(255);
    }

    private void drawClouds(Canvas canvas, float dt, SkyMath.Effects fx) {
        if (cloudArt.length == 0) return;
        boolean flash = SystemClock.uptimeMillis() < flashUntil;
        paint.setColor(Color.WHITE);
        paint.setAlpha(flash ? 255 : fx.greyClouds ? 225 : 205);
        for (int i = 0; i < cloudX.length; i++) {
            Bitmap b = cloudArt[cloudShape[i] % cloudArt.length];
            cloudX[i] += cloudSpeed[i] * dt;
            if (cloudX[i] > right) cloudX[i] = left - b.getWidth();   // round again from the left
            canvas.drawBitmap(b, cloudX[i], cloudY[i], paint);
        }
        paint.setAlpha(255);
    }

    private void drawRain(Canvas canvas, float dt, SkyMath.Effects fx) {
        if (dropX.length == 0) return;
        float len = px(fx.drizzle ? 14 : 26), slant = 0.18f;
        rainPaint.setStrokeWidth(Math.max(1f, px(fx.drizzle ? 1.6f : 2.2f)));
        float fadeFrom = bottom - (bottom - top) * 0.2f;
        for (int i = 0; i < dropX.length; i++) {
            dropY[i] += dropSpeed[i] * dt;
            dropX[i] -= dropSpeed[i] * dt * slant;
            if (dropY[i] > bottom || dropX[i] < left - px(40)) {
                dropY[i] = top - random.nextFloat() * px(60);
                dropX[i] = left + random.nextFloat() * (right - left + px(120));
            }
            float a = dropY[i] > fadeFrom ? 1f - (dropY[i] - fadeFrom) / (bottom - fadeFrom) : 1f;
            rainPaint.setColor(Color.argb(Math.round((fx.drizzle ? 120 : 170) * a), 200, 215, 240));
            canvas.drawLine(dropX[i], dropY[i], dropX[i] + len * slant, dropY[i] - len, rainPaint);
        }
    }

    private void drawSnow(Canvas canvas, float dt, long now) {
        if (flakeX.length == 0) return;
        float fadeFrom = bottom - (bottom - top) * 0.2f;
        for (int i = 0; i < flakeX.length; i++) {
            flakeY[i] += flakeSpeed[i] * dt;
            if (flakeY[i] > bottom) {
                flakeY[i] = top - flakeR[i];
                flakeX[i] = left + random.nextFloat() * (right - left);
            }
            float sway = (float) Math.sin(now / 900.0 + flakePhase[i]) * px(14);
            float a = flakeY[i] > fadeFrom ? 1f - (flakeY[i] - fadeFrom) / (bottom - fadeFrom) : 1f;
            paint.setColor(Color.argb(Math.round(230 * a), 255, 255, 255));
            canvas.drawCircle(flakeX[i] + sway, flakeY[i], flakeR[i], paint);
        }
    }

    /** Now and then two quick flashes, every 4 to 12 seconds. */
    private void drawLightning(Canvas canvas, long now) {
        if (now >= nextFlash) {
            flashUntil = now + 90;
            nextFlash = now + (random.nextBoolean() ? 160 : 4000 + random.nextInt(8000));
        }
        if (now < flashUntil) {
            // Soft top and bottom edges, so the flash lights the sky rather than showing its box.
            int on = Color.argb(70, 235, 240, 255), off = Color.argb(0, 235, 240, 255);
            paint.setShader(new LinearGradient(0, top, 0, bottom, new int[]{off, on, on, off},
                    new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setShader(null);
        }
    }

    // ---- Colour helpers -------------------------------------------------------------------------------------------

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }
}
