package com.chakriau.tvhome.v2.system;

import java.util.List;
import java.util.Locale;

/**
 * How the Inputs list names and orders the TV's sources (Requirements.md 5.9), learned from V1 on many TVs. Plain
 * Java, unit-tested.
 */
public final class InputLabels {

    private InputLabels() {
    }

    /**
     * A name given to an input in the TV's own Sources menu, tidied: Philips saves "Xbox&amp;GAME_CONSOLE" (the type
     * picks its icon there) and an unnamed port as "null". Gives "🎮 Xbox", or null for no name.
     */
    public static String tidy(CharSequence raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty() || s.equals("null")) return null;
        int amp = s.lastIndexOf('&');
        // Only a type in Philips' form (three or more capitals, digits or underscores), so "Tom & Jerry" stays.
        if (amp < 0 || !s.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}")) return s;
        String name = s.substring(0, amp).trim();
        String emoji = emoji(s.substring(amp + 1));
        if (name.isEmpty()) return emoji;
        return emoji == null ? name : emoji + " " + name;
    }

    private static String emoji(String type) {
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("GAME")) return "🎮";                                          // 🎮
        if (t.contains("COMPUTER") || t.equals("PC")) return "💻";                    // 💻
        if (t.contains("BLU") || t.contains("DVD") || t.contains("DISC")) return "📀"; // 📀
        if (t.contains("BOX") || t.contains("STB") || t.contains("SAT") || t.contains("CABLE")) return "📡";
        if (t.contains("AUDIO") || t.contains("SOUND") || t.contains("AMP")) return "🔊"; // 🔊
        if (t.contains("CAM")) return "📷";                                           // 📷
        return null;
    }

    /** The port's own label: an HDMI input whose label doesn't say so gets "HDMI " in front ("1" → "HDMI 1"). */
    public static String port(String label, String id, boolean hdmi) {
        String l = label == null || label.trim().isEmpty() ? id : label.trim();
        if (hdmi && !l.toUpperCase(Locale.ROOT).contains("HDMI")) l = "HDMI " + l;
        return l;
    }

    /**
     * The row's text: a name given to the input (or the CEC device on it) first, then the port, as
     * "Apple TV · HDMI 1"; just the port when there's no name or it says the same.
     */
    public static String row(String name, String port) {
        if (name == null || name.trim().isEmpty() || name.trim().equals(port)) return port;
        return name.trim() + " · " + port;
    }

    /**
     * Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in. With two or more
     * ports all "connected", the state is not shown at all.
     */
    public static boolean hdmiStateBogus(int ports, int connected) {
        return ports > 1 && connected == ports;
    }

    /**
     * The tuner for the Live TV row, from the TV's system tuner inputs: a hardware one (the framework names those
     * ".../HW&lt;n&gt;") before software ones, a digital one before an analog one (MediaTek's AnalogInputService opens a
     * dead ATV source), then the first by id. Null when there is none.
     */
    public static String pickTuner(List<String> ids) {
        String best = null;
        for (String id : ids) {
            if (best == null || tunerRank(id) < tunerRank(best)
                    || (tunerRank(id) == tunerRank(best) && id.compareTo(best) < 0)) {
                best = id;
            }
        }
        return best;
    }

    private static int tunerRank(String id) {
        boolean hw = id.matches(".*/HW\\d+$");
        boolean analog = id.toLowerCase(Locale.ROOT).contains("analog");
        return (hw ? 0 : 2) + (analog ? 1 : 0);
    }
}
