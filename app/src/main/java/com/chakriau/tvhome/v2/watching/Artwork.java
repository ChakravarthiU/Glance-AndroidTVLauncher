package com.chakriau.tvhome.v2.watching;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the pictures on Continue watching cards: web addresses, or content and resource URIs other apps give in
 * Watch Next. Decoded at card size, never full size (Requirements.md principle 4), and kept in a small cache.
 */
public final class Artwork {

    private static final String TAG = "TvHomeWatching";
    private static final int MAX_BYTES = 6 * 1024 * 1024;   // larger files are skipped: the app banner shows instead

    public interface Target {
        /** On the main thread; null when there is no picture (the card keeps its app banner). */
        void onBitmap(Bitmap bitmap);
    }

    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    /** About ten 16:9 cards at 1080p in RGB_565 (≈160 KB each). */
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(3 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    public Artwork(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
    }

    /** Loads the picture at about {@code w} × {@code h}; the view's tag guards against a reused card. */
    public void load(String uri, int w, int h, ImageView view, Target target) {
        view.setTag(uri);
        Bitmap cached = uri == null ? null : cache.get(uri);
        if (cached != null || uri == null) {
            target.onBitmap(cached);
            return;
        }
        executor.execute(() -> {
            Bitmap b = decode(uri, w, h);
            if (b != null) cache.put(uri, b);
            main.post(() -> {
                if (uri.equals(view.getTag())) target.onBitmap(b);
            });
        });
    }

    private Bitmap decode(String uri, int w, int h) {
        byte[] data;
        try {
            data = fetch(uri);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "no picture from " + uri + ": " + e);
            return null;
        }
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "picture too large: " + uri);
            return null;
        }
    }

    /** The largest power of two that keeps the picture at least the card's size (it is cropped to fill). */
    static int sampleSize(int srcW, int srcH, int w, int h) {
        int s = 1;
        while (srcW / (s * 2) >= w && srcH / (s * 2) >= h) s *= 2;
        return s;
    }

    private byte[] fetch(String uri) throws IOException {
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) {
            HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            try {
                if (c.getResponseCode() / 100 != 2) return null;
                try (InputStream in = c.getInputStream()) {
                    return readAll(in);
                }
            } finally {
                c.disconnect();
            }
        }
        if (scheme.equals("content") || scheme.equals("android.resource") || scheme.equals("file")) {
            try (InputStream in = resolver.openInputStream(u)) {
                return in == null ? null : readAll(in);
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_BYTES) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Frees the cached pictures (Home hidden for a while, or memory is short). */
    public void clear() {
        cache.evictAll();
    }

    public void shutdown() {
        executor.shutdownNow();
        cache.evictAll();
    }
}
