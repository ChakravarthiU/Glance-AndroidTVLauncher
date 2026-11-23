package com.chakriau.tvhome.v2.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ThemesTest {

    // ---- What a theme declares (Requirements.md 7.1) ---------------------------------------------------------------

    @Test
    public void themeOneDrawsEveryPart() {
        for (Themes.Part p : Themes.Part.values()) assertTrue(p.name(), Themes.THEME_1.has(p));
        assertEquals(8, Themes.THEME_1.appsBarMax);
    }

    @Test
    public void onlyTheFiveOptionalPartsHaveAnOnOff() {
        int optional = 0;
        for (Themes.Part p : Themes.Part.values()) if (p.optional) optional++;
        assertEquals(5, optional);
        assertTrue(Themes.Part.CONTINUE_WATCHING.optional);
        assertTrue(Themes.Part.BANNER.optional);
        assertTrue(Themes.Part.FESTIVE_LIGHTS.optional);
        assertTrue(Themes.Part.EYES.optional);
        assertTrue(Themes.Part.SKY.optional);
        assertFalse(Themes.Part.APPS_BAR.optional);
        assertFalse(Themes.Part.HOME_APPS.optional);
    }

    @Test
    public void anUnknownOrMissingThemeFallsBackToThemeOne() {
        assertSame(Themes.THEME_1, Themes.byId("theme1"));
        assertSame(Themes.THEME_1, Themes.byId("removed-theme"));
        assertSame(Themes.THEME_1, Themes.byId(null));
        assertEquals(0, Themes.indexOf(Themes.THEME_1));
    }

    @Test
    public void themeIdsAreUniqueAndAppsBarsFitTheSharedLimit() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Themes.Theme t : Themes.ALL) {
            assertTrue(t.id, ids.add(t.id));
            assertTrue(t.id, t.appsBarMax >= 1 && t.appsBarMax <= com.chakriau.tvhome.v2.apps.AppStates.APPS_BAR_MAX);
        }
    }

    @Test
    public void themeSettingsAreKeptPerTheme() {
        assertEquals("parts_theme1", ThemeMigration.partsFile("theme1"));
        assertEquals("on.EYES", ThemeMigration.onKey(Themes.Part.EYES));
    }

    // ---- Moving older settings to their step-12 places ------------------------------------------------------------

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void oldOnOffAndButtonStyleMoveToThemeOne() {
        Map<String, Object> out = ThemeMigration.partsFromOld(
                map("enabled", false, "personality", "SHY"),   // eyes_theme1
                map("enabled", true, "festival_greeting", false),   // banner_theme1
                map("enabled", false, "mode", "STARTED"),   // continue_watching
                map("style", "TEXT", "settings_press", "LAUNCHER"),   // top_bar
                Collections.emptyMap());
        assertEquals(false, out.get("on.EYES"));
        assertEquals(true, out.get("on.BANNER"));
        assertEquals(false, out.get("on.CONTINUE_WATCHING"));
        assertEquals("TEXT", out.get(ThemeMigration.TOP_BUTTON_STYLE));
        assertEquals(4, out.size());   // personality, mode and the Settings press stay where they are
    }

    @Test
    public void valuesNeverSetStayUnsetSoDefaultsApply() {
        Map<String, Object> out = ThemeMigration.partsFromOld(map("personality", "SHY"), Collections.emptyMap(),
                null, map(), Collections.emptyMap());
        assertTrue(out.isEmpty());
    }

    @Test
    public void aValueAlreadyInItsNewPlaceWins() {
        Map<String, Object> out = ThemeMigration.partsFromOld(map("enabled", false), map(), map(),
                map("style", "TEXT"), map("on.EYES", true));
        assertNull(out.get("on.EYES"));
        assertEquals("TEXT", out.get(ThemeMigration.TOP_BUTTON_STYLE));
    }

    @Test
    public void wrongTypesAreIgnored() {
        Map<String, Object> out = ThemeMigration.partsFromOld(map("enabled", "yes"), map(), map(), map("style", 3),
                map());
        assertTrue(out.isEmpty());
    }

    @Test
    public void festivalGreetingsBecomeGlobal() {
        assertEquals(Boolean.FALSE, ThemeMigration.greetingFromOld(map("festival_greeting", false), map()));
        assertNull(ThemeMigration.greetingFromOld(map(), map()));
        // Already set in the global store: left alone.
        assertNull(ThemeMigration.greetingFromOld(map("festival_greeting", false), map("festival_greeting", true)));
    }
}
