package com.chakriau.tvhome.v2.wallpapers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.chakriau.tvhome.v2.backup.Backup;
import com.chakriau.tvhome.v2.backup.BackupFormat;
import com.chakriau.tvhome.v2.festive.SpecialDayStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone page (Settings › Phone; Requirements.md 5.1, 5.4, 8), served on the home network only while the
 * launcher's Settings is open. Everything but the page itself needs the PIN shown on the TV; repeated wrong PINs lock
 * it for a while.
 *
 * <pre>
 * GET    /                 the phone page (assets/upload/index.html)
 * GET    /api/photos       the photos on the TV, as JSON: {max, enabled, current, photos: [{id, ticked}]}
 * GET    /api/thumb/{id}   a photo's thumbnail
 * POST   /api/upload       a picture (any size; the TV stores it at 1920 × 1080); ?tick=0 leaves it unticked
 * DELETE /api/photo/{id}   removes a photo
 * GET    /api/backup       a backup ZIP of every setting (?photos=1: and the photos), Requirements.md 5.11
 * POST   /api/restore      a backup ZIP: checked, staged, and put in place when the launcher restarts
 * other  /api/...          settings, special days, apps, city: handed to the {@link Api}
 * </pre>
 * The PIN comes in the {@code X-Pin} header.
 */
public final class UploadServer {

    public static final int PORT = 8766;   // V1 used 8765; a different port avoids any clash while both exist
    private static final String TAG = "TvHomeUpload";
    private static final int MAX_UPLOAD = 20 * 1024 * 1024;
    private static final int MAX_HEADER = 16 * 1024;
    private static final int MAX_BODY = 64 * 1024;

    /** Told on the main thread when photos are added or removed from the phone. */
    public interface Listener {
        void onPhotosChanged();
    }

    /** The rest of the phone page's requests (everything but photos). Called on the server's thread. */
    public interface Api {
        /** Null when the path isn't one the API knows (404). */
        Reply handle(String method, String path, String query, byte[] body);
    }

    /** An answer: status, content type and body. */
    public static final class Reply {
        public final int status;
        public final String type;
        public final byte[] body;

        public Reply(int status, String type, byte[] body) {
            this.status = status;
            this.type = type;
            this.body = body;
        }

        public static Reply json(String s) {
            return new Reply(200, "application/json; charset=utf-8", s.getBytes(StandardCharsets.UTF_8));
        }

        public static Reply text(int status, String s) {
            return new Reply(status, "text/plain; charset=utf-8", s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final Context context;
    private final Photos photos;
    private final WallpaperStore store;
    private final PinGuard guard;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService pool;
    private ServerSocket socket;
    private volatile boolean running;
    private volatile Api api;
    private volatile Runnable onRestored;

    public UploadServer(Context context, Photos photos, WallpaperStore store, Listener listener) {
        this.context = context.getApplicationContext();
        this.photos = photos;
        this.store = store;
        this.guard = new PinGuard(store.pin());
        this.listener = listener;
    }

    /** Starts listening. False if the port can't be opened. */
    public synchronized boolean start() {
        if (running) return true;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
        } catch (IOException e) {
            Log.w(TAG, "could not listen on " + PORT + ": " + e);
            return false;
        }
        pool = Executors.newFixedThreadPool(2);
        running = true;
        Thread t = new Thread(this::acceptLoop, "wallpaper-upload");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // Closing.
        }
        socket = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
    }

    public void setApi(Api a) {
        api = a;
    }

    /** Called on the main thread after a restore was staged: the launcher must restart to put it in place. */
    public void setOnRestored(Runnable r) {
        onRestored = r;
    }

