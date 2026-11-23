package com.chakriau.tvhome.v2.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.chakriau.tvhome.v2.weather.WeatherCode;

/** Small vector icons for the top bar (Requirements.md 5.8, 5.9), drawn in code so they recolour with focus. */
final class Icons {

    private Icons() {
    }

    /** A one-colour icon that can be recoloured (white on glass, dark when its button is focused). */
    abstract static class Glyph extends Drawable {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        Glyph(int sizePx, int color) {
            size = sizePx;
            fill.setStyle(Paint.Style.FILL);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            setColor(color);
        }

        void setColor(int color) {
            fill.setColor(color);
            line.setColor(color);
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            line.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            line.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A gear for Settings. */
    static final class Gear extends Glyph {
        private final Path path = new Path();

        Gear(int sizePx, int color) {
            super(sizePx, color);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            path.reset();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), r = Math.min(b.width(), b.height()) / 2f;
            int teeth = 8;
            double pitch = Math.PI * 2 / teeth;
            float inner = r * 0.72f;
            // Each tooth: up from the body, a flat top at the outer radius, back down.
            double[][] tooth = {{-0.30, inner}, {-0.17, r}, {0.17, r}, {0.30, inner}};
            boolean first = true;
            for (int k = 0; k < teeth; k++) {
                for (double[] p : tooth) {
                    double a = pitch * (k + p[0]);
                    float x = cx + (float) (Math.cos(a) * p[1]), y = cy + (float) (Math.sin(a) * p[1]);
                    if (first) path.moveTo(x, y);
                    else path.lineTo(x, y);
                    first = false;
                }
            }
            path.close();
            path.addCircle(cx, cy, r * 0.30f, Path.Direction.CCW);
            path.setFillType(Path.FillType.EVEN_ODD);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(path, fill);
        }
    }

    /** A TV screen on a stand, for Inputs and HDMI rows; with antennas, for Live TV. */
    static final class Monitor extends Glyph {
        private final boolean antenna;

        Monitor(int sizePx, int color, boolean antenna) {
            super(sizePx, color);
            this.antenna = antenna;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
            line.setStrokeWidth(s * 0.09f);
            float top = antenna ? 0.3f : 0.16f;
            c.drawRoundRect(new RectF(x + s * 0.08f, y + s * top, x + s * 0.92f, y + s * 0.74f),
                    s * 0.08f, s * 0.08f, line);
            if (antenna) {
                c.drawLine(x + s * 0.5f, y + s * 0.3f, x + s * 0.34f, y + s * 0.08f, line);
                c.drawLine(x + s * 0.5f, y + s * 0.3f, x + s * 0.66f, y + s * 0.08f, line);
            } else {
                c.drawLine(x + s * 0.5f, y + s * 0.74f, x + s * 0.5f, y + s * 0.88f, line);
                c.drawLine(x + s * 0.3f, y + s * 0.9f, x + s * 0.7f, y + s * 0.9f, line);
            }
        }
    }

    /** Wi-Fi waves; the outer ones dim with a weak signal. Offline: dimmed waves with a slash. */
    static final class Wifi extends Glyph {
        private int level = 4;
        private boolean offline;

        Wifi(int sizePx, int color) {
            super(sizePx, color);
        }

        void set(int signalLevel, boolean isOffline) {
            level = Math.max(1, Math.min(4, signalLevel));
            offline = isOffline;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float cx = b.exactCenterX(), base = b.top + (b.height() - s) / 2f + s * 0.84f;
            line.setStrokeWidth(s * 0.1f);
            int full = line.getAlpha();
            c.drawCircle(cx, base, s * 0.07f, fill);
            float[] radii = {0.3f, 0.52f, 0.74f};
            for (int i = 0; i < radii.length; i++) {
                boolean on = !offline && level >= i + 2;
                line.setAlpha(on ? full : full * 35 / 100);
                float r = s * radii[i];
                c.drawArc(new RectF(cx - r, base - r, cx + r, base + r), 225, 90, false, line);
            }
            line.setAlpha(full);
            if (offline) {
                c.drawLine(cx - s * 0.36f, base - s * 0.72f, cx + s * 0.36f, base + s * 0.02f, line);
            }
        }
    }

    /** A network plug for Ethernet. */
    static final class Ethernet extends Glyph {
        Ethernet(int sizePx, int color) {
            super(sizePx, color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
            line.setStrokeWidth(s * 0.09f);
            Path p = new Path();
            // The RJ45 socket outline: a box with a step at the bottom.
            p.moveTo(x + s * 0.14f, y + s * 0.18f);
            p.lineTo(x + s * 0.86f, y + s * 0.18f);
            p.lineTo(x + s * 0.86f, y + s * 0.68f);
            p.lineTo(x + s * 0.68f, y + s * 0.68f);
            p.lineTo(x + s * 0.68f, y + s * 0.84f);
            p.lineTo(x + s * 0.32f, y + s * 0.84f);
            p.lineTo(x + s * 0.32f, y + s * 0.68f);
            p.lineTo(x + s * 0.14f, y + s * 0.68f);
            p.close();
            c.drawPath(p, line);
            for (int i = 0; i < 4; i++) {
                float px = x + s * (0.32f + i * 0.12f);
                c.drawLine(px, y + s * 0.3f, px, y + s * 0.44f, line);
            }
        }
    }

    /** A play triangle, for the Resume label on Continue watching cards. */
    static final class Play extends Glyph {
        private final Path path = new Path();

