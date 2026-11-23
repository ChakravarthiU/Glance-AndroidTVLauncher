package com.chakriau.tvhome.v2.phone;

import android.content.Context;

import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.eyes.EyeSize;
import com.chakriau.tvhome.v2.eyes.EyesStore;
import com.chakriau.tvhome.v2.eyes.Personality;
import com.chakriau.tvhome.v2.festive.Festivals;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.ThemeStore;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.system.NetworkStatus;
import com.chakriau.tvhome.v2.system.TopBar;
import com.chakriau.tvhome.v2.system.TopBarStore;
import com.chakriau.tvhome.v2.wallpapers.Rotation;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;
import com.chakriau.tvhome.v2.watching.ContinueWatching;
import com.chakriau.tvhome.v2.watching.WatchNext;
import com.chakriau.tvhome.v2.watching.WatchingRow;
import com.chakriau.tvhome.v2.watching.WatchingStore;
import com.chakriau.tvhome.v2.weather.City;
import com.chakriau.tvhome.v2.weather.TimeFormats;
import com.chakriau.tvhome.v2.weather.Weather;
import com.chakriau.tvhome.v2.weather.WeatherCode;
import com.chakriau.tvhome.v2.weather.WeatherSource;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every setting the phone page shows (Settings › Phone, Requirements.md 8), in the TV's groups (the active theme,
 * then your content): on/off settings, settings with several choices, and read-only lines. The same stores as the TV's Settings, so a change made on either
 * shows on the other. Built fresh for each request (off the main thread).
 */
final class PhoneSettings {

    enum Type { TOGGLE, CHOICE, INFO }

    /** Applies a value from the phone; returns what's wrong, or null when done. */
    interface Setter {
        String apply(Object value);
    }

    static final class Control {
        final String key, label, note;
        final Type type;
        /** TOGGLE: Boolean; CHOICE: Integer index; INFO: String. */
        final Object value;
        final String[] options;
        final Setter setter;

        Control(String key, String label, String note, Type type, Object value, String[] options, Setter setter) {
            this.key = key;
            this.label = label;
            this.note = note;
            this.type = type;
            this.value = value;
            this.options = options;
            this.setter = setter;
        }
    }

    static final class Section {
        /** The group this section starts on the phone page (the theme's name, or "Your content"); null continues. */
        final String id, title, group;
        final List<Control> controls = new ArrayList<>();

        Section(String id, String title) {
            this(id, title, null);
        }

        Section(String id, String title, String group) {
            this.id = id;
            this.title = title;
            this.group = group;
        }

        void toggle(String key, String label, String note, boolean value, BoolSet set) {
            controls.add(new Control(key, label, note, Type.TOGGLE, value, null, v -> {
                if (!(v instanceof Boolean)) return "Choose on or off.";
                set.set((Boolean) v);
                return null;
            }));
        }

        void choice(String key, String label, String note, String[] options, int index, IntSet set) {
            controls.add(new Control(key, label, note, Type.CHOICE, index, options, v -> {
                if (!(v instanceof Number)) return "Choose one of the options.";
                int i = ((Number) v).intValue();
                if (i < 0 || i >= options.length) return "Choose one of the options.";
                return set.set(i);
            }));
        }

        void info(String label, String text) {
            controls.add(new Control("", label, null, Type.INFO, text, null, null));
        }
    }

    interface BoolSet {
        void set(boolean on);
    }

    /** Returns what's wrong, or null. */
    interface IntSet {
        String set(int index);
    }

    private PhoneSettings() {
    }

