package com.chakriau.tvhome.v2.backup;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BackupFormatTest {

    private static final BackupFormat.IdCheck IDS = id -> id.matches("[0-9a-z]{6,24}");

    @Test
    public void everySettingsFileIsBackedUpExceptThisTVsOwn() {
        assertTrue(BackupFormat.includeFile("special_days"));
        assertTrue(BackupFormat.includeFile("parts_theme1"));
        assertTrue(BackupFormat.includeFile("wallpapers"));
        assertFalse(BackupFormat.includeFile("safe_mode"));
        assertFalse(BackupFormat.includeFile("sky_test"));
        assertFalse(BackupFormat.includeFile("../etc"));
        assertFalse(BackupFormat.includeFile("WebViewChromiumPrefs"));
        assertFalse(BackupFormat.includeFile(""));
        assertFalse(BackupFormat.includeFile(null));
    }

    @Test
    public void thePhonePagePinStaysTheTVs() {
        assertTrue(BackupFormat.keep("wallpapers", "pin"));
        assertFalse(BackupFormat.keep("wallpapers", "interval"));
        assertFalse(BackupFormat.keep("weather", "pin"));
    }

    @Test
    public void valuesKeepTheirType() {
        Object[] values = {true, false, 7, -3, 1700000000000L, 0.5f, "Chennai|13.08", ""};
        for (Object v : values) {
            String[] tv = BackupFormat.encode(v);
            assertNotNull(String.valueOf(v), tv);
            Object back = BackupFormat.decode(tv[0], tv[1]);
            assertEquals(v, back);
            assertEquals(v.getClass(), back.getClass());
        }
        assertNull(BackupFormat.encode(new java.util.HashSet<String>()));   // sets are written as lists
        assertNull(BackupFormat.encode(3.0));                                // doubles never come from settings
    }

    @Test
    public void unreadableValuesAreLeftOut() {
        assertNull(BackupFormat.decode("b", "yes"));
        assertNull(BackupFormat.decode("i", "1.5"));
        assertNull(BackupFormat.decode("l", "x"));
        assertNull(BackupFormat.decode("f", "NaN"));
        assertNull(BackupFormat.decode("q", "1"));
        assertNull(BackupFormat.decode(null, "1"));
        assertNull(BackupFormat.decode("s", null));
    }

    @Test
    public void onlyPhotoEntriesWithGoodIdsAreTaken() {
        assertArrayEquals(new String[]{"abc123def", "photo"}, BackupFormat.photoEntry("photos/abc123def.jpg", IDS));
        assertArrayEquals(new String[]{"abc123def", "thumb"},
                BackupFormat.photoEntry("photos/thumbs/abc123def.jpg", IDS));
        assertNull(BackupFormat.photoEntry("photos/../../shared_prefs/x.jpg", IDS));
        assertNull(BackupFormat.photoEntry("photos/abc123def.png", IDS));
        assertNull(BackupFormat.photoEntry("photos/a.jpg", IDS));
        assertNull(BackupFormat.photoEntry("other/abc123def.jpg", IDS));
        assertNull(BackupFormat.photoEntry("backup.json", IDS));
        assertNull(BackupFormat.photoEntry(null, IDS));
    }

    @Test
    public void jpegsAreRecognised() {
        assertTrue(BackupFormat.looksLikeJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}, 4));
        assertFalse(BackupFormat.looksLikeJpeg(new byte[]{'P', 'K', 3, 4}, 4));
        assertFalse(BackupFormat.looksLikeJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8}, 2));
    }

    @Test
    public void manifestsAreChecked() {
        assertNull(BackupFormat.checkManifest(1, "tvhome-v2"));
        assertNotNull(BackupFormat.checkManifest(1, "tvhome"));
        assertNotNull(BackupFormat.checkManifest(0, "tvhome-v2"));
        assertTrue(BackupFormat.checkManifest(2, "tvhome-v2").contains("newer"));
    }

    @Test
    public void theFileNameHasTheDate() {
        assertEquals("tvhome-backup-2026-11-08-0905.zip", BackupFormat.fileName(2026, 11, 8, 9, 5));
    }
}
