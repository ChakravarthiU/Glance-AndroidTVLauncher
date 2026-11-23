package com.chakriau.tvhome.v2.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Finds the place (Requirements.md 5.8, order in {@link LocationChoice}) and fetches its weather. Blocking: call off
 * the main thread.
 */
public final class Weather {

    private static final String TAG = "TvHomeWeather";

    private Weather() {
    }

    /** Fetches fresh weather and saves it. Null when weather is off, no place is known, or the network failed. */
    public static WeatherStore.Reading update(Context context) {
        WeatherStore store = new WeatherStore(context);
        if (!store.enabled()) return null;
        boolean auto = store.automatic();
        City tv = auto ? tvLocation(context) : null;
        boolean vpn = auto && tv == null && vpnActive(context);
        City ip = auto && tv == null && !vpn ? WeatherClient.ipLocation() : null;
        LocationChoice.Result r = LocationChoice.choose(auto, tv, vpn, ip, store.lastGood(), store.byHand());
        if (r.city == null) return null;
        if (LocationChoice.remember(r)) store.setLastGood(r.city);
        // OpenWeatherMap when chosen and keyed; Open-Meteo otherwise, and whenever OpenWeatherMap fails (so a bad
        // key or a used-up day still shows weather, with the reason in Settings).
        WeatherSource from = WeatherSource.OPEN_METEO;
        String problem = null;
        WeatherClient.Current now = null;
        if (store.provider() == WeatherSource.OPEN_WEATHER_MAP) {
            String key = store.owmKey();
            if (key == null) {
                problem = "No OpenWeatherMap key yet: add it on the phone page";
            } else {
                WeatherClient.OwmResult o = WeatherClient.openWeatherMap(r.city, key);
                now = o.current;
                problem = o.problem;
                if (now != null) from = WeatherSource.OPEN_WEATHER_MAP;
            }
        }
        if (now == null) now = WeatherClient.forecast(r.city);
        if (now == null) return null;
        store.setReadingProvider(from, problem);
        WeatherStore.Reading reading = new WeatherStore.Reading(now.celsius, now.code, now.day, r.city.name,
                System.currentTimeMillis());
        store.setReading(reading, r.source);
        return reading;
    }

    /**
     * Tries an OpenWeatherMap key for the weather's city (the phone page's "Save and test"): null if it works, else
     * what went wrong. Blocking.
     */
    public static String testOwmKey(Context context, String key) {
        WeatherStore store = new WeatherStore(context);
        City c = store.automatic() ? store.lastGood() : store.byHand();
        if (c == null) c = store.automatic() ? store.byHand() : store.lastGood();
        if (c == null) c = new City("London", 51.5074, -0.1278, "");   // no city yet: any place tests the key
        return WeatherClient.openWeatherMap(c, key).problem;
    }

    /** Whether the owner has let the launcher use the TV's location service. */
    public static boolean locationAllowed(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * The TV's own location, if it has a location service, the owner allowed it, and the place can be named. Most TVs
     * have no location fix; then this is null and IP location is used.
     */
    @SuppressWarnings("deprecation")   // Geocoder.getFromLocation: the listener form needs Android 13
    @SuppressLint("MissingPermission")  // checked by locationAllowed() first
    static City tvLocation(Context context) {
        if (!locationAllowed(context)) return null;
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            // Ignore fixes older than a day: the TV may have moved house.
            if (best == null || System.currentTimeMillis() - best.getTime() > 24 * 60 * 60_000L) return null;
            if (!Geocoder.isPresent()) return null;
            List<Address> found = new Geocoder(context, Locale.getDefault())
                    .getFromLocation(best.getLatitude(), best.getLongitude(), 1);
            if (found == null || found.isEmpty()) return null;
            Address a = found.get(0);
            String name = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
            if (name == null) return null;
            City c = new City(name, best.getLatitude(), best.getLongitude(),
                    (a.getAdminArea() == null ? "" : a.getAdminArea() + ", ")
                            + (a.getCountryName() == null ? "" : a.getCountryName()));
            return c.valid() ? c : null;
        } catch (Exception e) {
            Log.w(TAG, "TV location failed: " + e);
            return null;
        }
    }

    /** Whether a VPN is on (IP location would then give the VPN server's city). */
    public static boolean vpnActive(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "VPN check failed: " + e);
        }
        // Some VPN apps show only as a tunnel interface.
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String n = nif.getName().toLowerCase(Locale.ROOT);
                if (nif.isUp() && (n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("wg"))) return true;
            }
        } catch (Exception ignored) {
            // No interfaces readable: assume none.
        }
        return false;
    }
}
