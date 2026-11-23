package com.chakriau.tvhome.v2.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.apps.AppStates;
import com.chakriau.tvhome.v2.apps.AppsModel;
import com.chakriau.tvhome.v2.backup.Backup;
import com.chakriau.tvhome.v2.festive.Banners;
import com.chakriau.tvhome.v2.festive.DecorTest;
import com.chakriau.tvhome.v2.festive.DecorTestStore;
import com.chakriau.tvhome.v2.festive.Festivals;
import com.chakriau.tvhome.v2.festive.SpecialDayStore;
import com.chakriau.tvhome.v2.festive.SpecialDays;
import com.chakriau.tvhome.v2.home.ThemeParts;
import com.chakriau.tvhome.v2.home.Themes;
import com.chakriau.tvhome.v2.safe.CrashLog;
import com.chakriau.tvhome.v2.sky.SkyMath;
import com.chakriau.tvhome.v2.sky.SkyState;
import com.chakriau.tvhome.v2.sky.SkyStore;
import com.chakriau.tvhome.v2.wallpapers.Photos;
import com.chakriau.tvhome.v2.wallpapers.UploadServer;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;
import com.chakriau.tvhome.v2.weather.City;
import com.chakriau.tvhome.v2.weather.Weather;
import com.chakriau.tvhome.v2.weather.WeatherClient;
import com.chakriau.tvhome.v2.weather.WeatherCode;
import com.chakriau.tvhome.v2.weather.WeatherSource;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Everything the phone page does besides photos (Settings › Phone; Requirements.md 5.4, 6.5, 8):
 *
 * <pre>
 * GET  /api/settings          every setting, by section (PhoneSettings)
 * POST /api/setting           {key, value}
 * GET  /api/special           {days, designs, suggestions}: the special days, banner designs, festival suggestions
 * POST /api/special           {id?, name, from, to, yearly, design, line1, line2, lights, photos: [ids]}: adds or changes one
 * DELETE /api/special/{id}    removes one
 * POST /api/special-preview   {design, line1, line2, lights?}: the TV shows Home with that banner (a 15-min test)
 * GET  /api/apps              the Apps bar, Home and Hidden lists, in order
 * POST /api/app               {pkg, action: bar | home | hidden | up | down}
 * GET  /api/city?q=name       city search (Open-Meteo)
 * POST /api/city              {name, latitude, longitude, detail}: weather for this city, set by hand
 * GET  /api/sky               the sky now (sun or moon, weather and why, sunrise and sunset), banner and lights
 *                             today, the test running, the kinds and designs
 * POST /api/home-test         {kind, minute, banner: real | none | design id, line1, line2, lights: real | on | off,
 *                             show}: tests the sky, banner and lights on Home for 15 min; show opens the Home preview
 * DELETE /api/home-test       ends the whole test: Home shows the real sky, banner and lights again
 * POST /api/show-home         opens the Home preview on the TV (Requirements.md 5.14)
 * POST /api/sky-test          {kind: "" | KIND, minute: -1 | 0..1439}: the sky part of the test only (older pages)
 * DELETE /api/sky-test        back to the real sky
 * POST /api/weather-key       {key}: saves the OpenWeatherMap key, tests it, and makes OpenWeatherMap the source
 * DELETE /api/weather-key     forgets the key; the weather comes from Open-Meteo again
 * POST /api/tick              {ids: [..], ticked: true|false}: ticks or unticks photos for Home (all: ids ["*"])
 * POST /api/show-now          {id}: ticks the photo and makes it the one Home shows now
 * GET  /api/backup-info       how many photos a backup with photos holds, and their size
 * GET  /api/problem           safe mode on or off, and the last problem recorded (Requirements.md 5.12)
 * POST /api/problem           {action: leave | clear}: try normal Home again, or forget the last problem
 * </pre>
 * Backup, restore and the last problem are served by {@code UploadServer} itself ({@code /api/backup},
 * {@code /api/restore}, {@code /api/problem}), since they stream files.
 * Runs on the phone server's thread; the TV is told on the main thread.
 */
public final class PhoneApi implements UploadServer.Api {

    /** What the TV's Settings does when the phone changes something. Called on the main thread. */
    public interface Tv {
        /** Something changed: show the current values again. */
        void changed();

