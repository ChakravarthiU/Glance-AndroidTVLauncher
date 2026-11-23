package com.chakriau.tvhome.v2.wallpapers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The photos uploaded from the phone, in the app's private storage. Every photo is stored at exactly 1920 × 1080
 * (centre-cropped) with a 320 × 180 thumbnail, so Home and Settings never decode anything larger than they draw.
 * Ids sort by upload time. Call off the main thread.
 */
public final class Photos {

    public static final int MAX_PHOTOS = 100;

    private final File dir;
    private final File thumbs;

    public Photos(Context context) {
        dir = new File(context.getApplicationContext().getFilesDir(), "wallpapers");
        thumbs = new File(dir, "thumbs");
    }

    /** Photo ids, oldest first. */
    public synchronized List<String> list() {
        List<String> ids = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return ids;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            String n = f.getName();
            if (f.isFile() && n.endsWith(".jpg") && validId(n.substring(0, n.length() - 4))) {
                ids.add(n.substring(0, n.length() - 4));
            }
        }
        return ids;
    }

    public File file(String id) {
        return validId(id) ? new File(dir, id + ".jpg") : null;
    }

    public File thumb(String id) {
        return validId(id) ? new File(thumbs, id + ".jpg") : null;
    }

    /** Ids are made here: "p" + upload time + random suffix. Anything else is refused (no paths from the phone). */
    public static boolean validId(String id) {
        return id != null && id.matches("p[0-9]{13}[a-z0-9]{4}");
    }

    /**
     * Turns an uploaded picture file into a wallpaper: decoded at a reduced size, centre-cropped to 16:9 and saved as
     * 1920 × 1080 JPEG with a thumbnail.
     *
     * @return the new photo's id
     * @throws IOException with a message for the phone page when the picture can't be used
     */
    public synchronized String add(File upload) throws IOException {
        if (list().size() >= MAX_PHOTOS) throw new IOException("The TV already has " + MAX_PHOTOS + " photos");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(upload.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("That file is not a picture");
        if (!PhotoSize.accepted(bounds.outWidth, bounds.outHeight)) throw new IOException("That picture is too large");

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = PhotoSize.sampleSize(bounds.outWidth, bounds.outHeight, PhotoSize.WIDTH, PhotoSize.HEIGHT);
        Bitmap src = BitmapFactory.decodeFile(upload.getAbsolutePath(), opts);
        if (src == null) throw new IOException("That picture could not be read");
        Bitmap full = null, small = null;
        try {
            full = cover(src, PhotoSize.WIDTH, PhotoSize.HEIGHT);
            small = cover(full, PhotoSize.THUMB_WIDTH, PhotoSize.THUMB_HEIGHT);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No room on the TV");
            if (!thumbs.isDirectory() && !thumbs.mkdirs()) throw new IOException("No room on the TV");
            String id = newId();
            write(full, 90, file(id));
            write(small, 80, thumb(id));
            return id;
        } finally {
            src.recycle();
            if (full != null && full != src) full.recycle();
            if (small != null) small.recycle();
        }
    }

    public synchronized boolean delete(String id) {
        File f = file(id), t = thumb(id);
        if (f == null) return false;
        if (t != null) //noinspection ResultOfMethodCallIgnored
            t.delete();
        return f.delete();
    }

    /** Draws the centre of {@code src} that fills a {@code w × h} frame, scaled to exactly that size. */
    private static Bitmap cover(Bitmap src, int w, int h) {
        int[] c = PhotoSize.coverCrop(src.getWidth(), src.getHeight(), w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, new Rect(c[0], c[1], c[2], c[3]), new Rect(0, 0, w, h), paint);
        return out;
    }

    private static void write(Bitmap b, int quality, File to) throws IOException {
        File tmp = new File(to.getParentFile(), to.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            if (!b.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw new IOException("Could not save the photo");
        }
        if (!tmp.renameTo(to)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Could not save the photo");
        }
    }

    private static final Random RANDOM = new Random();

    private static String newId() {
        StringBuilder s = new StringBuilder("p").append(String.format(Locale.ROOT, "%013d", System.currentTimeMillis()));
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 4; i++) s.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return s.toString();
    }

    /**
     * Decodes a stored file for display at about {@code w × h} (never larger than stored), or null if unreadable.
     */
    public static Bitmap decode(File f, int w, int h) {
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = PhotoSize.sampleSize(bounds.outWidth, bounds.outHeight, Math.max(1, w), Math.max(1, h));
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }
}
