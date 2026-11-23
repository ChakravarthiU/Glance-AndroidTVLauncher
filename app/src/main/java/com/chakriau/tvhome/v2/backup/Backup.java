package com.chakriau.tvhome.v2.backup;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Process;
import android.util.Log;

import com.chakriau.tvhome.v2.MainActivity;
import com.chakriau.tvhome.v2.safe.CrashLog;
import com.chakriau.tvhome.v2.wallpapers.Photos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backing up and restoring everything the owner set up (Requirements.md 5.11; the file format is in
 * {@link BackupFormat}). Made and restored from the phone page's Backup tab.
 *
 * <p>A restore is checked in full and staged in {@code files/restore/} first; the launcher then restarts and
 * {@link #applyPending} (run by {@code TvHomeApp} before anything reads a setting) puts the settings and photos in
 * place. Nothing the app holds in memory can write old values back over the restored ones that way.
 */
public final class Backup {

    private static final String TAG = "TvHomeBackup";
    private static final String STAGE = "restore";
    private static final String READY = "ready";
    private static final String SETTINGS = "settings.json";

    private Backup() {
    }

    /** A problem with a backup, worded for the phone page. */
    public static final class Problem extends IOException {
        Problem(String message) {
            super(message);
        }
    }

    // ---- Backing up -----------------------------------------------------------------------------------------------

    /** How many photos there are and their size in MB, for the phone page. */
    public static int[] photoInfo(Context c) {
        Photos photos = new Photos(c);
        long bytes = 0;
        List<String> ids = photos.list();
        for (String id : ids) {
            File f = photos.file(id), t = photos.thumb(id);
            if (f != null) bytes += f.length();
            if (t != null) bytes += t.length();
        }
        return new int[]{ids.size(), (int) Math.max(ids.isEmpty() ? 0 : 1, Math.round(bytes / 1048576.0))};
    }

    /** Writes a backup ZIP to {@code to}. */
    public static void write(Context context, File to, boolean withPhotos) throws IOException {
        Context c = context.getApplicationContext();
        Photos photos = new Photos(c);
        List<String> ids = withPhotos ? photos.list() : java.util.Collections.emptyList();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(to)))) {
            JSONObject manifest = new JSONObject()
                    .put("format", BackupFormat.FORMAT)
                    .put("app", BackupFormat.APP)
                    .put("version", version(c))
                    .put("created", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()))
                    .put("photos", ids.size())
                    .put("prefs", prefs(c));
            zip.putNextEntry(new ZipEntry(BackupFormat.MANIFEST));
            zip.write(manifest.toString(1).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.setLevel(0);   // photos are JPEGs already: storing them is as small and much faster
            for (String id : ids) {
                copyInto(zip, BackupFormat.PHOTOS + id + ".jpg", photos.file(id));
                copyInto(zip, BackupFormat.THUMBS + id + ".jpg", photos.thumb(id));
            }
        } catch (JSONException e) {
            throw new IOException("Could not write the settings: " + e.getMessage());
        }
    }

    /** Every included settings file, read through SharedPreferences so it is up to date to the last change. */
    private static JSONObject prefs(Context c) throws JSONException {
        JSONObject all = new JSONObject();
        for (String name : prefsFiles(c)) {
            Map<String, ?> values = c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
            JSONObject file = new JSONObject();
            for (Map.Entry<String, ?> e : values.entrySet()) {
                if (BackupFormat.keep(name, e.getKey())) continue;   // the TV's own (the PIN)
                Object v = e.getValue();
                if (v instanceof Set) {
                    JSONArray list = new JSONArray();
                    for (Object o : (Set<?>) v) list.put(String.valueOf(o));
                    file.put(e.getKey(), new JSONArray().put("set").put(list));
                } else {
                    String[] tv = BackupFormat.encode(v);
                    if (tv != null) file.put(e.getKey(), new JSONArray().put(tv[0]).put(tv[1]));
                }
            }
            all.put(name, file);
        }
        return all;
    }

    /** The app's settings files on disk (names without ".xml") that go into a backup. */
    private static Set<String> prefsFiles(Context c) {
        Set<String> out = new HashSet<>();
        String[] names = new File(c.getApplicationInfo().dataDir, "shared_prefs").list();
        if (names == null) return out;
        for (String n : names) {
            if (!n.endsWith(".xml")) continue;
            String base = n.substring(0, n.length() - 4);
            if (BackupFormat.includeFile(base)) out.add(base);
        }
        return out;
    }

    private static void copyInto(ZipOutputStream zip, String name, File f) throws IOException {
        if (f == null || !f.isFile()) return;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
        }
        zip.closeEntry();
    }

    // ---- Restoring ------------------------------------------------------------------------------------------------

    /**
     * Checks a backup ZIP and stages it for the next start. Returns a line for the phone ("Restoring 14 settings
     * files and 12 photos"); throws with a message the phone can show if the file can't be restored. Nothing on
     * the TV changes unless it succeeds.
     */
    public static String stage(Context context, File zipFile) throws IOException {
        Context c = context.getApplicationContext();
        File stage = new File(c.getFilesDir(), STAGE);
        deleteTree(stage);
        File photosDir = new File(stage, "photos"), thumbsDir = new File(photosDir, "thumbs");
        if (!thumbsDir.mkdirs()) throw new Problem("The TV has no room to restore.");
        JSONObject manifest = null;
        Set<String> photos = new HashSet<>(), thumbs = new HashSet<>();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entries > BackupFormat.MAX_ENTRIES) throw new Problem("This backup has too many files.");
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (BackupFormat.MANIFEST.equals(name)) {
                    byte[] b = readLimited(zip, BackupFormat.MAX_MANIFEST_BYTES);
                    try {
                        manifest = new JSONObject(new String(b, StandardCharsets.UTF_8));
                    } catch (JSONException ex) {
                        throw new Problem("This backup's settings can't be read.");
                    }
                    continue;
                }
                String[] p = BackupFormat.photoEntry(name, Photos::validId);
                if (p == null) continue;   // something else: ignored
                boolean thumb = p[1].equals("thumb");
                if (!thumb && photos.size() >= Photos.MAX_PHOTOS) continue;
                File out = new File(thumb ? thumbsDir : photosDir, p[0] + ".jpg");
                copyJpeg(zip, out);
                (thumb ? thumbs : photos).add(p[0]);
            }
        } catch (Problem ex) {
            deleteTree(stage);
            throw ex;
        } catch (IOException ex) {   // the ZIP itself is broken, or the TV couldn't write
            deleteTree(stage);
            Log.w(TAG, "restore: " + ex);
            throw new Problem("This file can't be read as a backup. Choose the .zip file Download backup made.");
        }
        if (manifest == null) {
            deleteTree(stage);
            throw new Problem("This isn't a TV Home backup (no backup.json inside).");
        }
        String problem = BackupFormat.checkManifest(manifest.optInt("format", 0), manifest.optString("app"));
        JSONObject prefs = manifest.optJSONObject("prefs");
        if (problem == null && prefs == null) problem = "This backup has no settings in it.";
        if (problem != null) {
            deleteTree(stage);
            throw new Problem(problem);
        }
        // Photos come back only if the backup has them; then the TV's set is replaced by the backup's.
        for (String t : thumbs) if (!photos.contains(t)) deleteQuietly(new File(thumbsDir, t + ".jpg"));
        try {
            JSONObject settings = new JSONObject().put("prefs", prefs).put("hasPhotos", !photos.isEmpty());
            writeFile(new File(stage, SETTINGS), settings.toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException ex) {
            deleteTree(stage);
            throw new Problem("This backup's settings can't be read.");
        }
        writeFile(new File(stage, READY), new byte[0]);   // last: only a complete stage is ever applied
        int files = 0;
        for (Iterator<String> it = prefs.keys(); it.hasNext(); ) if (BackupFormat.includeFile(it.next())) files++;
        return "Restoring " + files + " settings files" + (photos.isEmpty() ? "" : " and " + photos.size()
                + " photos") + ". The launcher restarts on the TV.";
    }

    /** Restarts the launcher so {@link #applyPending} runs before anything reads a setting. */
    public static void restart(Activity activity) {
        Context c = activity.getApplicationContext();
        Intent home = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7, home, PendingIntent.FLAG_IMMUTABLE
                | PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC, System.currentTimeMillis() + 800, pi);
        activity.finishAffinity();
        Process.killProcess(Process.myPid());
    }

    /**
     * Puts a staged restore in place, if there is one. Run once at the start of the app's process, before any
     * setting is read. A restore that fails part way is recorded as a problem (phone page › Backup) and dropped, so
     * it can't fail at every start.
     */
    public static void applyPending(Context context) {
        Context c = context.getApplicationContext();
        File stage = new File(c.getFilesDir(), STAGE);
        if (!new File(stage, READY).isFile()) {
            if (stage.exists()) deleteTree(stage);   // an unfinished stage: never applied
            return;
        }
        try {
            JSONObject settings = new JSONObject(new String(readAll(new File(stage, SETTINGS)),
                    StandardCharsets.UTF_8));
            applyPrefs(c, settings.getJSONObject("prefs"));
            if (settings.optBoolean("hasPhotos")) replacePhotos(c, new File(stage, "photos"));
            Log.i(TAG, "restored a backup");
        } catch (Exception e) {
            Log.w(TAG, "restore failed", e);
            CrashLog.recordProblem(c, "Restoring a backup failed", e);
        } finally {
            deleteTree(stage);
        }
    }

    private static void applyPrefs(Context c, JSONObject all) throws JSONException {
        // Every included file is cleared, so settings the backup doesn't have go back to their defaults.
        Set<String> names = prefsFiles(c);
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String n = it.next();
            if (BackupFormat.includeFile(n)) names.add(n);
        }
        for (String name : names) {
            SharedPreferences prefs = c.getSharedPreferences(name, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = prefs.edit();
            Map<String, ?> current = prefs.getAll();
            for (String key : current.keySet()) if (!BackupFormat.keep(name, key)) ed.remove(key);
            JSONObject file = all.optJSONObject(name);
            if (file != null) {
                for (Iterator<String> keys = file.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    if (BackupFormat.keep(name, key)) continue;
                    JSONArray tv = file.optJSONArray(key);
                    if (tv == null || tv.length() != 2) continue;
                    String type = tv.optString(0);
                    if (type.equals("set")) {
                        JSONArray list = tv.optJSONArray(1);
                        if (list == null) continue;
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < list.length(); i++) set.add(list.optString(i));
                        ed.putStringSet(key, set);
                        continue;
                    }
                    Object v = BackupFormat.decode(type, tv.optString(1, null));
                    if (v instanceof Boolean) ed.putBoolean(key, (Boolean) v);
                    else if (v instanceof Integer) ed.putInt(key, (Integer) v);
                    else if (v instanceof Long) ed.putLong(key, (Long) v);
                    else if (v instanceof Float) ed.putFloat(key, (Float) v);
                    else if (v instanceof String) ed.putString(key, (String) v);
                }
            }
            ed.commit();   // written now: the restore is complete before Home starts
        }
    }

    /** The backup's photos replace the TV's, thumbnails with them. */
    private static void replacePhotos(Context c, File staged) throws IOException {
        Photos photos = new Photos(c);
        for (String id : photos.list()) photos.delete(id);
        File[] files = staged.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".jpg")) continue;
            String id = f.getName().substring(0, f.getName().length() - 4);
            File to = photos.file(id), thumbTo = photos.thumb(id);
            if (to == null) continue;
            File parent = to.getParentFile(), thumbParent = thumbTo.getParentFile();
            if (parent != null) parent.mkdirs();
            if (thumbParent != null) thumbParent.mkdirs();
            if (!f.renameTo(to)) throw new IOException("could not move photo " + id);
            File thumb = new File(new File(staged, "thumbs"), f.getName());
            if (thumb.isFile() && !thumb.renameTo(thumbTo)) deleteQuietly(thumb);
        }
    }

    // ---- Files ----------------------------------------------------------------------------------------------------

    private static void copyJpeg(InputStream in, File out) throws IOException {
        byte[] buf = new byte[32 * 1024];
        long total = 0;
        boolean first = true;
        try (OutputStream o = new FileOutputStream(out)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (first) {
                    if (!BackupFormat.looksLikeJpeg(buf, n)) throw new Problem("A photo in the backup is damaged.");
                    first = false;
                }
                total += n;
                if (total > BackupFormat.MAX_PHOTO_BYTES) throw new Problem("A photo in the backup is too big.");
                o.write(buf, 0, n);
            }
        }
        if (first) throw new Problem("A photo in the backup is empty.");
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > max) throw new Problem("This backup's settings are too big.");
        }
        return b.toByteArray();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readLimited(in, BackupFormat.MAX_MANIFEST_BYTES * 2);
        }
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        deleteQuietly(f);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void deleteQuietly(File f) {
        f.delete();
    }

    @SuppressWarnings("deprecation")   // versionCode: getLongVersionCode() needs Android 9, V2 runs from 8
    static String version(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName + " (" + p.versionCode + ")";
        } catch (Exception e) {
            return "?";
        }
    }
}