        /** Show Home exactly as it looks, with any test, over Settings (or show the change in the open preview). */
        void showHome();

        /** A test started, changed or stopped: an open Home preview shows it. */
        void testChanged();
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final Context context;
    private final Tv tv;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    public PhoneApi(Context context, Tv tv) {
        this.context = context.getApplicationContext();
        this.tv = tv;
    }

    @Override
    public UploadServer.Reply handle(String method, String path, String query, byte[] body) {
        try {
            JSONObject in = body.length == 0 ? new JSONObject()
                    : new JSONObject(new String(body, StandardCharsets.UTF_8));
            boolean get = "GET".equals(method), post = "POST".equals(method);
            if (get && path.equals("/api/settings")) return json(settings());
            if (post && path.equals("/api/setting")) return setting(in);
            if (get && path.equals("/api/special")) return json(special());
            if (post && path.equals("/api/special")) return saveSpecial(in);
            if ("DELETE".equals(method) && path.startsWith("/api/special/")) {
                return deleteSpecial(path.substring("/api/special/".length()));
            }
            if (post && path.equals("/api/special-preview")) return previewSpecial(in);
            if (get && path.equals("/api/apps")) return json(apps());
            if (post && path.equals("/api/app")) return app(in);
            if (get && path.equals("/api/city")) return json(searchCity(param(query, "q")));
            if (post && path.equals("/api/city")) return setCity(in);
            if (get && path.equals("/api/sky")) return json(sky());
            if (post && path.equals("/api/home-test")) return homeTest(in);
            if ("DELETE".equals(method) && path.equals("/api/home-test")) return stopHomeTest();
            if (post && path.equals("/api/show-home")) return showHome();
            if (post && path.equals("/api/sky-test")) return skyTest(in);
            if ("DELETE".equals(method) && path.equals("/api/sky-test")) return stopSkyTest();
            if (post && path.equals("/api/weather-key")) return saveWeatherKey(in);
            if ("DELETE".equals(method) && path.equals("/api/weather-key")) return removeWeatherKey();
            if (post && path.equals("/api/tick")) return tick(in);
            if (post && path.equals("/api/show-now")) return showNow(in);
            if (get && path.equals("/api/backup-info")) return json(backupInfo());
            if (get && path.equals("/api/problem")) return json(problem());
            if (post && path.equals("/api/problem")) return problemAction(in);
            return null;
        } catch (JSONException e) {
            return UploadServer.Reply.text(400, "The request could not be read.");
        }
    }

    private static UploadServer.Reply json(Object o) {
        return UploadServer.Reply.json(o.toString());
    }

    private static UploadServer.Reply ok() {
        return UploadServer.Reply.text(200, "Done");
    }

    private static UploadServer.Reply bad(String why) {
        return UploadServer.Reply.text(400, why);
    }

    private void changed() {
        main.post(tv::changed);
    }

    // ---- Settings -------------------------------------------------------------------------------------------------

    private JSONArray settings() throws JSONException {
        JSONArray sections = new JSONArray();
        for (PhoneSettings.Section s : PhoneSettings.build(context)) {
            JSONArray controls = new JSONArray();
            for (PhoneSettings.Control c : s.controls) {
                JSONObject o = new JSONObject().put("key", c.key).put("label", c.label)
                        .put("type", c.type.name().toLowerCase(Locale.ROOT)).put("value", c.value);
                if (c.note != null) o.put("note", c.note);
                if (c.options != null) o.put("options", new JSONArray(java.util.Arrays.asList(c.options)));
                controls.put(o);
            }
            JSONObject o = new JSONObject().put("id", s.id).put("title", s.title).put("controls", controls);
            if (s.group != null) o.put("group", s.group);
            sections.put(o);
        }
        return sections;
    }

    private UploadServer.Reply setting(JSONObject in) {
        Object v = in.opt("value");
        String problem = PhoneSettings.set(context, in.optString("key"), v);
        if (problem != null) return bad(problem);
        changed();
        return ok();
    }

    // ---- Special days ---------------------------------------------------------------------------------------------

