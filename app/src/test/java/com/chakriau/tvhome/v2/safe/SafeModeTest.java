package com.chakriau.tvhome.v2.safe;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeModeTest {

    private static final long MIN = 60_000L;

    @Test
    public void threeCrashesInTenMinutesMeanSafeMode() {
        long now = 1_000_000_000L;
        assertFalse(SafeMode.shouldEnter(new long[]{now}, now));
        assertFalse(SafeMode.shouldEnter(new long[]{now - MIN, now}, now));
        assertTrue(SafeMode.shouldEnter(new long[]{now - 9 * MIN, now - MIN, now}, now));
        // Spread over more than ten minutes: just bad luck, not a launcher that can't start.
        assertFalse(SafeMode.shouldEnter(new long[]{now - 11 * MIN, now - MIN, now}, now));
    }

    @Test
    public void onlyTheLastFiveCrashesAreKept() {
        long[] t = new long[0];
        for (long i = 1; i <= 7; i++) t = SafeMode.add(t, i);
        assertArrayEquals(new long[]{3, 4, 5, 6, 7}, t);
    }

    @Test
    public void crashTimesAreSavedAndReadBack() {
        long[] t = {1L, 22L, 333L};
        assertArrayEquals(t, SafeMode.parse(SafeMode.format(t)));
        assertArrayEquals(new long[0], SafeMode.parse(""));
        assertArrayEquals(new long[0], SafeMode.parse(null));
        assertArrayEquals(new long[]{5L, 7L}, SafeMode.parse("5,x,7"));
    }

    @Test
    public void aNewerLauncherTriesNormalHomeAgain() {
        assertTrue(SafeMode.newerVersion(56, 57));
        assertFalse(SafeMode.newerVersion(57, 57));
    }

    @Test
    public void longReportsAreCut() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 100; i++) b.append("at line ").append(i).append('\n');
        String t = SafeMode.trim(b.toString());
        assertTrue(t.startsWith("at line 0"));
        assertTrue(t.contains("more lines"));
        assertEquals(SafeMode.MAX_LINES + 1, t.split("\n").length);
        assertEquals("", SafeMode.trim(null));
    }
}
