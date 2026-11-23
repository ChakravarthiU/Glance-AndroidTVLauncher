package com.chakriau.tvhome.v2.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Calendar;

/**
 * The analog face for the top bar's clock (Requirements.md 5.8, Settings › Time, date &amp; weather › Clock face): a
 * glass dial like Home's buttons and apps band, hour ticks, white hour and minute hands, and optionally a thin amber
 * seconds hand. Angles are {@link ClockMath}; this only draws.
 *
 * <p>Without the seconds hand it changes once a minute (the hands click forward over {@link ClockMath#HANDS_MS});
 * with it, it ticks once a second, and only between {@link #start()} and {@link #stop()} (while Home is visible).
 */
public final class AnalogClock extends View {

    private static final int AMBER = Color.rgb(0xFF, 0xD3, 0x4D);   // Home's first light-bulb colour (7.6)

    private final Paint dial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondsHand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float scale;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = this::onSecond;

    private boolean seconds, started;
    // Shown angles, and where an animation started from and is going to.
    private float hourA, minuteA, secondA;
    private float hourFrom, minuteFrom, secondFrom, hourTo, minuteTo, secondTo;
    private boolean placed;
    private ValueAnimator anim;

    /** @param scale design pixels to screen pixels ({@link HomeKit#px}). */
    public AnalogClock(Context context, float scale) {
        super(context);
        this.scale = scale;
        dial.setStyle(Paint.Style.FILL);
        dial.setColor(Color.argb(34, 255, 255, 255));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 2 * scale));
        rim.setColor(Color.argb(70, 255, 255, 255));
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(Color.WHITE);
        hand.setStyle(Paint.Style.STROKE);
        hand.setStrokeCap(Paint.Cap.ROUND);
        hand.setColor(Color.WHITE);
        hand.setShadowLayer(6 * scale, 0, scale, Color.argb(90, 0, 0, 0));
        secondsHand.setStyle(Paint.Style.STROKE);
        secondsHand.setStrokeCap(Paint.Cap.ROUND);
        secondsHand.setColor(AMBER);
        secondsHand.setStrokeWidth(Math.max(1f, 2.5f * scale));
        secondsHand.setShadowLayer(4 * scale, 0, scale, Color.argb(80, 0, 0, 0));
        setContentDescription("Clock");
    }

    /** Whether to draw (and tick) the seconds hand. */
    public void setSeconds(boolean on) {
        if (seconds == on) return;
        seconds = on;
        update(false);
        if (started) {
            main.removeCallbacks(tickRunnable);
            if (seconds) scheduleSecond();
        }
    }

    /** Starts ticking each second (only matters with the seconds hand). Home calls this when it shows. */
    public void start() {
        started = true;
        update(false);
        main.removeCallbacks(tickRunnable);
        if (seconds) scheduleSecond();
    }

    /** Stops ticking and any hand movement (Home hid). */
    public void stop() {
        started = false;
        main.removeCallbacks(tickRunnable);
        endAnimation();
    }

    /** Points the hands at the time now; moves them there smoothly when {@code animate}, else jumps. */
    public void update(boolean animate) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE), s = c.get(Calendar.SECOND);
        int sec = seconds ? s : 0;   // without the seconds hand, the minute hand moves once a minute
        float ht = ClockMath.hourAngle(h, m, sec);
        float mt = ClockMath.minuteAngle(m, sec);
        float st = ClockMath.secondAngle(s);
        setContentDescription(String.format(java.util.Locale.getDefault(), "Clock, %d:%02d", h, m));
        if (!animate || !placed || !isAttachedToWindow()) {
            endAnimation();
            hourA = ht;
            minuteA = mt;
            secondA = st;
            placed = true;
            invalidate();
            return;
        }
        if (Math.abs(ClockMath.sweep(hourA, ht)) < 0.01f && Math.abs(ClockMath.sweep(minuteA, mt)) < 0.01f
                && (!seconds || Math.abs(ClockMath.sweep(secondA, st)) < 0.01f)) {
            return;
        }
        endAnimation();
        hourFrom = hourA;
        minuteFrom = minuteA;
        secondFrom = secondA;
        hourTo = ht;
        minuteTo = mt;
        secondTo = st;
        // A seconds tick is short; the minute's click (no seconds hand) is a little slower.
        long ms = seconds ? ClockMath.TICK_MS : ClockMath.HANDS_MS;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            float f = ClockMath.easeOutBack((Float) a.getAnimatedValue());
            hourA = ClockMath.turn(hourFrom, hourTo, f);
            minuteA = ClockMath.turn(minuteFrom, minuteTo, f);
            secondA = ClockMath.turn(secondFrom, secondTo, f);
            invalidate();
        });
        anim.start();
    }

    private void endAnimation() {
        if (anim != null) {
            anim.removeAllUpdateListeners();
            anim.cancel();
            anim = null;
            hourA = hourTo;
            minuteA = minuteTo;
            secondA = secondTo;
            invalidate();
        }
    }

    private void onSecond() {
        if (!started || !seconds) return;
        update(true);
        scheduleSecond();
    }

    /** Next tick just after the next whole second, so the hand moves when the second changes. */
    private void scheduleSecond() {
        long now = System.currentTimeMillis();
        main.postAtTime(tickRunnable, SystemClock.uptimeMillis() + (1000 - now % 1000) + 5);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacks(tickRunnable);
        endAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - rim.getStrokeWidth();
        canvas.drawCircle(cx, cy, r, dial);
        canvas.drawCircle(cx, cy, r, rim);

        // Hour ticks: longer and brighter at 12, 3, 6 and 9.
        for (int i = 0; i < 12; i++) {
            boolean major = i % 3 == 0;
            float len = (major ? 13 : 7) * scale;
            float out = r - 9 * scale;
            tick.setStrokeWidth(Math.max(1f, (major ? 4f : 2.5f) * scale));
            tick.setAlpha(major ? 255 : 170);
            double a = Math.toRadians(i * 30);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            canvas.drawLine(cx + sx * out, cy + sy * out, cx + sx * (out - len), cy + sy * (out - len), tick);
        }

        hand.setStrokeWidth(8 * scale);
        drawHand(canvas, cx, cy, hourA, r * 0.50f, r * 0.10f, hand);
        hand.setStrokeWidth(5 * scale);
        drawHand(canvas, cx, cy, minuteA, r * 0.76f, r * 0.10f, hand);
        if (seconds) drawHand(canvas, cx, cy, secondA, r * 0.84f, r * 0.20f, secondsHand);

        hand.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 7 * scale, hand);
        hand.setStyle(Paint.Style.STROKE);
        if (seconds) {
            secondsHand.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, 3.5f * scale, secondsHand);
            secondsHand.setStyle(Paint.Style.STROKE);
        }
    }

    private static void drawHand(Canvas canvas, float cx, float cy, float degrees, float length, float tail,
                                 Paint p) {
        double a = Math.toRadians(degrees);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        canvas.drawLine(cx - sx * tail, cy - sy * tail, cx + sx * length, cy + sy * length, p);
    }
}
