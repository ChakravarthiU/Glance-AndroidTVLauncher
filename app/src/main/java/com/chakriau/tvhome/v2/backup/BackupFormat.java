package com.chakriau.tvhome.v2.backup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The backup file's rules (Requirements.md 5.11), plain Java so they are unit-tested. A backup is a ZIP:
 *
 * <pre>
 * backup.json                {"format": 1, "app": "tvhome-v2", "version": "2.0.0 (57)", "created": "...",
 *                             "photos": 12, "prefs": {"&lt;file&gt;": {"&lt;key&gt;": ["b"|"i"|"l"|"f"|"s"|"set", value]}}}
 * photos/&lt;id&gt;.jpg            each wallpaper photo, 1920 × 1080 (only with "Include the wallpaper photos")
 * photos/thumbs/&lt;id&gt;.jpg     its 320 × 180 thumbnail
 * </pre>
 *
 * Settings are every SharedPreferences file of the app except the ones in {@link #NEVER_FILES}; values keep their
 * type. On restore, keys in {@link #KEEP} keep the TV's current value (the phone page's PIN, so the phone that
 * restored isn't locked out).
 */
public final class BackupFormat {

    public static final int FORMAT = 1;
    public static final String APP = "tvhome-v2";
    public static final String MANIFEST = "backup.json";
    public static final String PHOTOS = "photos/", THUMBS = "photos/thumbs/";

    /**
     * Not backed up: the crash record and safe mode (about this TV's run, not your setup), and the phone page's
     * 15-minute tests of the sky, banner and lights.
     */
    public static final Set<String> NEVER_FILES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "safe_mode", "sky_test", "decor_test")));

    /** Kept from the TV on restore, never taken from the backup: file → keys. */
    static final Map<String, Set<String>> KEEP;

    static {
        Map<String, Set<String>> k = new TreeMap<>();
        k.put("wallpapers", Collections.singleton("pin"));
        KEEP = Collections.unmodifiableMap(k);
    }

    /** Limits a restore checks before touching anything. */
    public static final long MAX_FILE_BYTES = 300L * 1024 * 1024;
    public static final long MAX_PHOTO_BYTES = 8L * 1024 * 1024;
    public static final int MAX_ENTRIES = 2 * 100 + 10;
    public static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;

    private BackupFormat() {
    }

    /** Whether a SharedPreferences file (name without ".xml") goes into a backup and comes back from one. */
    public static boolean includeFile(String name) {
        return name != null && name.matches("[a-z0-9_]{1,60}") && !NEVER_FILES.contains(name);
    }

    /** Whether this key keeps the TV's current value on restore. */
    public static boolean keep(String file, String key) {
        Set<String> keys = KEEP.get(file);
        return keys != null && keys.contains(key);
    }

    /** A setting's value as [type, text]: "b" boolean, "i" int, "l" long, "f" float, "s" string; null if unknown. */
    public static String[] encode(Object v) {
        if (v instanceof Boolean) return new String[]{"b", v.toString()};
        if (v instanceof Integer) return new String[]{"i", v.toString()};
        if (v instanceof Long) return new String[]{"l", v.toString()};
        if (v instanceof Float) return new String[]{"f", v.toString()};
        if (v instanceof String) return new String[]{"s", (String) v};
        return null;   // a string set is written separately, as a list
    }

    /** The value back from [type, text], or null if it can't be read (that key is then left out). */
    public static Object decode(String type, String text) {
        if (type == null || text == null) return null;
        try {
            switch (type) {
                case "b":
                    if (!text.equals("true") && !text.equals("false")) return null;
                    return Boolean.valueOf(text);
                case "i":
                    return Integer.valueOf(text);
                case "l":
                    return Long.valueOf(text);
                case "f":
                    float f = Float.parseFloat(text);
                    return Float.isNaN(f) || Float.isInfinite(f) ? null : f;
                case "s":
                    return text;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A ZIP entry that is a photo: {id, "photo" | "thumb"}, or null for anything else (ignored on restore). */
    public static String[] photoEntry(String name, IdCheck valid) {
        if (name == null) return null;
        String rest, kind;
        if (name.startsWith(THUMBS)) {
            rest = name.substring(THUMBS.length());
            kind = "thumb";
        } else if (name.startsWith(PHOTOS)) {
            rest = name.substring(PHOTOS.length());
            kind = "photo";
        } else {
            return null;
        }
        if (!rest.endsWith(".jpg")) return null;
        String id = rest.substring(0, rest.length() - 4);
        return valid.ok(id) ? new String[]{id, kind} : null;
    }

    /** Photo ids are checked by the photo store's own rule. */
    public interface IdCheck {
        boolean ok(String id);
    }

    /** Whether the first bytes are a JPEG's. */
    public static boolean looksLikeJpeg(byte[] head, int n) {
        return n >= 3 && (head[0] & 0xff) == 0xFF && (head[1] & 0xff) == 0xD8 && (head[2] & 0xff) == 0xFF;
    }

    /** Problems with a backup's manifest fields, or null if it can be restored. */
    public static String checkManifest(int format, String app) {
        if (!APP.equals(app)) return "This isn't a TV Home V2 backup.";
        if (format < 1) return "This backup can't be read.";
        if (format > FORMAT) return "This backup is from a newer launcher. Update the launcher on the TV first.";
        return null;
    }

    /** The files a restore will write, sorted (for the message on the phone). */
    public static List<String> sortedFiles(Set<String> names) {
        List<String> out = new ArrayList<>(names);
        Collections.sort(out);
        return out;
    }

    /** "tvhome-backup-2026-11-08-1203.zip" */
    public static String fileName(int year, int month, int day, int hour, int minute) {
        return String.format(java.util.Locale.ROOT, "tvhome-backup-%04d-%02d-%02d-%02d%02d.zip", year, month, day,
                hour, minute);
    }
}
