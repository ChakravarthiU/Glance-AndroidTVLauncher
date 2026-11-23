package com.chakriau.tvhome.v2.apps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where every app lives: the Apps bar, the Home grid or Hidden, each in the owner's order (Requirements.md 5.5, 5.6).
 * Apps are keyed by package name. Plain Java, so the rules are unit-tested on the JVM.
 *
 * <p>Rules:
 * <ul>
 *   <li>A newly installed app goes to Home, at the end of the Home order.</li>
 *   <li>An app that is uninstalled keeps its place, unseen; reinstalled, it comes back where it was. This also keeps
 *       an app's place while it is briefly missing during an update.</li>
 *   <li>The Apps bar holds at most {@link #APPS_BAR_MAX} installed apps.</li>
 *   <li>Lists only ever change through {@link #reconcile} and the move methods, i.e. through Settings.</li>
 * </ul>
 */
public final class AppStates {

    public enum State { APPS_BAR, HOME, HIDDEN }

    public static final int APPS_BAR_MAX = 8;
    private static final String FORMAT = "tvhome-app-states-1";

    private final Map<State, List<String>> lists = new EnumMap<>(State.class);
    private final Set<String> installed = new HashSet<>();

    public AppStates() {
        for (State s : State.values()) lists.put(s, new ArrayList<>());
    }

    /**
     * Brings the lists up to date with the apps installed now: new ones are added to the end of Home.
     * An empty set is ignored (a failed query must never look like "everything was uninstalled").
     *
     * @return whether the saved lists changed
     */
    public boolean reconcile(Collection<String> installedNow) {
        installed.clear();
        installed.addAll(installedNow);
        if (installedNow.isEmpty()) return false;
        boolean changed = false;
        for (String pkg : installedNow) {
            if (stateOf(pkg) == null) {
                lists.get(State.HOME).add(pkg);
                changed = true;
            }
        }
        return changed;
    }

    /** The installed apps in a state, in order. */
    public List<String> visible(State state) {
        List<String> out = new ArrayList<>();
        for (String pkg : lists.get(state)) if (installed.contains(pkg)) out.add(pkg);
        return Collections.unmodifiableList(out);
    }

    /** The state an app is in, or null if it has never been seen. */
    public State stateOf(String pkg) {
        for (State s : State.values()) if (lists.get(s).contains(pkg)) return s;
        return null;
    }

    public boolean appsBarFull() {
        return visible(State.APPS_BAR).size() >= APPS_BAR_MAX;
    }

    /**
     * Moves an installed app to the end of another state.
     *
     * @return false if the app is unknown, already there, or the Apps bar is full
     */
    public boolean moveTo(String pkg, State target) {
        State from = stateOf(pkg);
        if (from == null || from == target || !installed.contains(pkg)) return false;
        if (target == State.APPS_BAR && appsBarFull()) return false;
        lists.get(from).remove(pkg);
        lists.get(target).add(pkg);
        return true;
    }

    /** Swaps an app with the installed app before it in its list. */
    public boolean moveUp(String pkg) {
        return shift(pkg, -1);
    }

    /** Swaps an app with the installed app after it in its list. */
    public boolean moveDown(String pkg) {
        return shift(pkg, 1);
    }

    private boolean shift(String pkg, int direction) {
        State s = stateOf(pkg);
        if (s == null || !installed.contains(pkg)) return false;
        List<String> list = lists.get(s);
        int i = list.indexOf(pkg);
        // Skip uninstalled apps: the owner only sees, and moves past, installed ones.
        for (int j = i + direction; j >= 0 && j < list.size(); j += direction) {
            if (installed.contains(list.get(j))) {
                Collections.swap(list, i, j);
                return true;
            }
        }
        return false;
    }

    // ---- Saving ----------------------------------------------------------------------------------------------

    /** One line per state: {@code APPS_BAR=pkg1,pkg2}. Package names never contain commas or line breaks. */
    public String serialize() {
        StringBuilder s = new StringBuilder(FORMAT).append('\n');
        for (State state : State.values()) {
            s.append(state.name()).append('=').append(String.join(",", lists.get(state))).append('\n');
        }
        return s.toString();
    }

    /** Reads {@link #serialize()} output. Unreadable input gives empty lists; duplicates keep the first place. */
    public static AppStates parse(String text) {
        AppStates states = new AppStates();
        if (text == null || !text.startsWith(FORMAT)) return states;
        Set<String> seen = new HashSet<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            State state;
            try {
                state = State.valueOf(line.substring(0, eq));
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (String pkg : line.substring(eq + 1).split(",")) {
                pkg = pkg.trim();
                if (!pkg.isEmpty() && seen.add(pkg)) states.lists.get(state).add(pkg);
            }
        }
        return states;
    }
}
