package com.chakriau.tvhome.v2.wallpapers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/** The TV's address on the home network, for the upload page's link and QR code. */
public final class NetworkAddress {

    private NetworkAddress() {
    }

    /**
     * The TV's private IPv4 address, preferring Ethernet and Wi-Fi over others (a VPN's tunnel is skipped), or null
     * when the TV is not on a network.
     */
    public static String lanIPv4() {
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return null;
            for (NetworkInterface nif : Collections.list(all)) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                String name = nif.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec")) continue;
                int rank = name.startsWith("eth") ? 0 : name.startsWith("wlan") ? 1 : 2;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress() && rank < bestRank) {
                        best = a.getHostAddress();
                        bestRank = rank;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }
}
