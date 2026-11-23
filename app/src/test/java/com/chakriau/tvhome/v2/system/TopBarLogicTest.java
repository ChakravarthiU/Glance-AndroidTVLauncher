package com.chakriau.tvhome.v2.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TopBarLogicTest {

    // ---- Input names ----------------------------------------------------------------------------------------------

    @Test
    public void philipsNamesLoseTheirTypeAndGetAnIcon() {
        assertEquals("🎮 Xbox", InputLabels.tidy("Xbox&GAME_CONSOLE"));
        assertEquals("💻 Ordinateur", InputLabels.tidy("Ordinateur&COMPUTER"));
        assertEquals("Fire Stick", InputLabels.tidy("Fire Stick&STREAMER"));   // unknown type: no icon
        assertEquals("🎮", InputLabels.tidy("&GAME_CONSOLE"));
    }

    @Test
    public void ordinaryNamesStayAndEmptyOnesAreNone() {
        assertEquals("Tom & Jerry", InputLabels.tidy("Tom & Jerry"));
        assertEquals("Apple TV", InputLabels.tidy("  Apple TV "));
        assertNull(InputLabels.tidy("null"));
        assertNull(InputLabels.tidy("  "));
        assertNull(InputLabels.tidy(null));
    }

    @Test
    public void hdmiPortsSayHdmi() {
        assertEquals("HDMI 1", InputLabels.port("1", "x/HW5", true));
        assertEquals("HDMI 2/ARC", InputLabels.port("HDMI 2/ARC", "x", true));
        assertEquals("AV", InputLabels.port("AV", "x", false));
        assertEquals("HDMI x/HW5", InputLabels.port(null, "x/HW5", true));
    }

    @Test
    public void rowPutsTheNameBeforeThePort() {
        assertEquals("Apple TV · HDMI 1", InputLabels.row("Apple TV", "HDMI 1"));
        assertEquals("HDMI 1", InputLabels.row(null, "HDMI 1"));
        assertEquals("HDMI 1", InputLabels.row("HDMI 1", "HDMI 1"));
        assertEquals("HDMI 1", InputLabels.row(" ", "HDMI 1"));
    }

    @Test
    public void everyPortConnectedMeansTheStateIsUseless() {
        assertTrue(InputLabels.hdmiStateBogus(4, 4));
        assertFalse(InputLabels.hdmiStateBogus(4, 2));
        assertFalse(InputLabels.hdmiStateBogus(1, 1));   // one port really in use
        assertFalse(InputLabels.hdmiStateBogus(0, 0));
    }

    @Test
    public void tunerPrefersHardwareThenDigital() {
        assertEquals("com.mtk/.TunerInputService/HW0", InputLabels.pickTuner(Arrays.asList(
                "com.airplay/.Input", "com.mtk/.AnalogInputService/HW1", "com.mtk/.TunerInputService/HW0")));
        assertEquals("a/.Dtv", InputLabels.pickTuner(Arrays.asList("b/.AnalogTv", "a/.Dtv")));
        assertEquals("a/HW2", InputLabels.pickTuner(Arrays.asList("b/HW3", "a/HW2")));
        assertNull(InputLabels.pickTuner(Collections.emptyList()));
    }

    // ---- Top bar --------------------------------------------------------------------------------------------------

    @Test
    public void wifiNamesAreCleanedOrUnknown() {
        assertEquals("Home-WiFi", TopBar.wifiName("\"Home-WiFi\""));
        assertEquals("Cafe \"Net\"", TopBar.wifiName("Cafe \"Net\""));
        assertNull(TopBar.wifiName("<unknown ssid>"));
        assertNull(TopBar.wifiName("0x"));
        assertNull(TopBar.wifiName("\"\""));
        assertNull(TopBar.wifiName(null));
    }

    @Test
    public void buttonStylesCycleAndSayWhatToDraw() {
        assertEquals(TopBar.Style.ICONS, TopBar.Style.BOTH.next());
        assertEquals(TopBar.Style.BOTH, TopBar.Style.TEXT.next());
        assertTrue(TopBar.Style.BOTH.icon() && TopBar.Style.BOTH.text());
        assertFalse(TopBar.Style.ICONS.text());
        assertFalse(TopBar.Style.TEXT.icon());
        assertEquals(TopBar.Style.BOTH, TopBar.Style.parse("junk"));
    }

    @Test
    public void settingsPressDefaultsToTheTvAndHoldIsTheOther() {
        assertEquals(TopBar.SettingsPress.TV, TopBar.SettingsPress.parse(null));
        assertEquals(TopBar.SettingsPress.LAUNCHER, TopBar.SettingsPress.TV.other());
        assertEquals(TopBar.SettingsPress.LAUNCHER, TopBar.SettingsPress.parse("LAUNCHER"));
    }
}
