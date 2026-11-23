package com.chakriau.tvhome.v2.wallpapers;

/**
 * Sizes for decoding photos on a 1 GB TV (Requirements.md principle 4): never decode at full size. Plain Java.
 */
public final class PhotoSize {

    /** Stored wallpapers are exactly this size (the UI renders at 1080p, also on 4K panels). */
    public static final int WIDTH = 1920, HEIGHT = 1080;
    /** Thumbnails for Settings and the phone page. */
    public static final int THUMB_WIDTH = 320, THUMB_HEIGHT = 180;
    /** Larger uploads are refused before decoding: a little more than 4K's 8.3 M pixels. */
    public static final long MAX_PIXELS = 25_000_000L;

    private PhotoSize() {
    }

    public static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers {@code w × h} in both directions. */
    public static int sampleSize(int width, int height, int w, int h) {
        int sample = 1;
        while (width / (sample * 2) >= w && height / (sample * 2) >= h) sample *= 2;
        return sample;
    }

    /**
     * The part of a {@code width × height} picture that fills a {@code w × h} frame when cropped to its shape
     * (centre crop): {left, top, right, bottom}.
     */
    public static int[] coverCrop(int width, int height, int w, int h) {
        if ((long) width * h > (long) height * w) {   // wider than the frame: crop the sides
            int cropW = (int) ((long) height * w / h);
            int left = (width - cropW) / 2;
            return new int[]{left, 0, left + cropW, height};
        }
        int cropH = (int) ((long) width * h / w);
        int top = (height - cropH) / 2;
        return new int[]{0, top, width, top + cropH};
    }
}