    private JSONObject special() throws JSONException {
        LocalDate today = LocalDate.now();
        List<SpecialDays.Day> days = new SpecialDayStore(context).days();
        List<String> photos = new Photos(context).list();
        JSONArray list = new JSONArray();
        List<SpecialDays.Day> sorted = new ArrayList<>(days);
        // Soonest first; days that are over go last.
        sorted.sort((a, b) -> {
            LocalDate na = a.next(today), nb = b.next(today);
            if (na == null || nb == null) return na == null ? (nb == null ? 0 : 1) : -1;
            return na.compareTo(nb);
        });
        for (SpecialDays.Day d : sorted) {
            LocalDate next = d.next(today);
            list.put(new JSONObject().put("id", d.id).put("name", d.name).put("from", d.from.toString())
                    .put("to", d.to.toString()).put("yearly", d.yearly).put("whenText", d.describeWhen())
                    .put("design", d.design == null ? "" : d.design.id)
                    .put("designLabel", d.design == null ? "" : d.design.label)
                    .put("line1", d.line1).put("line2", d.line2).put("lights", d.lights)
                    .put("photos", onTv(d.photos, photos))
                    .put("today", d.on(today)).put("over", next == null));
        }
        JSONArray designs = new JSONArray();
        for (Banners.Design d : Banners.Design.values()) {
            designs.put(new JSONObject().put("id", d.id).put("label", d.label));
        }
        JSONArray suggestions = new JSONArray();
        for (Festivals.Festival f : Festivals.ALL) {
            SpecialDays.Day s = SpecialDays.suggestion(f, today);
            if (s == null) continue;
            boolean added = false;
            for (SpecialDays.Day d : days) added |= d.name.equalsIgnoreCase(f.name);
            suggestions.put(new JSONObject().put("id", f.id).put("name", s.name).put("from", s.from.toString())
                    .put("to", s.to.toString()).put("yearly", s.yearly).put("design", s.design.id)
                    .put("line1", s.line1).put("line2", s.line2).put("lights", true).put("added", added)
                    .put("today", s.on(today)).put("dates", SHORT.format(s.from)
                            + (s.from.equals(s.to) ? "" : " – " + SHORT.format(s.to))));
        }
        return new JSONObject().put("days", list).put("designs", designs).put("suggestions", suggestions);
    }

    private UploadServer.Reply saveSpecial(JSONObject in) {
        String id = in.optString("id", "");
        SpecialDayStore store = new SpecialDayStore(context);
        if (!id.matches("[0-9a-z]{1,20}") || store.byId(id) == null) {
            id = Banners.newId(System.currentTimeMillis(), random.nextInt());
        }
        LocalDate from, to;
        try {
            from = LocalDate.parse(in.optString("from"));
            String t = in.optString("to", "");
            to = t.isEmpty() ? from : LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return bad("Choose the date.");
        }
        String designId = in.optString("design", "");
        Banners.Design design = designId.isEmpty() ? null : Banners.Design.parse(designId);
        if (!designId.isEmpty() && design == null) return bad("Choose a banner design.");
        List<String> picked = new ArrayList<>();
        JSONArray ps = in.optJSONArray("photos");
        List<String> onTv = new Photos(context).list();
        for (int i = 0; ps != null && i < ps.length(); i++) {
            String p = ps.optString(i);
            if (!onTv.contains(p)) return bad("A chosen photo is no longer on the TV.");
            if (!picked.contains(p)) picked.add(p);
        }
        boolean yearly = in.optBoolean("yearly", false);
        SpecialDays.Day d = SpecialDays.make(id, in.optString("name"), from, to, yearly, design,
                in.optString("line1"), in.optString("line2"), in.optBoolean("lights", true), picked,
                System.currentTimeMillis());
        if (d == null) {
            if (SpecialDays.cleanName(in.optString("name")).isEmpty()) return bad("Type a name for the day.");
            if (design != null && Banners.cleanLine(in.optString("line1"), Banners.LINE1_MAX).isEmpty()) {
                return bad("Type the banner's first line, or choose No banner.");
            }
            return bad("A day every year can last up to " + SpecialDays.YEARLY_MAX_DAYS + " days.");
        }
        store.save(d);
        changed();
        return UploadServer.Reply.text(200, "Saved: " + d.describeWhen());
    }

    private static JSONArray onTv(List<String> picked, List<String> onTv) {
        JSONArray out = new JSONArray();
        for (String p : picked) if (onTv.contains(p)) out.put(p);
        return out;
    }

