package com.chakriau.tvhome.v2.system;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * What the Network button shows (Requirements.md 5.9): the Wi-Fi name, "Ethernet" or "Offline". Android only gives
 * the Wi-Fi name to apps allowed to use location; without it the button says "Wi-Fi".
 */
public final class NetworkStatus {

    public enum Kind { WIFI, ETHERNET, OFFLINE }

    public final Kind kind;
    /** "Home-WiFi", "Wi-Fi", "Ethernet" or "Offline". */
    public final String label;
    /** Wi-Fi signal 1 to 4 (4 for Ethernet, 0 offline). */
    public final int level;

    private NetworkStatus(Kind kind, String label, int level) {
        this.kind = kind;
        this.label = label;
        this.level = level;
    }

    /** Whether the launcher may read the Wi-Fi name. */
    public static boolean nameAllowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")   // WifiManager.getConnectionInfo: the callback form needs Android 12
    public static NetworkStatus read(Context context) {
        boolean wifi = false, ethernet = false, online = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                // All networks, not just the active one: with a VPN on, the active network is the VPN itself.
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                    if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                    online = true;
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
                }
            }
        } catch (RuntimeException e) {
            Log.w("TvHomeSystem", "network state: " + e);
        }
        if (!online) return new NetworkStatus(Kind.OFFLINE, "Offline", 0);
        if (ethernet) return new NetworkStatus(Kind.ETHERNET, "Ethernet", 4);
        String name = null;
        int level = 3;
        if (wifi) {
            try {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm == null ? null : wm.getConnectionInfo();
                if (info != null) {
                    name = TopBar.wifiName(info.getSSID());
                    level = Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(info.getRssi(), 5)));
                }
            } catch (RuntimeException e) {
                Log.w("TvHomeSystem", "Wi-Fi info: " + e);
            }
        }
        return new NetworkStatus(Kind.WIFI, name != null ? name : "Wi-Fi", level);
    }
}