    static List<Section> build(Context c) {
        List<Section> out = new ArrayList<>();
        ThemeParts parts = ThemeParts.active(c);
        Themes.Theme theme = parts.theme();

        // ---- The active theme ----
        Section th = new Section("theme", "Theme", theme.name);
        String[] themeNames = new String[Themes.ALL.size()];
        for (int i = 0; i < themeNames.length; i++) themeNames[i] = Themes.ALL.get(i).name;
        th.choice("theme.active", "Theme", theme.description, themeNames, Themes.indexOf(theme), i -> {
            ThemeStore.setActive(c, Themes.ALL.get(i));
            return null;
        });
        for (Themes.Part p : Themes.Part.values()) {
            if (!p.optional || !theme.has(p)) continue;
            if (p == Themes.Part.FESTIVE_LIGHTS) {
                Festivals.LightsMode[] modes = Festivals.LightsMode.values();
                String[] modeNames = new String[modes.length];
                for (int i = 0; i < modes.length; i++) modeNames[i] = modes[i].label;
                th.choice("lights.mode", p.label, p.note, modeNames, parts.lights().mode().ordinal(), i -> {
                    parts.lights().setMode(modes[i]);
                    return null;
                });
            } else {
                th.toggle("part." + p.name(), p.label, p.note, parts.on(p), on -> parts.setOn(p, on));
            }
        }
        if (theme.has(Themes.Part.TOP_BAR)) {
            TopBar.Style[] styles = TopBar.Style.values();
            String[] styleNames = new String[styles.length];
            for (int i = 0; i < styles.length; i++) styleNames[i] = styles[i].label;
            th.choice("top.style", "Top-button style", "For Inputs, Network and Settings", styleNames,
                    parts.topButtonStyle().ordinal(), i -> {
                        parts.setTopButtonStyle(styles[i]);
                        return null;
                    });
        }
        out.add(th);

        if (theme.has(Themes.Part.EYES)) {
            EyesStore eyes = new EyesStore(c, theme.id);
            Section e = new Section("eyes", "Eyes");
            Personality[] ps = Personality.values();
            String[] names = new String[ps.length];
            for (int i = 0; i < ps.length; i++) names[i] = ps[i].label;
            e.choice("eyes.personality", "Personality", eyes.personality().note, names, eyes.personality().ordinal(),
                    i -> {
                        eyes.setPersonality(ps[i]);
                        return null;
                    });
            EyeSize[] sizes = EyeSize.values();
            String[] sizeNames = new String[sizes.length];
            for (int i = 0; i < sizes.length; i++) sizeNames[i] = sizes[i].label;
            e.choice("eyes.size", "Size", "How big the eyes are on Home", sizeNames, eyes.size().ordinal(), i -> {
                eyes.setSize(sizes[i]);
                return null;
            });
            out.add(e);
        }

        // ---- Your content ----
        WallpaperStore wp = new WallpaperStore(c);
        Section w = new Section("wallpapers", "Wallpapers", "Your content");
        w.toggle("wallpaper.enabled", "Wallpaper", "Off shows the built-in picture instead", wp.enabled(),
                wp::setEnabled);
        String[] intervals = new String[Rotation.INTERVALS.length];
        for (int i = 0; i < intervals.length; i++) {
            int m = Rotation.INTERVALS[i];
            intervals[i] = m == 60 ? "1 hour" : m + " minutes";
        }
        w.choice("wallpaper.interval", "Change every", "When two or more photos are ticked", intervals,
                indexOf(Rotation.INTERVALS, wp.intervalMinutes()), i -> {
                    wp.setIntervalMinutes(Rotation.INTERVALS[i]);
                    return null;
                });
        w.choice("wallpaper.order", "Order", "In turn, or shuffled", new String[]{"In turn", "Shuffle"},
                wp.shuffle() ? 1 : 0, i -> {
                    wp.setShuffle(i == 1);
                    return null;
                });
        w.toggle("wallpaper.fade", "Cross-fade", "Fade softly from one photo to the next", wp.crossFade(),
                wp::setCrossFade);
        out.add(w);

        WatchingStore cw = new WatchingStore(c);
        Section cs = new Section("watching", "Continue watching");
        ContinueWatching.Mode[] cwModes = ContinueWatching.Mode.values();
        String[] cwNames = new String[cwModes.length];
        for (int i = 0; i < cwModes.length; i++) cwNames[i] = cwModes[i].label;
        cs.choice("cw.mode", "Show", cw.mode().note, cwNames, cw.mode().ordinal(), i -> {
            cw.setMode(cwModes[i]);
            return null;
        });
        cs.choice("cw.perApp", "Items from each app", null, numbers(ContinueWatching.PER_APP_CHOICES),
                indexOf(ContinueWatching.PER_APP_CHOICES, cw.perApp()), i -> {
                    cw.setPerApp(ContinueWatching.PER_APP_CHOICES[i]);
                    return null;
                });
        cs.choice("cw.total", "Items in the row", null, numbers(ContinueWatching.TOTAL_CHOICES),
                indexOf(ContinueWatching.TOTAL_CHOICES, cw.total()), i -> {
                    cw.setTotal(ContinueWatching.TOTAL_CHOICES[i]);
                    return null;
                });
        cs.info("Read Play Next", WatchNext.allowed(c) ? "Allowed"
                : "Not allowed. Allow it on the TV: Settings › Continue watching › Read Play Next");
        // Apps with something in Play Next (and any turned off), each shown or not.
        Map<String, AppInfo> installed = WatchingRow.installedApps(AppsModel.get(c).current());
        WatchNext.Result wn = WatchNext.read(c);
        Set<String> blocked = cw.blocked();
        List<String> pkgs = new ArrayList<>();
        if (wn.status == WatchNext.Status.READABLE) {
            pkgs.addAll(ContinueWatching.countByApp(wn.items, installed.keySet(), c.getPackageName()).keySet());
        }
        for (String b : blocked) if (installed.containsKey(b) && !pkgs.contains(b)) pkgs.add(b);
        for (String pkg : pkgs) {
            AppInfo app = installed.get(pkg);
            if (app == null) continue;
            cs.toggle("cw.app." + pkg, app.label, "May appear in the row", !blocked.contains(pkg),
                    on -> cw.setBlocked(pkg, !on));
        }
        out.add(cs);

        Section f = new Section("special", "Special days");
        f.info("Banner, lights and wallpaper for each day", "In the Special days tab above");
        out.add(f);

        WeatherStore ws = new WeatherStore(c);
        Section t = new Section("time", "Time, date & weather");
        t.toggle("weather.enabled", "Weather", "Temperature and sky in the top bar", ws.enabled(), ws::setEnabled);
        t.choice("weather.location", "Location", "Automatic: the TV's location, else its internet address",
                new String[]{"Automatic", "Set by hand"}, ws.automatic() ? 0 : 1, i -> {
                    if (i == 1 && ws.byHand() == null) return "Search for a city below first.";
                    ws.setAutomatic(i == 0);
                    ws.clearReading();   // fetched again for the new place next time Home shows
                    return null;
                });
        WeatherSource[] sources = WeatherSource.values();
        String[] sourceNames = new String[sources.length];
        for (int i = 0; i < sources.length; i++) sourceNames[i] = sources[i].label;
        t.choice("weather.provider", "Weather from", ws.provider().note, sourceNames, ws.provider().ordinal(), i -> {
            if (sources[i] == WeatherSource.OPEN_WEATHER_MAP && ws.owmKey() == null) {
                return "Add your OpenWeatherMap key below first.";
            }
            ws.setProvider(sources[i]);
            return null;
        });
        t.info("OpenWeatherMap key", ws.owmKey() == null ? "Not added" : "Added (ends "
                + WeatherSource.masked(ws.owmKey()) + ")");
        City hand = ws.byHand();
        t.info("City (set by hand)", hand == null ? "None yet" : hand.name + (hand.detail.isEmpty() ? ""
                : ", " + hand.detail));
        WeatherStore.Reading r = ws.reading();
        String problem = ws.providerProblem();
        t.info("Now showing", r == null ? "Nothing yet" : WeatherCode.degrees(r.celsius, ws.fahrenheit()) + " "
                + WeatherCode.kind(r.code).label + ", " + r.city + " (from " + ws.readingProvider().label + ")"
                + (problem == null || ws.provider() != WeatherSource.OPEN_WEATHER_MAP ? "" : ". " + problem));
        t.choice("weather.units", "Units", null, new String[]{"°C", "°F"}, ws.fahrenheit() ? 1 : 0, i -> {
            ws.setFahrenheit(i == 1);
            return null;
        });
        TimeFormats.Face[] faces = TimeFormats.Face.values();
        String[] faceNames = new String[faces.length];
        for (int i = 0; i < faces.length; i++) faceNames[i] = faces[i].label;
        t.choice("weather.face", "Clock face", "Digital numbers, or an analog dial", faceNames,
                ws.clockFace().ordinal(), i -> {
                    ws.setClockFace(faces[i]);
                    return null;
                });
        t.toggle("weather.roll", "Rolling digits", "Digits roll up when the time changes (digital face)",
                ws.clockRoll(), ws::setClockRoll);
        TimeFormats.Clock[] clocks = TimeFormats.Clock.values();
        String[] clockNames = new String[clocks.length];
        for (int i = 0; i < clocks.length; i++) clockNames[i] = clocks[i].label;
        t.choice("weather.clock", "Clock", "12 or 24 hours (digital face)", clockNames, ws.clock().ordinal(), i -> {
            ws.setClock(clocks[i]);
            return null;
        });
        TimeFormats.DateStyle[] dates = TimeFormats.DateStyle.values();
        String[] dateNames = new String[dates.length];
        for (int i = 0; i < dates.length; i++) {
            dateNames[i] = new SimpleDateFormat(dates[i].pattern, Locale.getDefault()).format(new Date());
        }
        t.choice("weather.date", "Date format", "Never the year", dateNames, ws.dateStyle().ordinal(), i -> {
            ws.setDateStyle(dates[i]);
            return null;
        });
        t.info("The TV's location", Weather.locationAllowed(c) ? "Allowed" : "Not allowed (allow it on the TV)");
        out.add(t);

        TopBarStore bar = new TopBarStore(c);
        Section s = new Section("system", "System");
        TopBar.SettingsPress[] presses = TopBar.SettingsPress.values();
        String[] pressNames = new String[presses.length];
        for (int i = 0; i < presses.length; i++) pressNames[i] = "Press: " + presses[i].label;
        s.choice("top.press", "Settings button", bar.settingsPress().holdHint, pressNames,
                bar.settingsPress().ordinal(), i -> {
                    bar.setSettingsPress(presses[i]);
                    return null;
                });
        s.info("Wi-Fi name on the Network button", NetworkStatus.nameAllowed(c) ? "Allowed"
                : "Not allowed (allow it on the TV: Settings › System)");
        out.add(s);
        return out;
    }

    /** Applies one change from the phone; returns what's wrong, or null. */
    static String set(Context c, String key, Object value) {
        for (Section s : build(c)) {
            for (Control k : s.controls) {
                if (k.setter != null && k.key.equals(key)) return k.setter.apply(value);
            }
        }
        return "That setting isn't on this TV.";
    }

    private static String[] numbers(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = String.valueOf(values[i]);
        return out;
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }
}