    private UploadServer.Reply deleteSpecial(String id) {
        if (!new SpecialDayStore(context).delete(id)) return UploadServer.Reply.text(404, "Already removed.");
        changed();
        return ok();
    }

    private UploadServer.Reply previewSpecial(JSONObject in) {
        Banners.Design design = Banners.Design.parse(in.optString("design"));
        String l1 = Banners.cleanLine(in.optString("line1"), Banners.LINE1_MAX);
        if (design == null) return bad("Choose a banner design.");
        if (l1.isEmpty()) return bad("Type the banner's first line.");
        DecorTest.LightsChoice lights = !in.has("lights") ? DecorTest.LightsChoice.REAL
                : in.optBoolean("lights") ? DecorTest.LightsChoice.ON : DecorTest.LightsChoice.OFF;
        // The banner (and the day's lights) on Home itself, as a 15-minute test; the sky test, if any, carries on.
        new DecorTestStore(context).set(new DecorTest(DecorTest.BannerChoice.DESIGN, design, l1,
                in.optString("line2"), lights, System.currentTimeMillis() + SkyState.TEST_MS));
        main.post(tv::showHome);
        return UploadServer.Reply.text(200, "Showing on the TV's Home");
    }

    // ---- Apps -----------------------------------------------------------------------------------------------------

    private JSONObject apps() throws JSONException {
        AppsModel.Snapshot s = AppsModel.get(context).current();
        return new JSONObject().put("barMax", AppStates.APPS_BAR_MAX)
                .put("bar", appList(s.appsBar)).put("home", appList(s.home)).put("hidden", appList(s.hidden));
    }

    private static JSONArray appList(List<AppInfo> apps) throws JSONException {
        JSONArray out = new JSONArray();
        for (AppInfo a : apps) out.put(new JSONObject().put("pkg", a.packageName).put("label", a.label));
        return out;
    }

    private UploadServer.Reply app(JSONObject in) {
        String pkg = in.optString("pkg");
        AppsModel model = AppsModel.get(context);
        boolean done;
        switch (in.optString("action")) {
            case "bar":
                done = model.moveTo(pkg, AppStates.State.APPS_BAR);
                if (!done) return bad("The Apps bar is full (" + AppStates.APPS_BAR_MAX + "). Move an app out first.");
                break;
            case "home":
                done = model.moveTo(pkg, AppStates.State.HOME);
                break;
            case "hidden":
                done = model.moveTo(pkg, AppStates.State.HIDDEN);
                break;
            case "up":
                done = model.moveUp(pkg);
                break;
            case "down":
                done = model.moveDown(pkg);
                break;
            default:
                return bad("Unknown action.");
        }
        if (done) changed();
        return ok();
    }

    // ---- Sky ------------------------------------------------------------------------------------------------------

