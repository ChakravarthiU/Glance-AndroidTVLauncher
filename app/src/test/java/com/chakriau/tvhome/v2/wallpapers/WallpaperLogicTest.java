package com.chakriau.tvhome.v2.wallpapers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class WallpaperLogicTest {

    private static final List<String> ABC = Arrays.asList("a", "b", "c");
    private static final long MIN = 60_000L;

    // ---- Rotation -------------------------------------------------------------------------------------------------

    @Test
    public void inTurnGoesThroughTheListAndWraps() {
        assertEquals("a", Rotation.next(ABC, null, false, new Random(1)));
        assertEquals("b", Rotation.next(ABC, "a", false, new Random(1)));
        assertEquals("a", Rotation.next(ABC, "c", false, new Random(1)));
        assertEquals("a", Rotation.next(ABC, "gone", false, new Random(1)));
    }

    @Test
    public void shuffleNeverRepeatsThePhotoOnScreen() {
        Random r = new Random(42);
        String cur = "a";
        for (int i = 0; i < 200; i++) {
            String next = Rotation.next(ABC, cur, true, r);
            assertNotEquals(cur, next);
            assertTrue(ABC.contains(next));
            cur = next;
        }
    }

    @Test
    public void oneTickedPhotoStaysFixedAndNoneShowsNothing() {
        List<String> one = Collections.singletonList("x");
        assertEquals("x", Rotation.current(one, "x", 1, 1 + 999 * MIN, 5, true, new Random()));
        assertEquals("x", Rotation.current(one, null, 0, 10, 5, false, new Random()));
        assertNull(Rotation.current(Collections.emptyList(), "x", 0, 10, 5, false, new Random()));
    }

    @Test
    public void currentKeepsThePhotoUntilItsTimeIsUp() {
        long shown = 1_000_000;
        assertEquals("a", Rotation.current(ABC, "a", shown, shown + 14 * MIN, 15, false, new Random()));
        assertEquals("b", Rotation.current(ABC, "a", shown, shown + 15 * MIN, 15, false, new Random()));
        // Unticked while on screen: moves on at once
        assertEquals("a", Rotation.current(Arrays.asList("a", "c"), "b", shown, shown + MIN, 15, false, new Random()));
    }

    @Test
    public void dueHandlesFirstShowAndAClockSetBack() {
        assertTrue(Rotation.due(0, 5, 5));
        assertTrue(Rotation.due(100 * MIN, 50 * MIN, 5));
        assertFalse(Rotation.due(10 * MIN, 14 * MIN, 5));
        assertEquals(MIN, Rotation.untilDue(10 * MIN, 14 * MIN, 5));
        assertEquals(0, Rotation.untilDue(10 * MIN, 16 * MIN, 5));
    }

    @Test
    public void intervalsStepAndValidate() {
        assertEquals(15, Rotation.nextInterval(5));
        assertEquals(5, Rotation.nextInterval(60));
        assertEquals(Rotation.DEFAULT_INTERVAL, Rotation.nextInterval(7));
        assertEquals(30, Rotation.validInterval(30));
        assertEquals(Rotation.DEFAULT_INTERVAL, Rotation.validInterval(0));
    }

    // ---- PIN ------------------------------------------------------------------------------------------------------

    @Test
    public void pinLocksAfterRepeatedWrongAttemptsThenUnlocks() {
        PinGuard g = new PinGuard("123456");
        long t = 1000;
        for (int i = 1; i < PinGuard.MAX_WRONG; i++) assertEquals(PinGuard.Result.WRONG, g.check("000000", t));
        assertEquals(PinGuard.Result.LOCKED, g.check("000000", t));
        // Even the right PIN is refused while locked
        assertEquals(PinGuard.Result.LOCKED, g.check("123456", t + PinGuard.LOCK_MS - 1));
        assertEquals(5, g.lockedMinutes(t));
        assertEquals(PinGuard.Result.OK, g.check("123456", t + PinGuard.LOCK_MS));
        assertEquals(0, g.lockedMinutes(t + PinGuard.LOCK_MS));
    }

    @Test
    public void rightPinResetsTheWrongCount() {
        PinGuard g = new PinGuard("123456");
        for (int i = 1; i < PinGuard.MAX_WRONG; i++) g.check("bad", 0);
        assertEquals(PinGuard.Result.OK, g.check("123456", 0));
        assertEquals(PinGuard.Result.WRONG, g.check("bad", 0));
        assertEquals(PinGuard.Result.WRONG, g.check(null, 0));
    }

    @Test
    public void changingThePinClearsTheLock() {
        PinGuard g = new PinGuard("111111");
        for (int i = 0; i < PinGuard.MAX_WRONG; i++) g.check("x", 0);
        g.setPin("222222");
        assertEquals(PinGuard.Result.OK, g.check("222222", 1));
        assertEquals(PinGuard.Result.WRONG, g.check("111111", 1));
    }

    @Test
    public void newPinsAreSixDigits() {
        String p = PinGuard.newPin(new SecureRandom());
        assertTrue(p.matches("[0-9]{6}"));
    }

    // ---- Sizes ----------------------------------------------------------------------------------------------------

    @Test
    public void sampleSizeStillCoversTheFrame() {
        assertEquals(2, PhotoSize.sampleSize(4000, 3000, 1920, 1080));
        assertEquals(1, PhotoSize.sampleSize(1920, 1080, 1920, 1080));
        assertEquals(4, PhotoSize.sampleSize(8000, 4500, 1920, 1080));
        assertEquals(1, PhotoSize.sampleSize(1000, 800, 1920, 1080));
    }

    @Test
    public void coverCropKeepsTheCentreInTheFramesShape() {
        assertArrayEquals(new int[]{0, 375, 4000, 2625}, PhotoSize.coverCrop(4000, 3000, 1920, 1080)); // 4:3
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, PhotoSize.coverCrop(1920, 1080, 1920, 1080));
        assertArrayEquals(new int[]{1080, 0, 2920, 1035}, PhotoSize.coverCrop(4000, 1035, 1840, 1035)); // panorama
        int[] portrait = PhotoSize.coverCrop(1080, 1920, 1920, 1080);
        assertEquals(1080, portrait[2] - portrait[0]);
        assertEquals(607, portrait[3] - portrait[1]);
    }

    @Test
    public void hugePicturesAreRefused() {
        assertTrue(PhotoSize.accepted(4000, 3000));
        assertFalse(PhotoSize.accepted(65535, 2159));
        assertFalse(PhotoSize.accepted(0, 100));
    }
}
