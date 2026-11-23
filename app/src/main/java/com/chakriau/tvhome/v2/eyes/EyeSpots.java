package com.chakriau.tvhome.v2.eyes;

import java.util.List;
import java.util.Random;

/**
 * Where the two pairs of eyes go (Requirements.md 5.2): random spots at different heights, one in each half of the
 * free space, never over anything listed to avoid (clock, buttons, banner, apps). Plain Java.
 */
public final class EyeSpots {

    /** A rectangle in screen pixels. */
    public static final class Box {
        public final float left, top, right, bottom;

        public Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        boolean overlaps(Box o) {
            return left < o.right && o.left < right && top < o.bottom && o.top < bottom;
        }

        static Box around(float cx, float cy, float w, float h) {
            return new Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        }
    }

    private static final int TRIES = 300;

    private EyeSpots() {
    }

    /**
     * Picks the centres of the two pairs, {x0, y0, x1, y1}: the first in the left half of {@code area}, the second
     * in the right half, at least {@code minDy} apart in height when the space allows, each pair's box
     * ({@code pairW} × {@code pairH}) inside the area and clear of {@code avoid}.
     */
    public static float[] pick(Random random, Box area, List<Box> avoid, float pairW, float pairH, float minDy) {
        float mid = (area.left + area.right) / 2;
        Box leftHalf = new Box(area.left, area.top, mid, area.bottom);
        Box rightHalf = new Box(mid, area.top, area.right, area.bottom);
        // Try for well-spread heights first; if the space is too tight, accept any clear spots.
        for (float dy : new float[]{minDy, minDy / 2, 0}) {
            for (int i = 0; i < TRIES; i++) {
                float[] a = spot(random, leftHalf, pairW, pairH);
                float[] b = spot(random, rightHalf, pairW, pairH);
                if (a == null || b == null) break;
                if (Math.abs(a[1] - b[1]) < dy) continue;
                if (clear(a, avoid, pairW, pairH) && clear(b, avoid, pairW, pairH)) {
                    return new float[]{a[0], a[1], b[0], b[1]};
                }
            }
        }
        // Nowhere clear: the outer edges of the area, at different heights.
        float y0 = area.top + pairH / 2, y1 = area.bottom - pairH / 2;
        return new float[]{area.left + pairW / 2, y0, area.right - pairW / 2, Math.max(y0, y1)};
    }

    private static float[] spot(Random random, Box half, float w, float h) {
        float xSpan = half.right - half.left - w, ySpan = half.bottom - half.top - h;
        if (xSpan < 0 || ySpan < 0) return null;
        return new float[]{half.left + w / 2 + random.nextFloat() * xSpan,
                half.top + h / 2 + random.nextFloat() * ySpan};
    }

    static boolean clear(float[] c, List<Box> avoid, float w, float h) {
        Box b = Box.around(c[0], c[1], w, h);
        for (Box o : avoid) if (b.overlaps(o)) return false;
        return true;
    }
}
