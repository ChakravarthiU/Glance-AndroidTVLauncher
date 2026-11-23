package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Theme 1's built-in wallpaper: a dusk sky over two mountain ridges with a warm glow on the horizon (as in
 * {@code design/theme1-home.png}). Drawn from paths, so it costs no image memory. Phone photos replace it in build
 * order step 6.
 */
final class DefaultWallpaper extends View {

    private final Paint sky = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint far = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint near = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ground = new Paint();
    private final Path farRidge = new Path();
    private final Path nearRidge = new Path();
    private float horizon;

    DefaultWallpaper(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float sx = w / 1920f, sy = h / 1080f;
        horizon = 640 * sy;
        sky.setShader(new LinearGradient(0, 0, 0, horizon,
                new int[]{Color.rgb(16, 30, 70), Color.rgb(28, 52, 104), Color.rgb(64, 78, 128)},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w * 0.55f, horizon, 760 * sx,
                new int[]{Color.argb(170, 236, 152, 110), Color.argb(70, 200, 120, 120), Color.argb(0, 120, 90, 140)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        far.setColor(Color.rgb(38, 58, 108));
        // The near ridge runs down to the bottom edge: it fades into the dark ground under the apps.
        near.setShader(new LinearGradient(0, 560 * sy, 0, h,
                new int[]{Color.rgb(30, 46, 92), Color.rgb(10, 14, 34)}, null, Shader.TileMode.CLAMP));
        ground.setShader(new LinearGradient(0, horizon, 0, h,
                new int[]{Color.rgb(16, 26, 58), Color.rgb(10, 14, 34)}, null, Shader.TileMode.CLAMP));

        // Ridge points in mockup pixels (x, y), from the left edge to the right.
        ridge(farRidge, sx, sy, h, new float[]{0, 560, 200, 490, 420, 560, 640, 470, 800, 520, 1000, 450, 1220, 540,
                1420, 470, 1640, 530, 1920, 480});
        ridge(nearRidge, sx, sy, h, new float[]{0, 610, 260, 560, 520, 630, 800, 570, 1080, 640, 1300, 590, 1560, 650,
                1780, 590, 1920, 620});
    }

    private static void ridge(Path p, float sx, float sy, int h, float[] pts) {
        p.reset();
        p.moveTo(0, h);
        for (int i = 0; i < pts.length; i += 2) p.lineTo(pts[i] * sx, pts[i + 1] * sy);
        p.lineTo(pts[pts.length - 2] * sx, h);
        p.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, horizon, sky);
        canvas.drawRect(0, horizon, w, h, ground);
        canvas.drawRect(0, 0, w, h, glow);
        canvas.drawPath(farRidge, far);
        canvas.drawPath(nearRidge, near);
    }
}
