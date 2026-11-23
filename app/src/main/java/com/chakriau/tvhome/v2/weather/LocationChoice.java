package com.chakriau.tvhome.v2.weather;

/**
 * Which place the weather is for (Requirements.md 5.8). Automatic tries, in order: the TV's own location service,
 * IP geolocation (only with no VPN active, since a VPN points to the wrong city), the last good city, then the city
 * set by hand. Set by hand uses that city only. Plain Java, unit-tested.
 */
public final class LocationChoice {

    public enum Source {
        TV("TV location"), IP("internet address"), LAST_GOOD("last known city"), BY_HAND("set by hand");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** The chosen city and where it came from; {@link #city} is null when nothing is known. */
    public static final class Result {
        public final City city;
        public final Source source;

        Result(City city, Source source) {
            this.city = city;
            this.source = source;
        }
    }

    private LocationChoice() {
    }

    /**
     * @param automatic   false when the owner chose "set by hand"
     * @param tv          from the TV's location service, or null
     * @param vpnActive   whether a VPN is on (IP location is then skipped)
     * @param ip          from IP geolocation, or null (not looked up while a VPN is on)
     * @param lastGood    the last city that worked automatically, or null
     * @param byHand      the city set by hand, or null
     */
    public static Result choose(boolean automatic, City tv, boolean vpnActive, City ip, City lastGood, City byHand) {
        if (!automatic) return new Result(ok(byHand) ? byHand : null, Source.BY_HAND);
        if (ok(tv)) return new Result(tv, Source.TV);
        if (!vpnActive && ok(ip)) return new Result(ip, Source.IP);
        if (ok(lastGood)) return new Result(lastGood, Source.LAST_GOOD);
        if (ok(byHand)) return new Result(byHand, Source.BY_HAND);
        return new Result(null, null);
    }

    /** Whether an automatic result should be saved as the new last good city. */
    public static boolean remember(Result r) {
        return r.city != null && (r.source == Source.TV || r.source == Source.IP);
    }

    private static boolean ok(City c) {
        return c != null && c.valid();
    }
}
