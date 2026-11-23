package com.chakriau.tvhome.v2.system;

/**
 * Choices for the top-right buttons (Requirements.md 5.9; global settings, Settings › System) and the Wi-Fi name
 * cleanup for the Network button. Plain Java, unit-tested.
 */
public final class TopBar {

    /** How all three buttons look. */
    public enum Style {
        BOTH("Icons and text"), ICONS("Icons only"), TEXT("Text only");

        public final String label;

        Style(String label) {
            this.label = label;
        }

        public boolean icon() {
            return this != TEXT;
        }

        public boolean text() {
            return this != ICONS;
        }

        public Style next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Style parse(String s) {
            for (Style v : values()) if (v.name().equals(s)) return v;
            return BOTH;
        }
    }

    /** What a press on the Settings button opens; a long press opens the other one. */
    public enum SettingsPress {
        TV("The TV's settings", "Hold OK: the launcher's settings"),
        LAUNCHER("The launcher's settings", "Hold OK: the TV's settings");

        public final String label;
        public final String holdHint;

        SettingsPress(String label, String holdHint) {
            this.label = label;
            this.holdHint = holdHint;
        }

        public SettingsPress other() {
            return this == TV ? LAUNCHER : TV;
        }

        public static SettingsPress parse(String s) {
            return LAUNCHER.name().equals(s) ? LAUNCHER : TV;   // default: press = TV (Requirements.md 3)
        }
    }

    private TopBar() {
    }

    /**
     * The Wi-Fi name as Android reports it, cleaned: the quotes taken off; null when it isn't known (no location
     * permission gives "&lt;unknown ssid&gt;", some TVs give "0x" or nothing).
     */
    public static String wifiName(String ssid) {
        if (ssid == null) return null;
        String s = ssid.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty() || s.equalsIgnoreCase("<unknown ssid>") || s.equals("0x") || s.equals("null")) return null;
        return s;
    }
}