    /** The PIN changed in Settings: the old one stops working at once. */
    public void setPin(String pin) {
        guard.setPin(pin);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = socket.accept();
                ExecutorService p = pool;
                if (p == null) {
                    s.close();
                    continue;
                }
                p.execute(() -> {
                    try (Socket c = s) {
                        c.setSoTimeout(20_000);
                        handle(c.getInputStream(), c.getOutputStream());
                    } catch (Exception e) {
                        Log.w(TAG, "request failed: " + e);
                    }
                });
            } catch (Exception e) {
                if (running) Log.w(TAG, "accept failed: " + e);
            }
        }
    }

    // ---- Requests -------------------------------------------------------------------------------------------------

    private void handle(InputStream in, OutputStream out) throws IOException {
        String head = readHead(in);
        if (head == null) return;
        String[] lines = head.split("\r\n");
        String[] req = lines[0].split(" ");
        if (req.length < 2) return;
        String method = req[0];
        String path = req[1];
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) {
            query = path.substring(q + 1);
            path = path.substring(0, q);
        }
        Map<String, String> h = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) h.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }

        if ("GET".equals(method) && (path.equals("/") || path.equals("/index.html") || path.equals("/banners"))) {
            reply(out, 200, "text/html; charset=utf-8", page("upload/index.html"));
            return;
        }
        if (!path.startsWith("/api/")) {
            reply(out, 404, "text/plain; charset=utf-8", text("Not found"));
            return;
        }
        PinGuard.Result pin = guard.check(h.get("x-pin"), System.currentTimeMillis());
        if (pin == PinGuard.Result.LOCKED) {
            int m = Math.max(1, guard.lockedMinutes(System.currentTimeMillis()));
            reply(out, 429, "text/plain; charset=utf-8",
                    text("Too many wrong PINs. Try again in " + m + (m == 1 ? " minute." : " minutes.")));
            return;
        }
        if (pin == PinGuard.Result.WRONG) {
            reply(out, 403, "text/plain; charset=utf-8", text("Wrong PIN. Check the PIN on the TV."));
            return;
        }

        if ("GET".equals(method) && path.equals("/api/photos")) {
            reply(out, 200, "application/json; charset=utf-8", text(photosJson()));
        } else if ("GET".equals(method) && path.startsWith("/api/thumb/")) {
            File f = photos.thumb(path.substring("/api/thumb/".length()));
            if (f == null || !f.isFile()) reply(out, 404, "text/plain; charset=utf-8", text("No such photo"));
            else reply(out, 200, "image/jpeg", readAll(new FileInputStream(f)));
        } else if ("DELETE".equals(method) && path.startsWith("/api/photo/")) {
            String id = path.substring("/api/photo/".length());
            if (photos.delete(id)) {
                new SpecialDayStore(context).forgetPhoto(id);
                Set<String> t = store.ticked();
                t.remove(id);
                store.setTicked(t);
                reply(out, 200, "text/plain; charset=utf-8", text("Removed"));
                changed();
            } else {
                reply(out, 404, "text/plain; charset=utf-8", text("No such photo"));
            }
        } else if ("POST".equals(method) && path.equals("/api/upload")) {
            upload(in, out, h, !"tick=0".equals(query));
        } else if ("GET".equals(method) && path.equals("/api/backup")) {
            backup(out, "photos=1".equals(query));
        } else if ("POST".equals(method) && path.equals("/api/restore")) {
            restore(in, out, h);
        } else {
            other(method, path, query, in, out, h);
        }
    }

    /** {@code tick}: on Home straight away; not for a special day's photo, which stays a surprise until its day. */
    private void upload(InputStream in, OutputStream out, Map<String, String> h, boolean tick) throws IOException {
        int length;
        try {
            length = Integer.parseInt(h.getOrDefault("content-length", "-1"));
        } catch (NumberFormatException e) {
            length = -1;
        }
        if (length <= 0 || length > MAX_UPLOAD) {
            reply(out, 413, "text/plain; charset=utf-8", text("Pictures up to 20 MB, please."));
            return;
        }
        File tmp = File.createTempFile("upload", ".img", context.getCacheDir());
        try {
            try (OutputStream f = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int left = length;
                while (left > 0) {
                    int n = in.read(buf, 0, Math.min(left, buf.length));
                    if (n < 0) throw new IOException("The upload stopped part way");
                    f.write(buf, 0, n);
                    left -= n;
                }
            }
            String id;
            try {
                id = photos.add(tmp);
            } catch (IOException e) {
                reply(out, 400, "text/plain; charset=utf-8", text(e.getMessage()));
                return;
            }
            // A new photo is ticked straight away, so it is on Home without another step on the TV.
            if (tick) {
                Set<String> t = store.ticked();
                t.add(id);
                store.setTicked(t);
            }
            reply(out, 200, "application/json; charset=utf-8", text("{\"id\":\"" + id + "\"}"));
            changed();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Writes the backup to a temporary file (its size must be known), then sends it as a download. */
    private void backup(OutputStream out, boolean withPhotos) throws IOException {
        File tmp = File.createTempFile("backup", ".zip", context.getCacheDir());
        try {
            try {
                Backup.write(context, tmp, withPhotos);
            } catch (IOException e) {
                Log.w(TAG, "backup failed", e);
                reply(out, 500, "text/plain; charset=utf-8", text("The TV could not make the backup: "
                        + e.getMessage()));
                return;
            }
            java.util.Calendar now = java.util.Calendar.getInstance();
            String name = BackupFormat.fileName(now.get(java.util.Calendar.YEAR),
                    now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH),
                    now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE));
            String head = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/zip\r\n"
                    + "Content-Length: " + tmp.length() + "\r\n"
                    + "Content-Disposition: attachment; filename=\"" + name + "\"\r\n"
                    + "Access-Control-Expose-Headers: Content-Disposition\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "X-Content-Type-Options: nosniff\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            try (InputStream f = new FileInputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = f.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Receives a backup ZIP, checks and stages it (Backup.stage), then asks the launcher to restart. */
    private void restore(InputStream in, OutputStream out, Map<String, String> h) throws IOException {
        long length;
        try {
            length = Long.parseLong(h.getOrDefault("content-length", "-1"));
        } catch (NumberFormatException e) {
            length = -1;
        }
        if (length <= 0 || length > BackupFormat.MAX_FILE_BYTES) {
            reply(out, 413, "text/plain; charset=utf-8", text("Backups up to 300 MB, please."));
            return;
        }
        File cache = context.getCacheDir();
        if (cache.getUsableSpace() < length * 2 + 50L * 1024 * 1024) {
            reply(out, 413, "text/plain; charset=utf-8", text("The TV doesn't have room for this backup."));
            return;
        }
        File tmp = File.createTempFile("restore", ".zip", cache);
        try {
            try (OutputStream f = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                long left = length;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(left, buf.length));
                    if (n < 0) throw new IOException("The upload stopped part way");
                    f.write(buf, 0, n);
                    left -= n;
                }
            }
            String message;
            try {
                message = Backup.stage(context, tmp);
            } catch (Backup.Problem e) {
                reply(out, 400, "text/plain; charset=utf-8", text(e.getMessage()));
                return;
            }
            reply(out, 200, "text/plain; charset=utf-8", text(message));
            Runnable r = onRestored;
            if (r != null) main.postDelayed(r, 1500);   // after the phone has had the answer
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private void other(String method, String path, String query, InputStream in, OutputStream out,
                       Map<String, String> h) throws IOException {
        Api a = api;
        if (a == null) {
            reply(out, 404, "text/plain; charset=utf-8", text("Not found"));
            return;
        }
        int length;
        try {
            length = Integer.parseInt(h.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            length = -1;
        }
        if (length < 0 || length > MAX_BODY) {
            reply(out, 413, "text/plain; charset=utf-8", text("That is too long."));
            return;
        }
        byte[] body = new byte[length];
        int got = 0;
        while (got < length) {
            int n = in.read(body, got, length - got);
            if (n < 0) throw new IOException("The request stopped part way");
            got += n;
        }
        Reply r;
        try {
            r = a.handle(method, path, query, body);
        } catch (RuntimeException e) {
            Log.w(TAG, "phone request failed: " + method + " " + path, e);
            r = Reply.text(500, "Something went wrong on the TV.");
        }
        if (r == null) r = Reply.text(404, "Not found");
        reply(out, r.status, r.type, r.body);
    }

    private void changed() {
        main.post(listener::onPhotosChanged);
    }

    private String photosJson() {
        List<String> ids = photos.list();
        Set<String> t = store.ticked();
        String current = store.current();
        StringBuilder b = new StringBuilder("{\"max\":").append(Photos.MAX_PHOTOS)
                .append(",\"enabled\":").append(store.enabled())
                .append(",\"current\":\"").append(current != null && Photos.validId(current) ? current : "")
                .append("\",\"photos\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":\"").append(ids.get(i)).append("\",\"ticked\":").append(t.contains(ids.get(i))).append('}');
        }
        return b.append("]}").toString();
    }

    private byte[] page(String asset) throws IOException {
        return readAll(context.getAssets().open(asset));
    }

    // ---- HTTP plumbing --------------------------------------------------------------------------------------------

    /** Reads up to the blank line after the headers (byte by byte, so the body stays in the stream). */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0, c;
        while ((c = in.read()) != -1) {
            b.write(c);
            if (b.size() > MAX_HEADER) return null;
            state = (c == '\r' && (state == 0 || state == 2)) ? state + 1
                    : (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) return b.toString("ISO-8859-1");
        }
        return null;
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static void reply(OutputStream out, int status, String type, byte[] body) throws IOException {
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : status == 403 ? "Forbidden"
                : status == 429 ? "Too Many Requests" : status == 413 ? "Payload Too Large" : status == 409 ? "Conflict"
                : status == 400 ? "Bad Request" : status == 500 ? "Internal Server Error" : "Error";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
