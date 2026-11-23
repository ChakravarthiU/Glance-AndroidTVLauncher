package com.chakriau.tvhome.v2.wallpapers;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** The QR code on Settings › Wallpapers that opens the upload page on the phone (no typing on the TV). */
public final class QrCode {

    private QrCode() {
    }

    /** Black modules on white, with the standard quiet zone, about {@code size} pixels square; null on failure. */
    public static Bitmap make(String text, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix m;
        try {
            m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);   // 1 pixel per module
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
        int modules = m.getWidth();
        int cell = Math.max(1, size / modules);   // whole pixels per module keep the edges sharp
        int px = modules * cell;
        int[] pixels = new int[px * px];
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                pixels[y * px + x] = m.get(x / cell, y / cell) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.RGB_565);
    }
}