    private JSONObject sky() throws JSONException {
        SkyStore store = new SkyStore(context);
        ThemeParts parts = ThemeParts.active(context);
        ZoneId zone = ZoneId.systemDefault();
        LocalTime[] riseSet = SkyMath.riseAndSet(LocalDate.now(zone), zone, store.place());
        SkyState now = store.now();
        WeatherCode.Kind real = store.realKind();
        JSONObject o = new JSONObject()
                .put("theme", parts.theme().name)
                .put("drawn", parts.has(Themes.Part.SKY))
                .put("on", parts.shows(Themes.Part.SKY))
                .put("place", store.placeName() == null ? "" : store.placeName())
                .put("sunrise", hhmm(riseSet[0])).put("sunset", hhmm(riseSet[1]))
                .put("body", now.position.body == SkyMath.Body.SUN ? "Sun" : "Moon")
                .put("percent", Math.round(now.position.progress * 100))
                .put("moon", moonName(SkyMath.moonPhase(System.currentTimeMillis())))
                .put("weather", real == null ? "" : real.label)
                .put("showing", now.kind == null ? "Clear" : now.kind.label);
        long nowMs = System.currentTimeMillis();
        o.put("reading", reading(real));
        o.put("drawing", drawing(now));

        // Today's real banner and lights, for the test's "real" choices.
        SpecialDayStore days = new SpecialDayStore(context);
        Banners.Shown banner = SpecialDays.banner(LocalDate.now(zone), days.days());
        o.put("bannerToday", banner == null ? "" : banner.design.label + ": " + banner.line1)
                .put("bannerDrawn", parts.has(Themes.Part.BANNER)).put("bannerOn", parts.shows(Themes.Part.BANNER))
                .put("lightsDrawn", parts.has(Themes.Part.FESTIVE_LIGHTS))
                .put("lightsToday", SpecialDays.lightsOn(parts.lights().mode(), LocalDate.now(zone), days.days()));

        SkyState.Test t = store.test();
        DecorTest d = new DecorTestStore(context).test();
        if (t != null || d != null) {
            long until = Math.max(t == null ? 0 : t.until, d == null ? 0 : d.until);
            long left = Math.max(0, until - nowMs);
            JSONObject test = new JSONObject().put("minutesLeft", (left + 59_999) / 60_000)
                    .put("kind", t == null || t.kind == null ? "" : t.kind.name())
                    .put("minute", t == null ? -1 : t.minuteOfDay)
                    .put("banner", d == null ? "real" : d.banner == DecorTest.BannerChoice.DESIGN ? d.design.id
                            : d.banner.name().toLowerCase(Locale.ROOT))
                    .put("line1", d == null || d.design == null ? "" : d.line1)
                    .put("line2", d == null || d.design == null ? "" : d.line2)
                    .put("lights", d == null ? "real" : d.lights.name().toLowerCase(Locale.ROOT));
            o.put("test", test);
        }
        JSONArray kinds = new JSONArray();
        for (WeatherCode.Kind k : WeatherCode.Kind.values()) {
            kinds.put(new JSONObject().put("id", k.name()).put("label", k.label));
        }
        JSONArray designs = new JSONArray();
        for (Banners.Design b : Banners.Design.values()) {
            designs.put(new JSONObject().put("id", b.id).put("label", b.label));
        }
        return o.put("kinds", kinds).put("designs", designs);
    }

    /**
     * The weather reading behind the sky, in words, so the phone can tell a real cloudy sky from a test: what the
     * service said, which service, for where and how long ago. Null-safe: {} when there is none.
     */
    private JSONObject reading(WeatherCode.Kind real) throws JSONException {
        WeatherStore ws = new WeatherStore(context);
        JSONObject r = new JSONObject().put("enabled", ws.enabled());
        WeatherStore.Reading reading = ws.reading();
        if (reading == null) return r;
        long ago = Math.max(0, System.currentTimeMillis() - reading.fetchedAt) / 60_000;
        WeatherSource from = ws.readingProvider();
        return r.put("code", reading.code).put("said", WeatherCode.describe(reading.code))
                .put("kind", WeatherCode.kind(reading.code).label)
                .put("city", reading.city == null ? "" : reading.city)
                .put("from", from == null ? "" : from.label)
                .put("minutesAgo", ago)
                .put("used", real != null);
    }

    /** What the sky is drawing now, in words ("3 white clouds", "110 rain drops, lightning"). */
    private static String drawing(SkyState s) {
        SkyMath.Effects e = s.effects;
        List<String> what = new ArrayList<>();
        if (e.clouds > 0) what.add(e.clouds + (e.greyClouds ? " grey" : " white") + (e.clouds == 1 ? " cloud" : " clouds"));
        if (e.rain > 0) what.add(e.rain + (e.drizzle ? " drizzle drops" : " rain drops"));
        if (e.snow > 0) what.add(e.snow + " snowflakes");
        if (e.lightning) what.add("lightning");
        if (e.haze) what.add("low haze");
        if (e.stars && s.position.body == SkyMath.Body.MOON) what.add("stars");
        String body = s.position.body == SkyMath.Body.SUN ? "the sun" : "the moon";
        if (e.bodyAlpha <= 0) what.add(body + " hidden");
        else if (e.bodyAlpha < 1) what.add(body + " behind cloud");
        else what.add(body);
        return String.join(", ", what);
    }

