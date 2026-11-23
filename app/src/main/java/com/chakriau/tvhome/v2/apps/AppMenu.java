package com.chakriau.tvhome.v2.apps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The per-app menu in Settings › Apps (Requirements.md section 8): move up, move down, move to another tab, App info.
 * Plain Java so the rules are unit-tested.
 */
public final class AppMenu {

    public enum Action {
        MOVE_UP("Move up"),
        MOVE_DOWN("Move down"),
        TO_APPS_BAR("Move to Apps bar"),
        TO_HOME("Move to Home"),
        HIDE("Hide from Home"),
        APP_INFO("App info");

        public final String label;

        Action(String label) {
            this.label = label;
        }

        /** The state a move-to action sends the app to, or null for other actions. */
        public AppStates.State target() {
            switch (this) {
                case TO_APPS_BAR:
                    return AppStates.State.APPS_BAR;
                case TO_HOME:
                    return AppStates.State.HOME;
                case HIDE:
                    return AppStates.State.HIDDEN;
                default:
                    return null;
            }
        }
    }

    /** One menu row; disabled rows are shown (with the reason) but do nothing. */
    public static final class Item {
        public final Action action;
        public final boolean enabled;
        /** Why a row is disabled, shown under it; null when enabled. */
        public final String note;

        Item(Action action, boolean enabled, String note) {
            this.action = action;
            this.enabled = enabled;
            this.note = note;
        }
    }

    private AppMenu() {
    }

    /**
     * The menu for the app at {@code index} of {@code count} apps in {@code state}.
     * Hidden apps have no order to change, so they get no move up / down.
     */
    public static List<Item> itemsFor(AppStates.State state, int index, int count, boolean appsBarFull) {
        List<Item> items = new ArrayList<>();
        if (state != AppStates.State.HIDDEN) {
            items.add(new Item(Action.MOVE_UP, index > 0, index > 0 ? null : "Already first"));
            items.add(new Item(Action.MOVE_DOWN, index < count - 1, index < count - 1 ? null : "Already last"));
        }
        if (state != AppStates.State.APPS_BAR) {
            items.add(new Item(Action.TO_APPS_BAR, !appsBarFull,
                    appsBarFull ? "The Apps bar is full (" + AppStates.APPS_BAR_MAX + ")" : null));
        }
        if (state != AppStates.State.HOME) items.add(new Item(Action.TO_HOME, true, null));
        if (state != AppStates.State.HIDDEN) items.add(new Item(Action.HIDE, true, null));
        items.add(new Item(Action.APP_INFO, true, null));
        return Collections.unmodifiableList(items);
    }

    /** The tab label: "Apps bar · 7 of 8", "Home · 10", "Hidden · 14". */
    public static String tabLabel(AppStates.State state, int count) {
        switch (state) {
            case APPS_BAR:
                return "Apps bar · " + count + " of " + AppStates.APPS_BAR_MAX;
            case HOME:
                return "Home · " + count;
            default:
                return "Hidden · " + count;
        }
    }

    /** The short state name on each row's chip. */
    public static String stateName(AppStates.State state) {
        switch (state) {
            case APPS_BAR:
                return "Apps bar";
            case HOME:
                return "Home";
            default:
                return "Hidden";
        }
    }
}
