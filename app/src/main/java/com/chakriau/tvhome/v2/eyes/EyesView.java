package com.chakriau.tvhome.v2.eyes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Two pairs of eyes with a personality (Requirements.md 5.2; drawing from V1's {@code GooglyEyesView}). Purely
 * decorative: never focusable, never touched, invisible to accessibility. Idle, each pair glances at random spots,
 * blinks, and now and then the pairs look at each other; while the remote moves they follow the focused item, then
 * drift back to glancing a few seconds after the last key. Animates only between {@link #start} and {@link #stop}.
 *
 * <p>Coordinates are this view's own pixels. The owner places the pairs with {@link #setPairs}.
 */
public final class EyesView extends View {

    /** After the last focus change, how long the eyes keep following before glancing around again. */
    private static final long FOLLOW_MS = 3500;
    private static final long FRAME_MS = 33;
    private static final long IDLE_FRAME_MS = 100;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Path angryEye = new Path(), angryLid = new Path(), angryBrow = new Path();
    private final Path clip = new Path();
    private float radius;

    private Personality personality = Personality.DEFAULT;
    private final float[] pairX = new float[2], pairY = new float[2];
    private boolean placed;

    /** The focused item, while following. */
    private float targetX, targetY;
    private long followUntil;
    /** Each pair's idle glance. */
    private final float[] glanceX = new float[2], glanceY = new float[2];
    private final long[] glanceUntil = new long[2];
    private long nextWander;
    /** Each pair blinks on its own. */
    private final long[] nextBlink = new long[2], blinkStart = new long[2], blinkUntil = new long[2];
    /** Mischievous: one eye winks. */
    private final long[] nextWink = new long[2], winkStart = new long[2], winkUntil = new long[2];
    private final boolean[] winkLeft = new boolean[2];
    private long surpriseUntil;
    /** Each eye's pupil offset (in radii), eased toward where it wants to look: [pair * 2 + (left ? 0 : 1)]. */
    private final float[] pupilX = new float[4], pupilY = new float[4];
    private long lastFrame;
    private boolean active;

    public EyesView(Context context, float radiusPx) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setRadius(radiusPx);
    }

    public float radius() {
        return radius;
    }

    public void setRadius(float radiusPx) {
        radius = radiusPx;
        buildAngryPaths();
        invalidate();
    }

    /** Pair width and height for placement ({@link EyeSpots}): both eyes, with room for brows and blush. */
    public static float pairWidth(float radiusPx) {
        return radiusPx * 4.9f;
    }

    public static float pairHeight(float radiusPx) {
        return radiusPx * 3.2f;
    }

    public void setPersonality(Personality p) {
        if (p == personality) return;
        personality = p;
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < 2; i++) {
            nextBlink[i] = now + 600 + random.nextInt(1500);
            blinkUntil[i] = 0;
            winkUntil[i] = 0;
            nextWink[i] = now + 4000 + random.nextInt(4000);
        }
        nextWander = now + 800;
        invalidate();
    }

    /** Places the two pairs (centres, in this view's pixels). */
    public void setPairs(float x0, float y0, float x1, float y1) {
        pairX[0] = x0;
        pairY[0] = y0;
        pairX[1] = x1;
        pairY[1] = y1;
        if (!placed) {
            targetX = (x0 + x1) / 2;
            targetY = Math.max(y0, y1) + radius * 6;
        }
        placed = true;
        invalidate();
    }

    /** The focused item moved here: both pairs follow it for a few seconds, eyes open. */
    public void lookAt(float x, float y) {
        long now = SystemClock.uptimeMillis();
        targetX = x;
        targetY = y;
        followUntil = now + FOLLOW_MS;
        for (int i = 0; i < 2; i++) {
            glanceUntil[i] = 0;
            blinkUntil[i] = 0;
            winkUntil[i] = 0;
            nextBlink[i] = Math.max(nextBlink[i], now + 1800);
        }
        nextWander = Math.max(nextWander, followUntil + 600);
        invalidate();
    }

    /** Wide eyes for a moment (Home shown again, or the eyes moved to new spots). */
    public void surprise() {
        surpriseUntil = SystemClock.uptimeMillis() + (personality == Personality.PLAYFUL ? 1000 : 650);
        invalidate();
    }

    public void start() {
        if (active) return;
        active = true;
        long now = SystemClock.uptimeMillis();
        lastFrame = now;
        for (int i = 0; i < 2; i++) {
            nextBlink[i] = now + 1500 + random.nextInt(3000);
            nextWink[i] = now + 5500 + random.nextInt(4000);
        }
        nextWander = now + 1500 + random.nextInt(2000);
        invalidate();
    }

    public void stop() {
        active = false;
    }

    // ---- Behaviour ------------------------------------------------------------------------------------------------

    private void step(long now) {
        Personality p = personality;
        boolean following = now < followUntil;
        if (!following && now >= nextWander) {
            if (random.nextFloat() < p.lookAtEachOther) {
                // The pairs look at each other.
                for (int i = 0; i < 2; i++) {
                    glanceX[i] = pairX[1 - i];
                    glanceY[i] = pairY[1 - i];
                    glanceUntil[i] = now + p.glanceMs + 400;
                }
            } else {
                // Each pair to its own random spot, mostly across the screen and below the eyes.
                for (int i = 0; i < 2; i++) {
                    glanceX[i] = getWidth() * (0.1f + random.nextFloat() * 0.8f);
                    glanceY[i] = getHeight() * (0.15f + random.nextFloat() * 0.75f);
                    glanceUntil[i] = now + p.glanceMs + random.nextInt(500);
                }
            }
            nextWander = now + Personality.between(p.wanderGapMin, p.wanderGapMax, random.nextFloat());
        }
        for (int i = 0; i < 2; i++) {
            if (now >= nextBlink[i]) {   // lookAt keeps the eyes open while the remote is moving
                blinkStart[i] = now;
                blinkUntil[i] = now + p.blinkMs;
                // Pairs drift out of step, so they don't blink as one.
                nextBlink[i] = now + Personality.between(p.blinkGapMin, p.blinkGapMax, random.nextFloat());
            }
            if (p == Personality.MISCHIEVOUS && now >= nextWink[i] && !following) {
                winkStart[i] = now;
                winkUntil[i] = now + 430;
                winkLeft[i] = random.nextBoolean();
                nextWink[i] = now + 6500 + random.nextInt(5000);
            }
        }
    }

    /** Where pair {@code pair}'s eye at (x, y) wants its pupil, in radii; eases toward it; true while moving. */
    private boolean aim(int pair, int eye, float x, float y, boolean left, long now, float dtFrames) {
        Personality p = personality;
        float tx, ty;
        if (now < followUntil) {
            tx = targetX;
            ty = targetY;
        } else if (now < glanceUntil[pair]) {
            tx = glanceX[pair];
            ty = glanceY[pair];
        } else {
            tx = targetX;
            ty = targetY;
        }
        float dx = tx - x, dy = ty - y;
        float len = (float) Math.hypot(dx, dy);
        float wantX = 0, wantY = 0;
        if (len > 1f) {
            float reach = Math.min(1f, len / (radius * 3));   // something close by: look only part way
            wantX = dx / len * p.travel * reach;
            wantY = dy / len * p.travel * reach * (p.style == Personality.Style.ANGRY ? 0.8f : 1f);
        }
        if (p == Personality.SHY && now >= followUntil) {
            wantY += 0.22f;                       // looks down
            wantX += (left ? 1f : -1f) * 0.10f;   // and a little inward
        }
        float k = 1f - (float) Math.pow(1f - p.speed, dtFrames);
        pupilX[eye] += (wantX - pupilX[eye]) * k;
        pupilY[eye] += (wantY - pupilY[eye]) * k;
        return Math.abs(wantX - pupilX[eye]) > 0.004f || Math.abs(wantY - pupilY[eye]) > 0.004f;
    }

    private float blink(int pair, long now) {
        if (now >= blinkUntil[pair] || blinkUntil[pair] <= blinkStart[pair]) return 0f;
        float phase = (now - blinkStart[pair]) / (float) (blinkUntil[pair] - blinkStart[pair]);
        return (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, phase)));
    }

    private float wink(int pair, boolean left, long now) {
        if (now >= winkUntil[pair] || left != winkLeft[pair]) return 0f;
        float phase = (now - winkStart[pair]) / (float) (winkUntil[pair] - winkStart[pair]);
        return (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, phase)));
    }

    // ---- Drawing --------------------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!placed) return;
        long now = SystemClock.uptimeMillis();
        float dtFrames = Math.max(0.25f, Math.min(6f, (now - lastFrame) / (float) FRAME_MS));
        lastFrame = now;
        if (active) step(now);
        boolean moving = false;
        boolean angry = personality.style == Personality.Style.ANGRY;
        float spacing = radius * (angry ? 1.17f : 1.08f);
        for (int pair = 0; pair < 2; pair++) {
            float lx = pairX[pair] - spacing, rx = pairX[pair] + spacing, y = pairY[pair];
            moving |= aim(pair, pair * 2, lx, y, true, now, dtFrames);
            moving |= aim(pair, pair * 2 + 1, rx, y, false, now, dtFrames);
            float closing = blink(pair, now);
            if (angry) {
                drawAngryEye(canvas, lx, y, pair * 2, closing, true);
                drawAngryEye(canvas, rx, y, pair * 2 + 1, closing, false);
            } else {
                drawEye(canvas, lx, y, pair * 2, Math.max(closing, wink(pair, true, now)), true, now);
                drawEye(canvas, rx, y, pair * 2 + 1, Math.max(closing, wink(pair, false, now)), false, now);
            }
        }
        if (active) {
            boolean blinking = false;
            for (int i = 0; i < 2; i++) blinking |= now < blinkUntil[i] || now < winkUntil[i];
            postInvalidateDelayed(moving || blinking || now < surpriseUntil ? FRAME_MS : IDLE_FRAME_MS);
        }
    }

    private void drawEye(Canvas canvas, float x, float y, int eye, float closing, boolean left, long now) {
        Personality p = personality;
        float r = radius;
        float eyeR = r * (p == Personality.SPARKLING ? 1.10f : 1f) * (now < surpriseUntil ? 1.16f : 1f);
        float shape = p == Personality.MISCHIEVOUS ? 0.76f : p == Personality.SHY ? 0.88f : 1.12f;
        float vertical = eyeR * shape * (1f - 0.86f * closing);

        // The white, with a soft shadow.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(r * 0.22f, 0, r * 0.15f, 0xAA000000);
        canvas.drawOval(x - eyeR, y - vertical, x + eyeR, y + vertical, paint);
        paint.clearShadowLayer();

        clip.reset();
        clip.addOval(x - eyeR, y - vertical, x + eyeR, y + vertical, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float px = x + pupilX[eye] * r, py = y + pupilY[eye] * r * (1f - 0.86f * closing);
        if (p == Personality.SPARKLING) {
            paint.setColor(0xFF7354BD);
            canvas.drawCircle(px, py, r * 0.52f, paint);
            paint.setColor(0xFF172146);
            canvas.drawCircle(px, py, r * 0.29f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(px - r * 0.16f, py - r * 0.18f, r * 0.13f, paint);
            canvas.drawCircle(px + r * 0.20f, py + r * 0.10f, r * 0.055f, paint);
        } else {
            paint.setColor(0xFF111820);
            canvas.drawCircle(px, py, r * (now < surpriseUntil ? 0.30f : 0.42f), paint);
            paint.setColor(0xDDFFFFFF);
            canvas.drawCircle(px - r * 0.11f, py - r * 0.12f, r * 0.10f, paint);
        }
        if (p == Personality.SLEEPY && closing < 0.95f) {
            // A heavy upper lid over the top of the eye.
            float lid = y - vertical + vertical * 2 * 0.42f;
            paint.setColor(0xFFE5DCCB);
            canvas.drawRect(x - eyeR, y - vertical, x + eyeR, lid, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.12f);
            paint.setColor(0xFF242B32);
            canvas.drawLine(x - eyeR, lid, x + eyeR, lid, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();

        // Outline, and the personality's extras.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(r * 0.13f);
        paint.setColor(0xFF242B32);
        canvas.drawOval(x - eyeR, y - vertical, x + eyeR, y + vertical, paint);
        if (p == Personality.MISCHIEVOUS && closing < 0.65f) {
            // A slanted brow, raised on the left eye.
            paint.setStrokeWidth(r * 0.17f);
            canvas.drawLine(x - eyeR * 0.82f, y - vertical * 1.1f,
                    x + eyeR * 0.82f, y - vertical * (left ? 1.45f : 0.85f), paint);
        } else if (p == Personality.SHY && closing < 0.65f) {
            paint.setStrokeWidth(r * 0.12f);
            canvas.drawArc(x - eyeR * 0.9f, y - vertical * 1.5f, x + eyeR * 0.9f, y - vertical * 0.2f,
                    200, 140, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x88F29BB4);
            float o = left ? -r * 0.25f : r * 0.25f;
            canvas.drawOval(x - r * 0.42f + o, y + eyeR * 1.0f, x + r * 0.42f + o, y + eyeR * 1.22f, paint);
        } else if (p == Personality.SPARKLING) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xDDFFFFFF);
            drawSparkle(canvas, x + eyeR * (left ? -0.95f : 0.95f), y - eyeR * 1.05f, r * 0.22f);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** A small four-point star. */
    private void drawSparkle(Canvas canvas, float cx, float cy, float s) {
        clip.reset();
        clip.moveTo(cx, cy - s);
        clip.quadTo(cx, cy, cx + s, cy);
        clip.quadTo(cx, cy, cx, cy + s);
        clip.quadTo(cx, cy, cx - s, cy);
        clip.quadTo(cx, cy, cx, cy - s);
        clip.close();
        canvas.drawPath(clip, paint);
    }

    /** V1's angular, red-iris eye (blue for Determined). The right eye is the left one mirrored. */
    private void drawAngryEye(Canvas canvas, float x, float y, int eye, float closing, boolean left) {
        float r = radius;
        boolean determined = personality == Personality.DETERMINED;
        canvas.save();
        canvas.translate(x, y);
        if (!left) canvas.scale(-1f, 1f);
        canvas.save();
        canvas.scale(1f, 1f - 0.86f * closing);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(0.25f * r, 0, 0.10f * r, 0xCC000000);
        canvas.drawPath(angryEye, paint);
        paint.clearShadowLayer();
        canvas.save();
        canvas.clipPath(angryEye);
        float dx = pupilX[eye] * r * (left ? 1f : -1f), dy = pupilY[eye] * r;
        float irisX = -0.08f * r + dx, irisY = 0.02f * r + dy;
        paint.setColor(determined ? 0xFF318DBA : 0xFFB51424);
        canvas.drawCircle(irisX, irisY, 0.39f * r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.09f * r);
        paint.setColor(determined ? 0xFF17465E : 0xFF490B14);
        canvas.drawCircle(irisX, irisY, 0.34f * r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(determined ? 0xFF0F2232 : 0xFF160D15);
        canvas.drawCircle(irisX, irisY, 0.19f * r, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(irisX - 0.12f * r, irisY - 0.14f * r, 0.10f * r, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.12f * r);
        paint.setColor(0xFF111016);
        canvas.drawPath(angryEye, paint);
        paint.setStrokeWidth((determined ? 0.14f : 0.22f) * r);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(angryLid, paint);
        canvas.restore();
        if (determined) {
            // A lighter diagonal brow reads as focused rather than furious.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.15f * r);
            canvas.drawLine(-1.20f * r, -1.12f * r, 0.90f * r, -0.63f * r, paint);
        } else {
            // The heavy diagonal brow slopes toward the nose: the scowl.
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(angryBrow, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void buildAngryPaths() {
        float r = radius;
        angryEye.reset();
        angryEye.moveTo(-1.35f * r, -0.12f * r);
        angryEye.cubicTo(-0.74f * r, -0.92f * r, 0.12f * r, -0.73f * r, 1.15f * r, 0.12f * r);
        angryEye.cubicTo(0.37f * r, 0.69f * r, -0.69f * r, 0.75f * r, -1.35f * r, -0.12f * r);
        angryEye.close();
        angryLid.reset();
        angryLid.moveTo(-1.39f * r, -0.15f * r);
        angryLid.cubicTo(-0.69f * r, -1.02f * r, 0.15f * r, -0.77f * r, 1.18f * r, 0.12f * r);
        angryBrow.reset();
        angryBrow.moveTo(-1.42f * r, -1.12f * r);
        angryBrow.cubicTo(-0.56f * r, -1.35f * r, 0.22f * r, -0.78f * r, 1.31f * r, -0.30f * r);
        angryBrow.lineTo(0.69f * r, -0.30f * r);
        angryBrow.cubicTo(0.13f * r, -0.70f * r, -0.65f * r, -1.00f * r, -1.42f * r, -1.12f * r);
        angryBrow.close();
    }
}