    private UploadServer.Reply homeTest(JSONObject in) {
        String k = in.optString("kind", "");
        WeatherCode.Kind kind = null;
        if (!k.isEmpty()) {
            try {
                kind = WeatherCode.Kind.valueOf(k);
            } catch (IllegalArgumentException e) {
                return bad("Unknown weather.");
            }
        }
        int minute = in.optInt("minute", -1);
        if (minute < -1 || minute > 1439) return bad("Choose a time of day.");
        String b = in.optString("banner", "real");
        DecorTest.BannerChoice banner;
        Banners.Design design = null;
        if (b.isEmpty() || b.equals("real")) banner = DecorTest.BannerChoice.REAL;
        else if (b.equals("none")) banner = DecorTest.BannerChoice.NONE;
        else {
            design = Banners.Design.parse(b);
            if (design == null) return bad("Unknown banner design.");
            banner = DecorTest.BannerChoice.DESIGN;
        }
        String l = in.optString("lights", "real");
        DecorTest.LightsChoice lights = l.equals("on") ? DecorTest.LightsChoice.ON
                : l.equals("off") ? DecorTest.LightsChoice.OFF : DecorTest.LightsChoice.REAL;

        long until = System.currentTimeMillis() + SkyState.TEST_MS;
        SkyStore sky = new SkyStore(context);
        if (kind == null && minute < 0) sky.stopTest();
        else sky.setTest(new SkyState.Test(kind, minute, until));
        DecorTestStore decor = new DecorTestStore(context);
        DecorTest d = new DecorTest(banner, design, in.optString("line1"), in.optString("line2"), lights, until);
        if (d.changesAnything()) decor.set(d);
        else decor.stop();

        boolean show = in.optBoolean("show", false);
        Runnable onTv = show ? tv::showHome : tv::testChanged;
        main.post(onTv);
        boolean any = kind != null || minute >= 0 || d.changesAnything();
        if (!any) return UploadServer.Reply.text(200, show ? "Showing the real Home on the TV" : "Nothing to test: all real");
        return UploadServer.Reply.text(200, show ? "Showing on the TV's Home for 15 minutes" : "Updated on the TV");
    }

    private UploadServer.Reply stopHomeTest() {
        new SkyStore(context).stopTest();
        new DecorTestStore(context).stop();
        main.post(tv::testChanged);
        return UploadServer.Reply.text(200, "Test ended: Home shows the real sky, banner and lights");
    }

    private UploadServer.Reply showHome() {
        main.post(tv::showHome);
        return UploadServer.Reply.text(200, "Showing Home on the TV");
    }

    private UploadServer.Reply skyTest(JSONObject in) {
        String k = in.optString("kind", "");
        WeatherCode.Kind kind = null;
        if (!k.isEmpty()) {
            try {
                kind = WeatherCode.Kind.valueOf(k);
            } catch (IllegalArgumentException e) {
                return bad("Unknown weather.");
            }
        }
        int minute = in.optInt("minute", -1);
        if (minute < -1 || minute > 1439) return bad("Choose a time of day.");
        new SkyStore(context).setTest(new SkyState.Test(kind, minute,
                System.currentTimeMillis() + SkyState.TEST_MS));
        main.post(tv::showHome);
        return UploadServer.Reply.text(200, "Showing on the TV's Home for 15 minutes");
    }

    private UploadServer.Reply stopSkyTest() {
        new SkyStore(context).stopTest();
        main.post(tv::testChanged);
        return UploadServer.Reply.text(200, "Back to the real sky");
    }

    private static String hhmm(LocalTime t) {
        return String.format(Locale.ROOT, "%d:%02d", t.getHour(), t.getMinute());
    }

    private static String moonName(float phase) {
        String[] names = {"New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon",
                "Waning gibbous", "Last quarter", "Waning crescent"};
        return names[Math.round(phase * 8) % 8];
    }

    // ---- Wallpaper photos -----------------------------------------------------------------------------------------

    /** Ticks or unticks photos (the same choice as OK on a photo in Settings › Wallpapers). */
    private UploadServer.Reply tick(JSONObject in) {
        JSONArray ids = in.optJSONArray("ids");
        if (ids == null || ids.length() == 0) return bad("Choose a photo.");
        boolean on = in.optBoolean("ticked", true);
        List<String> onTv = new Photos(context).list();
        WallpaperStore ws = new WallpaperStore(context);
        java.util.Set<String> t = ws.ticked();
        t.retainAll(onTv);   // forget photos that are gone
        if (ids.length() == 1 && "*".equals(ids.optString(0))) {
            if (on) t.addAll(onTv);
            else t.clear();
        } else {
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.optString(i);
                if (!onTv.contains(id)) return bad("That photo is no longer on the TV.");
                if (on) t.add(id);
                else t.remove(id);
            }
        }
        ws.setTicked(t);
        changed();
        return UploadServer.Reply.text(200, t.isEmpty() ? "No photos ticked: Home shows the built-in picture"
                : t.size() == 1 ? "1 photo on Home" : t.size() + " photos on Home, taking turns");
    }

