package com.chakriau.tvhome.v2.watching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.junit.Test;

public class ContinueWatchingTest {

    private static final String ME = "com.chakriau.tvhome.v2";
    private static final Set<String> INSTALLED = new HashSet<>(Arrays.asList("yt", "prime", "hotstar", "zee5", ME));
    private static final Set<String> NONE = Collections.emptySet();
    private static long nextId = 1;

    private static ContinueWatching.Item item(String pkg, long engaged, int type, long pos, long dur) {
        return new ContinueWatching.Item(nextId++, pkg, "Title " + pkg, null, null, null, pos, dur, engaged, type,
                true, "intent:#Intent;end", null);
    }

    private static ContinueWatching.Item episode(String season, String ep, String epTitle, int type, long pos,
                                                 long dur) {
        return new ContinueWatching.Item(1, "yt", "Show", season, ep, epTitle, pos, dur, 0, type, true, null, null);
    }

    // ---- Picking --------------------------------------------------------------------------------------------------

    @Test
    public void newestItemFromEachAppNewestFirst() {
        List<ContinueWatching.Item> all = Arrays.asList(
                item("yt", 100, ContinueWatching.TYPE_CONTINUE, 10, 100),
                item("prime", 300, ContinueWatching.TYPE_CONTINUE, 10, 100),
                item("yt", 200, ContinueWatching.TYPE_CONTINUE, 10, 100));
        List<ContinueWatching.Item> row = ContinueWatching.pick(all, INSTALLED, NONE, ME,
                ContinueWatching.Mode.STARTED, 1, 10);
        assertEquals(2, row.size());
        assertEquals("prime", row.get(0).packageName);
        assertEquals(200, row.get(1).lastEngagedMs);
    }

    @Test
    public void startedOnlySkipsNextEpisodesAtZeroButAllKeepsThem() {
        ContinueWatching.Item next = item("hotstar", 500, ContinueWatching.TYPE_NEXT, 0, 100);
        ContinueWatching.Item older = item("hotstar", 400, ContinueWatching.TYPE_CONTINUE, 30, 100);
        List<ContinueWatching.Item> all = Arrays.asList(next, older);
        assertEquals(older,
                ContinueWatching.pick(all, INSTALLED, NONE, ME, ContinueWatching.Mode.STARTED, 1, 10).get(0));
        assertEquals(next, ContinueWatching.pick(all, INSTALLED, NONE, ME, ContinueWatching.Mode.ALL, 1, 10).get(0));
    }

    @Test
    public void aPositionCountsAsStartedWhateverTheType() {
        assertTrue(ContinueWatching.started(item("yt", 1, ContinueWatching.TYPE_NEXT, 5, 100)));
        assertTrue(ContinueWatching.started(item("yt", 1, ContinueWatching.TYPE_CONTINUE, 0, 0)));
        assertFalse(ContinueWatching.started(item("yt", 1, ContinueWatching.TYPE_WATCHLIST, 0, 100)));
    }

    @Test
    public void skipsOwnUninstalledTurnedOffHiddenAndUntitledItems() {
        ContinueWatching.Item own = item(ME, 9, 0, 1, 10);
        ContinueWatching.Item gone = item("netflix", 8, 0, 1, 10);
        ContinueWatching.Item off = item("zee5", 7, 0, 1, 10);
        ContinueWatching.Item notShown = new ContinueWatching.Item(50, "prime", "X", null, null, null, 1, 10, 6, 0,
                false, null, null);
        ContinueWatching.Item untitled = new ContinueWatching.Item(51, "yt", " ", null, null, null, 1, 10, 5, 0, true,
                null, null);
        ContinueWatching.Item noApp = new ContinueWatching.Item(52, null, "Y", null, null, null, 1, 10, 4, 0, true,
                null, null);
        ContinueWatching.Item ok = item("hotstar", 1, 0, 1, 10);
        List<ContinueWatching.Item> row = ContinueWatching.pick(
                Arrays.asList(own, gone, off, notShown, untitled, noApp, ok), INSTALLED,
                Collections.singleton("zee5"), ME, ContinueWatching.Mode.ALL, 1, 10);
        assertEquals(Collections.singletonList(ok), row);
    }