        Play(int sizePx, int color) {
            super(sizePx, color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
            path.reset();
            path.moveTo(x + s * 0.2f, y + s * 0.1f);
            path.lineTo(x + s * 0.88f, y + s * 0.5f);
            path.lineTo(x + s * 0.2f, y + s * 0.9f);
            path.close();
            c.drawPath(path, fill);
        }
    }

    /** Outline weather icons for the top bar (sun, moon, cloud, rain, snow, storm, fog). */
    static final class Weather extends Drawable {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private WeatherCode.Kind kind = WeatherCode.Kind.CLOUDY;
        private boolean day = true;

        Weather(int sizePx, int color) {
            size = sizePx;
            stroke.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setColor(color);
            fill.setStyle(Paint.Style.FILL);
        }

        void set(WeatherCode.Kind k, boolean isDay) {
            kind = k;
            day = isDay;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            canvas.save();
            canvas.translate(b.left + (b.width() - s) / 2f, b.top + (b.height() - s) / 2f);
            stroke.setStrokeWidth(s * 0.075f);
            switch (kind) {
                case CLEAR:
                    if (day) sun(canvas, s, 0.5f, 0.5f, 1f);
                    else moon(canvas, s, 0.5f, 0.5f, 1f);
                    break;
                case PARTLY_CLOUDY: {
                    Path c = cloud(s, 0.08f, 0.06f);
                    canvas.save();
                    canvas.clipOutPath(c);   // the sun goes behind the cloud
                    if (day) sun(canvas, s, 0.36f, 0.36f, 0.62f);
                    else moon(canvas, s, 0.36f, 0.36f, 0.62f);
                    canvas.restore();
                    canvas.drawPath(c, stroke);
                    break;
                }
                case FOG:
                    canvas.drawPath(cloud(s, 0f, -0.1f), stroke);
                    for (int i = 0; i < 2; i++) {
                        float y = s * (0.78f + i * 0.12f);
                        canvas.drawLine(s * (0.18f + i * 0.08f), y, s * (0.82f - i * 0.04f), y, stroke);
                    }
                    break;
                case DRIZZLE:
                case RAIN:
                    canvas.drawPath(cloud(s, 0f, -0.12f), stroke);
                    int drops = kind == WeatherCode.Kind.RAIN ? 3 : 2;
                    for (int i = 0; i < drops; i++) {
                        float x = s * (drops == 3 ? 0.34f + i * 0.16f : 0.42f + i * 0.16f);
                        canvas.drawLine(x, s * 0.72f, x - s * 0.06f, s * 0.9f, stroke);
                    }
                    break;
                case SNOW:
                    canvas.drawPath(cloud(s, 0f, -0.12f), stroke);
                    for (int i = 0; i < 3; i++) {
                        canvas.drawCircle(s * (0.34f + i * 0.16f), s * (i == 1 ? 0.86f : 0.78f), s * 0.045f, fill);
                    }
                    break;
                case STORM:
                    canvas.drawPath(cloud(s, 0f, -0.12f), stroke);
                    Path bolt = new Path();
                    bolt.moveTo(s * 0.54f, s * 0.6f);
                    bolt.lineTo(s * 0.4f, s * 0.8f);
                    bolt.lineTo(s * 0.52f, s * 0.8f);
                    bolt.lineTo(s * 0.44f, s * 0.97f);
                    canvas.drawPath(bolt, stroke);
                    break;
                default:
                    canvas.drawPath(cloud(s, 0f, 0f), stroke);
            }
            canvas.restore();
        }

        private void sun(Canvas c, float s, float cx, float cy, float scale) {
            float r = s * 0.17f * scale;
            c.drawCircle(s * cx, s * cy, r, stroke);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                float r1 = s * 0.29f * scale, r2 = s * 0.4f * scale;
                c.drawLine(s * cx + (float) Math.cos(a) * r1, s * cy + (float) Math.sin(a) * r1,
                        s * cx + (float) Math.cos(a) * r2, s * cy + (float) Math.sin(a) * r2, stroke);
            }
        }

        private void moon(Canvas c, float s, float cx, float cy, float scale) {
            Path outer = new Path();
            outer.addCircle(s * cx, s * cy, s * 0.3f * scale, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(s * (cx + 0.17f * scale), s * (cy - 0.12f * scale), s * 0.25f * scale, Path.Direction.CW);
            outer.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(outer, stroke);
        }

        /** A cloud outline in the lower part of the square, moved by (dx, dy) of the size. */
        private static Path cloud(float s, float dx, float dy) {
            Path base = new Path();
            base.addRoundRect(new RectF(s * (0.21f + dx), s * (0.58f + dy), s * (0.85f + dx), s * (0.73f + dy)),
                    s * 0.07f, s * 0.07f, Path.Direction.CW);
            // Three overlapping puffs on a rounded base, joined into one outline.
            Path out = new Path();
            out.addCircle(s * (0.36f + dx), s * (0.58f + dy), s * 0.15f, Path.Direction.CW);
            for (float[] c : new float[][]{{0.55f, 0.48f, 0.2f}, {0.72f, 0.6f, 0.13f}}) {
                Path circle = new Path();
                circle.addCircle(s * (c[0] + dx), s * (c[1] + dy), s * c[2], Path.Direction.CW);
                out.op(circle, Path.Op.UNION);
            }
            out.op(base, Path.Op.UNION);
            return out;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            stroke.setAlpha(alpha);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            stroke.setColorFilter(cf);
            fill.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
