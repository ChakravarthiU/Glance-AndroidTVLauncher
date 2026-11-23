package com.chakriau.tvhome.v2.weather;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The internet services behind the weather (Requirements.md 5.8): Open-Meteo (or OpenWeatherMap, with the owner's
 * key) for the current weather, Open-Meteo for the city search, and keyless IP geolocation services. All calls block: run them off the main thread. Failures return null (or an
 * empty list) rather than throwing.
 */
public final class WeatherClient {

    private static final String TAG = "TvHomeWeather";
    private static final int TIMEOUT_MS = 7000;

    /** Current weather: temperature, a WMO weather code ({@link WeatherCode}), day or night. */
    public static final class Current {
        public final double celsius;
        public final int code;
        public final boolean day;

        Current(double celsius, int code, boolean day) {
            this.celsius = celsius;
            this.code = code;
            this.day = day;
        }
    }

    private WeatherClient() {
    }

    public static Current forecast(City c) {
        String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day&timezone=auto", c.latitude, c.longitude);
        try {
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            return new Current(cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
        } catch (Exception e) {
            Log.w(TAG, "forecast failed: " + e);
            return null;
        }
    }

    /** OpenWeatherMap's answer: the weather, or what went wrong in words for Settings. */
    public static final class OwmResult {
        public final Current current;
        public final String problem;

        OwmResult(Current current, String problem) {
            this.current = current;
            this.problem = problem;
        }
    }

    /**
     * Current weather from OpenWeatherMap's free "current weather" API (2.5), its condition id turned into a WMO code
     * ({@link OwmCode}). The key goes only into the request; it is never logged.
     */
    public static OwmResult openWeatherMap(City c, String key) {
        String url = String.format(Locale.ROOT, "https://api.openweathermap.org/data/2.5/weather?lat=%.4f&lon=%.4f"
                + "&units=metric&appid=", c.latitude, c.longitude);
        try {
            JSONObject o = new JSONObject(get(url + URLEncoder.encode(key, "UTF-8")));
            JSONObject w = o.getJSONArray("weather").getJSONObject(0);
            double celsius = o.getJSONObject("main").getDouble("temp");
            return new OwmResult(new Current(celsius, OwmCode.toWmo(w.getInt("id")), OwmCode.day(w.optString("icon"))),
                    null);
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            Log.w(TAG, "OpenWeatherMap failed: " + (m.startsWith("HTTP ") ? m : e.getClass().getSimpleName()));
            if (m.equals("HTTP 401")) {
                return new OwmResult(null, "OpenWeatherMap doesn't accept the key (a new key can take a couple of "
                        + "hours to start working)");
            }
            if (m.equals("HTTP 429")) return new OwmResult(null, "Too many requests on this key; try again later");
            return new OwmResult(null, "OpenWeatherMap didn't answer (" + (m.startsWith("HTTP ") ? m
                    : "no connection") + ")");
        } catch (Exception e) {
            Log.w(TAG, "OpenWeatherMap answer unreadable: " + e.getClass().getSimpleName());
            return new OwmResult(null, "OpenWeatherMap's answer couldn't be read");
        }
    }

    /** Places matching a typed name (Settings › Time, date &amp; weather › City), best first. */
    public static List<City> search(String name) {
        List<City> out = new ArrayList<>();
        String q = name == null ? "" : name.trim();
        if (q.length() < 2) return out;   // Open-Meteo returns nothing for one letter
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&format=json&name="
                    + URLEncoder.encode(q, "UTF-8");
            JSONArray results = new JSONObject(get(url)).optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String detail = join(r.optString("admin1", ""), r.optString("country", ""));
                City c = new City(r.optString("name", ""), r.optDouble("latitude", 0), r.optDouble("longitude", 0),
                        detail);
                if (c.valid()) out.add(c);
            }
        } catch (Exception e) {
            Log.w(TAG, "city search failed: " + e);
        }
        return out;
    }

    /** Where the TV's internet address says it is, or null. Only meaningful with no VPN on. */
    public static City ipLocation() {
        // Two keyless services, the second only if the first fails.
        City c = ipFrom("https://api.ip.sb/geoip");
        return c != null ? c : ipFrom("https://ipwho.is/");
    }

    private static City ipFrom(String url) {
        try {
            JSONObject o = new JSONObject(get(url));
            if (o.has("success") && !o.optBoolean("success", true)) return null;
            City c = new City(o.optString("city", ""), o.optDouble("latitude", 0), o.optDouble("longitude", 0),
                    join(o.optString("region", ""), o.optString("country", "")));
            return c.valid() ? c : null;
        } catch (Exception e) {
            Log.w(TAG, "IP location failed (" + url + "): " + e);
            return null;
        }
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + ", " + b;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "TvHomeV2 (Android TV launcher)");
            c.setRequestProperty("Accept", "application/json");
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > 512 * 1024) throw new IOException("Response too large");
                }
                return new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