    @Test
    public void stopsAtTheMaximum() {
        Set<String> many = new HashSet<>();
        List<ContinueWatching.Item> all = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add("app" + i);
            all.add(item("app" + i, i, 0, 1, 10));
        }
        List<ContinueWatching.Item> row = ContinueWatching.pick(all, many, NONE, ME, ContinueWatching.Mode.STARTED,
                1, 10);
        assertEquals(10, row.size());
        assertEquals("app14", row.get(0).packageName);
    }

    @Test
    public void severalFromOneAppUpToTheLimitsNewestFirst() {
        List<ContinueWatching.Item> all = Arrays.asList(
                item("yt", 9, 0, 1, 10), item("yt", 8, 0, 1, 10), item("yt", 7, 0, 1, 10),
                item("prime", 6, 0, 1, 10), item("prime", 5, 0, 1, 10), item("zee5", 4, 0, 1, 10));
        List<ContinueWatching.Item> row = ContinueWatching.pick(all, INSTALLED, NONE, ME,
                ContinueWatching.Mode.STARTED, 2, 10);
        assertEquals(5, row.size());
        assertEquals(Arrays.asList(9L, 8L, 6L, 5L, 4L), engaged(row));
        row = ContinueWatching.pick(all, INSTALLED, NONE, ME, ContinueWatching.Mode.STARTED, 3, 4);
        assertEquals(Arrays.asList(9L, 8L, 7L, 6L), engaged(row));
    }

    private static List<Long> engaged(List<ContinueWatching.Item> row) {
        List<Long> out = new java.util.ArrayList<>();
        for (ContinueWatching.Item i : row) out.add(i.lastEngagedMs);
        return out;
    }

    @Test
    public void choicesCycleAndBadSavedValuesFallBack() {
        assertEquals(2, ContinueWatching.nextChoice(ContinueWatching.PER_APP_CHOICES, 1));
        assertEquals(1, ContinueWatching.nextChoice(ContinueWatching.PER_APP_CHOICES, 5));
        assertEquals(15, ContinueWatching.nextChoice(ContinueWatching.TOTAL_CHOICES, 10));
        assertEquals(5, ContinueWatching.nextChoice(ContinueWatching.TOTAL_CHOICES, 7));
        assertEquals(10, ContinueWatching.validChoice(ContinueWatching.TOTAL_CHOICES, 0, 10));
        assertEquals(20, ContinueWatching.validChoice(ContinueWatching.TOTAL_CHOICES, 20, 10));
    }

    @Test
    public void countsItemsPerAppInOrderOfNewest() {
        Map<String, Integer> counts = ContinueWatching.countByApp(Arrays.asList(
                item("yt", 1, 1, 0, 0), item("prime", 5, 1, 0, 0), item("yt", 3, 1, 0, 0), item("netflix", 9, 0, 0, 0)),
                INSTALLED, ME);
        assertEquals(Arrays.asList("prime", "yt"), new java.util.ArrayList<>(counts.keySet()));
        assertEquals(Integer.valueOf(2), counts.get("yt"));
    }

    // ---- Card text ------------------------------------------------------------------------------------------------

    @Test
    public void timeLeftInMinutesAndHours() {
        assertEquals("24 min left", ContinueWatching.timeLeft(36 * 60_000L, 60 * 60_000L));
        assertEquals("1 h 12 min left", ContinueWatching.timeLeft(18 * 60_000L, 90 * 60_000L));
        assertEquals("2 h left", ContinueWatching.timeLeft(1_000, 2 * 3_600_000L + 1_000));
        assertEquals("1 min left", ContinueWatching.timeLeft(59_000, 60_000));
        assertNull(ContinueWatching.timeLeft(0, 60_000));
        assertNull(ContinueWatching.timeLeft(5_000, 0));
    }

    @Test
    public void subtitles() {
        long min = 60_000;
        assertEquals("S2 E6 · 24 min left", ContinueWatching.subtitle(episode("2", "6", "Rain",
                ContinueWatching.TYPE_CONTINUE, 20 * min, 44 * min)));
        assertEquals("Ep 3 · Next episode", ContinueWatching.subtitle(episode(null, "3", null,
                ContinueWatching.TYPE_NEXT, 0, 30 * min)));
        assertEquals("Highlights · 8 min left", ContinueWatching.subtitle(episode(" ", null, "Highlights",
                ContinueWatching.TYPE_CONTINUE, 2 * min, 10 * min)));
        assertEquals("New episode", ContinueWatching.subtitle(episode("0", null, null, ContinueWatching.TYPE_NEW, 0, 0)));
        assertEquals("", ContinueWatching.subtitle(episode(null, null, null, ContinueWatching.TYPE_CONTINUE, 0, 0)));
    }

    @Test
    public void progressAndAction() {
        ContinueWatching.Item half = item("yt", 1, 0, 50, 100);
        assertEquals(0.5f, ContinueWatching.progress(half), 0.001f);
        assertEquals(1f, ContinueWatching.progress(item("yt", 1, 0, 150, 100)), 0.001f);
        assertEquals("Resume", ContinueWatching.action(half));
        assertEquals("Play", ContinueWatching.action(item("yt", 1, ContinueWatching.TYPE_NEXT, 0, 100)));
    }

    @Test
    public void landscapePosterThenThumbnailThenAnyPoster() {
        assertEquals("p", ContinueWatching.art("p", 0, "t"));
        assertEquals("p", ContinueWatching.art("p", 2, "t"));
        assertEquals("t", ContinueWatching.art("p", 4, "t"));   // 2:3 portrait poster
        assertEquals("p", ContinueWatching.art("p", -1, null));
        assertEquals("t", ContinueWatching.art(" ", 0, "t"));
        assertNull(ContinueWatching.art(null, 0, ""));
    }

    @Test
    public void modeCyclesAndParses() {
        assertEquals(ContinueWatching.Mode.ALL, ContinueWatching.Mode.STARTED.next());
        assertEquals(ContinueWatching.Mode.STARTED, ContinueWatching.Mode.parse("bogus"));
        assertEquals(ContinueWatching.Mode.ALL, ContinueWatching.Mode.parse("ALL"));
    }

    // ---- Recently opened ------------------------------------------------------------------------------------------

    @Test
    public void recentListKeepsEachAppOnceNewestFirst() {
        List<ContinueWatching.Opened> l = ContinueWatching.addRecent(Collections.emptyList(), "yt", 1, 3);
        l = ContinueWatching.addRecent(l, "prime", 2, 3);
        l = ContinueWatching.addRecent(l, "yt", 3, 3);
        l = ContinueWatching.addRecent(l, "zee5", 4, 3);
        l = ContinueWatching.addRecent(l, "hotstar", 5, 3);
        String saved = ContinueWatching.formatRecent(l);
        assertEquals("hotstar@5,zee5@4,yt@3", saved);
        List<ContinueWatching.Opened> back = ContinueWatching.parseRecent(saved + ",broken,x@notanumber,@5");
        assertEquals(3, back.size());
        assertEquals("yt", back.get(2).packageName);
        assertEquals(3, back.get(2).at);
        assertTrue(ContinueWatching.parseRecent(null).isEmpty());
    }

    @Test
    public void recentRowSkipsUninstalledAndTurnedOffApps() {
        List<ContinueWatching.Opened> l = ContinueWatching.parseRecent("netflix@9,zee5@8,yt@7,prime@6");
        List<ContinueWatching.Opened> row = ContinueWatching.recentRow(l, INSTALLED, Collections.singleton("zee5"), 1);
        assertEquals(1, row.size());
        assertEquals("yt", row.get(0).packageName);
    }

    @Test
    public void openedTodayYesterdayDaysAgoInTheTvsZone() {
        TimeZone ist = TimeZone.getTimeZone("Asia/Kolkata");
        long day = 86_400_000L;
        long midnightIst = 20_000 * day - (long) (5.5 * 3_600_000);   // 00:00 IST on a day
        long now = midnightIst + 10 * 3_600_000L;                      // 10:00 IST
        assertEquals("Opened today", ContinueWatching.opened(midnightIst + 60_000, now, ist));
        assertEquals("Opened yesterday", ContinueWatching.opened(midnightIst - 60_000, now, ist));
        assertEquals("Opened 3 days ago", ContinueWatching.opened(now - 3 * day, now, ist));
        assertEquals("Opened today", ContinueWatching.opened(now + 5_000, now, ist));   // clock moved back
    }

    @Test
    public void sampleSizeKeepsAtLeastCardSize() {
        assertEquals(1, Artwork.sampleSize(400, 225, 380, 214));
        assertEquals(4, Artwork.sampleSize(1920, 1080, 380, 214));
        assertEquals(2, Artwork.sampleSize(1280, 720, 380, 214));
    }
}