    /** Ticks a photo and puts it on Home now; rotation carries on from it. */
    private UploadServer.Reply showNow(JSONObject in) {
        String id = in.optString("id");
        if (!new Photos(context).list().contains(id)) return bad("That photo is no longer on the TV.");
        WallpaperStore ws = new WallpaperStore(context);
        java.util.Set<String> t = ws.ticked();
        t.add(id);
        ws.setTicked(t);
        ws.setCurrent(id, System.currentTimeMillis());
        changed();
        String note = ws.enabled() ? "" : " (Wallpaper is off in Settings: turn it on to see it)";
        return UploadServer.Reply.text(200, "On Home when you go back to it" + note);
    }

    // ---- Backup and problems --------------------------------------------------------------------------------------

    private JSONObject backupInfo() throws JSONException {
        int[] p = Backup.photoInfo(context);
        return new JSONObject().put("photos", p[0]).put("photosMb", p[1]);
    }

    private JSONObject problem() throws JSONException {
        JSONObject o = new JSONObject().put("safeMode", CrashLog.safeMode(context));
        CrashLog.Problem p = CrashLog.last(context);
        if (p != null) o.put("last", new JSONObject().put("when", p.when).put("version", p.version).put("text", p.text));
        return o;
    }

    private UploadServer.Reply problemAction(JSONObject in) {
        switch (in.optString("action")) {
            case "leave":
                CrashLog.leave(context);
                break;
            case "clear":
                CrashLog.clear(context);
                break;
            default:
                return bad("Unknown action.");
        }
        changed();
        return ok();
    }

    // ---- City -----------------------------------------------------------------------------------------------------

    private static JSONArray searchCity(String q) throws JSONException {
        JSONArray out = new JSONArray();
        if (q == null || q.trim().length() < 2) return out;
        for (City c : WeatherClient.search(q.trim())) {
            out.put(new JSONObject().put("name", c.name).put("detail", c.detail).put("latitude", c.latitude)
                    .put("longitude", c.longitude));
        }
        return out;
    }

    private UploadServer.Reply setCity(JSONObject in) {
        City c = new City(in.optString("name"), in.optDouble("latitude", 0), in.optDouble("longitude", 0),
                in.optString("detail"));
        if (!c.valid()) return bad("Choose a city from the search.");
        WeatherStore ws = new WeatherStore(context);
        ws.setByHand(c);
        ws.setAutomatic(false);   // choosing a city means "use this one"
        WeatherStore.Reading r = Weather.update(context);
        changed();
        return UploadServer.Reply.text(200, r == null ? "Saved. No weather yet: check the TV's internet connection."
                : "Saved");
    }

    /** Saves the key whatever the test says (a new key can take a while to start working) and reports the test. */
    private UploadServer.Reply saveWeatherKey(JSONObject in) {
        String key = in.optString("key", "").trim();
        if (!WeatherSource.keyLooksRight(key)) {
            return bad("That doesn't look like an OpenWeatherMap key (32 letters and digits, from your account's "
                    + "API keys page).");
        }
        WeatherStore ws = new WeatherStore(context);
        ws.setOwmKey(key);
        ws.setProvider(WeatherSource.OPEN_WEATHER_MAP);
        String problem = Weather.testOwmKey(context, key);
        Weather.update(context);   // the new reading shows on the TV straight away
        changed();
        return UploadServer.Reply.text(200, problem == null
                ? "Saved. The key works: the weather now comes from OpenWeatherMap."
                : "Saved, but: " + problem + ". Until it works the TV uses Open-Meteo.");
    }

    private UploadServer.Reply removeWeatherKey() {
        WeatherStore ws = new WeatherStore(context);
        ws.setOwmKey(null);
        ws.setProvider(WeatherSource.OPEN_METEO);
        Weather.update(context);
        changed();
        return UploadServer.Reply.text(200, "Key removed. The weather comes from Open-Meteo.");
    }

    private static String param(String query, String name) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
