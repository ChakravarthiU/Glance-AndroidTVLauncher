package com.chakriau.tvhome.v2.apps;

import static com.chakriau.tvhome.v2.apps.AppStates.State.APPS_BAR;
import static com.chakriau.tvhome.v2.apps.AppStates.State.HIDDEN;
import static com.chakriau.tvhome.v2.apps.AppStates.State.HOME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class AppStatesTest {

    private static AppStates withApps(String... pkgs) {
        AppStates s = new AppStates();
        s.reconcile(Arrays.asList(pkgs));
        return s;
    }

    @Test
    public void newAppsGoToTheEndOfHome() {
        AppStates s = withApps("a", "b");
        assertTrue(s.reconcile(Arrays.asList("a", "b", "c")));
        assertEquals(Arrays.asList("a", "b", "c"), s.visible(HOME));
        assertTrue(s.visible(APPS_BAR).isEmpty());
    }

    @Test
    public void reconcileWithNothingNewChangesNothing() {
        AppStates s = withApps("a", "b");
        assertFalse(s.reconcile(Arrays.asList("b", "a")));
        assertEquals(Arrays.asList("a", "b"), s.visible(HOME));
    }

    @Test
    public void emptyQueryNeverWipesTheLists() {
        AppStates s = withApps("a", "b");
        assertFalse(s.reconcile(Collections.emptyList()));
        assertTrue(s.reconcile(Arrays.asList("a", "b", "c")));
        assertEquals(Arrays.asList("a", "b", "c"), s.visible(HOME));
    }

    @Test
    public void uninstalledAppIsUnseenAndReturnsToItsPlace() {
        AppStates s = withApps("a", "b", "c");
        s.moveTo("b", APPS_BAR);
        s.reconcile(Arrays.asList("a", "c"));
        assertTrue(s.visible(APPS_BAR).isEmpty());
        assertEquals(APPS_BAR, s.stateOf("b"));
        s.reconcile(Arrays.asList("a", "b", "c"));
        assertEquals(Collections.singletonList("b"), s.visible(APPS_BAR));
    }

    @Test
    public void moveToAppendsAndAppsBarHoldsAtMostEight() {
        List<String> pkgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) pkgs.add("p" + i);
        AppStates s = new AppStates();
        s.reconcile(pkgs);
        for (int i = 0; i < 8; i++) assertTrue(s.moveTo("p" + i, APPS_BAR));
        assertTrue(s.appsBarFull());
        assertFalse(s.moveTo("p8", APPS_BAR));
        assertEquals(HOME, s.stateOf("p8"));
        assertTrue(s.moveTo("p0", HIDDEN));
        assertTrue(s.moveTo("p8", APPS_BAR));
        assertEquals("p8", s.visible(APPS_BAR).get(7));
    }

    @Test
    public void uninstalledAppsDoNotCountTowardTheAppsBarLimit() {
        List<String> pkgs = new ArrayList<>();
        for (int i = 0; i < 9; i++) pkgs.add("p" + i);
        AppStates s = new AppStates();
        s.reconcile(pkgs);
        for (int i = 0; i < 8; i++) s.moveTo("p" + i, APPS_BAR);
        pkgs.remove("p3");
        s.reconcile(pkgs);
        assertTrue(s.moveTo("p8", APPS_BAR));
    }

    @Test
    public void moveToRejectsUnknownSameStateAndUninstalled() {
        AppStates s = withApps("a", "b");
        assertFalse(s.moveTo("zzz", APPS_BAR));
        assertFalse(s.moveTo("a", HOME));
        s.reconcile(Collections.singletonList("b"));
        assertFalse(s.moveTo("a", HIDDEN));
    }

    @Test
    public void moveUpAndDownSwapNeighboursAndStopAtTheEnds() {
        AppStates s = withApps("a", "b", "c");
        assertTrue(s.moveUp("c"));
        assertEquals(Arrays.asList("a", "c", "b"), s.visible(HOME));
        assertTrue(s.moveDown("a"));
        assertEquals(Arrays.asList("c", "a", "b"), s.visible(HOME));
        assertFalse(s.moveUp("c"));
        assertFalse(s.moveDown("b"));
    }

    @Test
    public void movesSkipUninstalledApps() {
        AppStates s = withApps("a", "b", "c");
        s.reconcile(Arrays.asList("a", "c"));
        assertTrue(s.moveUp("c"));
        assertEquals(Arrays.asList("c", "a"), s.visible(HOME));
        s.reconcile(Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("c", "b", "a"), s.visible(HOME));
    }

    @Test
    public void serializeAndParseRoundTrip() {
        AppStates s = withApps("a", "b", "c", "d");
        s.moveTo("c", APPS_BAR);
        s.moveTo("d", HIDDEN);
        AppStates back = AppStates.parse(s.serialize());
        back.reconcile(Arrays.asList("a", "b", "c", "d"));
        assertEquals(Collections.singletonList("c"), back.visible(APPS_BAR));
        assertEquals(Arrays.asList("a", "b"), back.visible(HOME));
        assertEquals(Collections.singletonList("d"), back.visible(HIDDEN));
    }

    @Test
    public void parseIgnoresJunkAndDuplicates() {
        AppStates s = AppStates.parse("tvhome-app-states-1\nAPPS_BAR=a,a, b\nNOPE=x\nHOME=b,c\nbroken line\n");
        assertEquals(APPS_BAR, s.stateOf("a"));
        assertEquals(APPS_BAR, s.stateOf("b"));
        assertEquals(HOME, s.stateOf("c"));
        assertNull(s.stateOf("x"));
        assertNull(AppStates.parse("something else").stateOf("a"));
        assertNull(AppStates.parse(null).stateOf("a"));
    }
}
