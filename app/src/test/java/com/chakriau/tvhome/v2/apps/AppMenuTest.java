package com.chakriau.tvhome.v2.apps;

import static com.chakriau.tvhome.v2.apps.AppMenu.Action.APP_INFO;
import static com.chakriau.tvhome.v2.apps.AppMenu.Action.HIDE;
import static com.chakriau.tvhome.v2.apps.AppMenu.Action.MOVE_DOWN;
import static com.chakriau.tvhome.v2.apps.AppMenu.Action.MOVE_UP;
import static com.chakriau.tvhome.v2.apps.AppMenu.Action.TO_APPS_BAR;
import static com.chakriau.tvhome.v2.apps.AppMenu.Action.TO_HOME;
import static com.chakriau.tvhome.v2.apps.AppStates.State.APPS_BAR;
import static com.chakriau.tvhome.v2.apps.AppStates.State.HIDDEN;
import static com.chakriau.tvhome.v2.apps.AppStates.State.HOME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AppMenuTest {

    private static List<AppMenu.Action> actions(List<AppMenu.Item> items) {
        List<AppMenu.Action> out = new ArrayList<>();
        for (AppMenu.Item i : items) out.add(i.action);
        return out;
    }

    private static AppMenu.Item find(List<AppMenu.Item> items, AppMenu.Action a) {
        for (AppMenu.Item i : items) if (i.action == a) return i;
        return null;
    }

    @Test
    public void appsBarAppCanMoveWithinTheBarToHomeOrHidden() {
        List<AppMenu.Item> items = AppMenu.itemsFor(APPS_BAR, 1, 7, false);
        assertEquals(Arrays.asList(MOVE_UP, MOVE_DOWN, TO_HOME, HIDE, APP_INFO), actions(items));
        for (AppMenu.Item i : items) assertTrue(i.enabled);
    }

    @Test
    public void homeAppCanGoToTheBarUnlessItIsFull() {
        assertEquals(Arrays.asList(MOVE_UP, MOVE_DOWN, TO_APPS_BAR, HIDE, APP_INFO),
                actions(AppMenu.itemsFor(HOME, 3, 10, false)));
        AppMenu.Item toBar = find(AppMenu.itemsFor(HOME, 3, 10, true), TO_APPS_BAR);
        assertNotNull(toBar);
        assertFalse(toBar.enabled);
        assertTrue(toBar.note.contains("full"));
    }

    @Test
    public void hiddenAppsHaveNoOrderToChange() {
        assertEquals(Arrays.asList(TO_APPS_BAR, TO_HOME, APP_INFO), actions(AppMenu.itemsFor(HIDDEN, 0, 5, false)));
    }

    @Test
    public void firstAndLastCannotMovePastTheEnds() {
        List<AppMenu.Item> first = AppMenu.itemsFor(HOME, 0, 3, false);
        assertFalse(find(first, MOVE_UP).enabled);
        assertTrue(find(first, MOVE_DOWN).enabled);
        List<AppMenu.Item> last = AppMenu.itemsFor(HOME, 2, 3, false);
        assertTrue(find(last, MOVE_UP).enabled);
        assertFalse(find(last, MOVE_DOWN).enabled);
        List<AppMenu.Item> only = AppMenu.itemsFor(APPS_BAR, 0, 1, false);
        assertFalse(find(only, MOVE_UP).enabled);
        assertFalse(find(only, MOVE_DOWN).enabled);
    }

    @Test
    public void moveActionsNameTheirTarget() {
        assertEquals(APPS_BAR, TO_APPS_BAR.target());
        assertEquals(HOME, TO_HOME.target());
        assertEquals(HIDDEN, HIDE.target());
        assertNull(MOVE_UP.target());
        assertNull(APP_INFO.target());
    }

    @Test
    public void tabLabels() {
        assertEquals("Apps bar · 7 of 8", AppMenu.tabLabel(APPS_BAR, 7));
        assertEquals("Home · 10", AppMenu.tabLabel(HOME, 10));
        assertEquals("Hidden · 14", AppMenu.tabLabel(HIDDEN, 14));
    }
}
