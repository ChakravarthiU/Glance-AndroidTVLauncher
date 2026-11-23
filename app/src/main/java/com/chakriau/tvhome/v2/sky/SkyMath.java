package com.chakriau.tvhome.v2.sky;

import com.chakriau.tvhome.v2.weather.WeatherCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The sky's sums (Requirements.md 5.10), plain Java so they are unit-tested: when the sun rises and sets for a place,
 * whether the sun or the moon is up and how far along its arc it is, where on the arc that puts it, the moon's
 * phase, and what each kind of weather shows.
 *
 * <p>The sun crosses Home from the left edge (rising) to the right edge (setting) between sunrise and sunset; the
 * moon crosses the same way from sunset to the next sunrise. (The real moon rises at a different time each day; on
 * Home it simply takes the night, in its real phase.)
 */
public final class SkyMath {

    /** Used when the place isn't known (no city yet): sunrise 6:00, sunset 18:00 on the TV's clock. */
    static final LocalTime DEFAULT_RISE = LocalTime.of(6, 0), DEFAULT_SET = LocalTime.of(18, 0);
    private static final double SYNODIC_DAYS = 29.530588853;
    /** A new moon: 6 January 2000, 18:14 UTC. */
    private static final long NEW_MOON_MS = 947182440000L;
    private static final long DAY_MS = 86_400_000L;

    public enum Body { SUN, MOON }

    /** Where the sun or moon is: which one, and how far along its arc (0 = rising at the left, 1 = setting). */
    public static final class Position {
        public final Body body;
        public final float progress;

        Position(Body body, float progress) {
            this.body = body;
            this.progress = progress;
        }
    }

    private SkyMath() {
    }

    // ---- Sunrise and sunset ---------------------------------------------------------------------------------------

    /**
     * Sunrise and sunset on {@code date} at a place, as epoch milliseconds, or null where the sun doesn't rise or set
     * that day (far north or south). The sunrise equation (NOAA's simplified form), good to a minute or two.
     */
    public static long[] sunEvents(LocalDate date, double latitude, double longitude) {
        double n = date.toEpochDay() - 10957 + 0.0008;   // days since 1 Jan 2000, 12:00 UTC
        double jStar = n - longitude / 360.0;
        double m = mod360(357.5291 + 0.98560028 * jStar);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.02 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lambda = Math.toRadians(mod360(m + c + 180 + 102.9372));
        double transit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lambda);
        double sinDecl = Math.sin(lambda) * Math.sin(Math.toRadians(23.4397));
        double cosDecl = Math.cos(Math.asin(sinDecl));
        double lat = Math.toRadians(latitude);
        double cosOmega = (Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * sinDecl) / (Math.cos(lat) * cosDecl);
        if (cosOmega < -1 || cosOmega > 1 || Double.isNaN(cosOmega)) return null;
        double omega = Math.toDegrees(Math.acos(cosOmega));
        return new long[]{julianToMillis(transit - omega / 360.0), julianToMillis(transit + omega / 360.0)};
    }

    /** Sunrise and sunset for the TV's {@code date} in {@code zone}: from the place if known, else 6:00 and 18:00. */
    static long[] dayOf(LocalDate date, ZoneId zone, double[] place) {
        long[] e = place == null ? null : sunEvents(date, place[0], place[1]);
        if (e != null) return e;
        return new long[]{ZonedDateTime.of(date, DEFAULT_RISE, zone).toInstant().toEpochMilli(),
                ZonedDateTime.of(date, DEFAULT_SET, zone).toInstant().toEpochMilli()};
    }

    /**
     * Whether the sun or the moon is up at {@code now}, and how far along. {@code place} is {latitude, longitude},
     * or null when no city is known.
     */
    public static Position position(long now, ZoneId zone, double[] place) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long[] d = dayOf(today, zone, place);
        if (now >= d[0] && now <= d[1]) return new Position(Body.SUN, fraction(now, d[0], d[1]));
        if (now < d[0]) {   // before sunrise: the night that began at yesterday's sunset
            long[] y = dayOf(today.minusDays(1), zone, place);
            return new Position(Body.MOON, fraction(now, y[1], d[0]));
        }
        long[] t = dayOf(today.plusDays(1), zone, place);   // after sunset: the night until tomorrow's sunrise
        return new Position(Body.MOON, fraction(now, d[1], t[0]));
    }

    /** Today's sunrise and sunset as clock times in {@code zone}, for Settings ("6:12 – 17:48"). */
    public static LocalTime[] riseAndSet(LocalDate date, ZoneId zone, double[] place) {
        long[] d = dayOf(date, zone, place);
        return new LocalTime[]{Instant.ofEpochMilli(d[0]).atZone(zone).toLocalTime().withSecond(0).withNano(0),
                Instant.ofEpochMilli(d[1]).atZone(zone).toLocalTime().withSecond(0).withNano(0)};
    }

    // ---- The arc --------------------------------------------------------------------------------------------------

    /**
     * Where the sun or moon is drawn: x from {@code left} (rising) to {@code right} (setting), y on a half-sine from
     * {@code horizon} up to {@code peak} at midday (or midnight) and back down.
     */
    public static float[] arc(float progress, float left, float right, float horizon, float peak) {
        float p = Math.max(0f, Math.min(1f, progress));
        float x = left + p * (right - left);
        float y = horizon - (float) Math.sin(Math.PI * p) * (horizon - peak);
        return new float[]{x, y};
    }

    /** How visible the body is near the horizon: it fades in over the first 4% of its arc and out over the last. */
    public static float horizonFade(float progress) {
        float edge = Math.min(progress, 1f - progress);
        return Math.max(0f, Math.min(1f, edge / 0.04f));
    }

    /**
     * How warm the sun looks (0 high in the sky .. 1 on the horizon), in 8 steps: it reddens and grows over the first
     * and last 18% of its arc, as it does through more air near the horizon.
     */
    public static float warmth(float progress) {
        float edge = Math.min(progress, 1f - progress);
        float w = 1f - Math.min(1f, Math.max(0f, edge) / 0.18f);
        return Math.round(w * 8) / 8f;
    }

    /** How much of the moon is lit, 0 (new) to 1 (full): its glow follows this. */
    public static float moonLit(float phase) {
        return (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, phase)));
    }

    // ---- The wallpaper behind the sky ------------------------------------------------------------------------------

    /**
     * How bright the wallpaper is across the sky box, as {@code columns} values from 0 (black) to 1 (white), left to
     * right: the mean luminance (Rec. 709) of each column of {@code pixels} (ARGB, row-major, {@code width} ×
     * {@code height}, already cut to the sky box and shrunk). Worked out once per photo; null for no pixels.
     */
    public static float[] skyLight(int[] pixels, int width, int height, int columns) {
        if (pixels == null || width <= 0 || height <= 0 || columns <= 0 || pixels.length < width * height) return null;
        float[] sum = new float[columns];
        int[] count = new int[columns];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = pixels[y * width + x];
                float lum = (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
                int col = Math.min(columns - 1, x * columns / width);
                sum[col] += lum;
                count[col]++;
            }
        }
        for (int i = 0; i < columns; i++) sum[i] = count[i] == 0 ? 0f : sum[i] / count[i];
        return sum;
    }

    /** The wallpaper's brightness at {@code across} (0 left edge .. 1 right edge of the sky box); 0 when unknown. */
    public static float lightAt(float[] light, float across) {
        if (light == null || light.length == 0) return 0f;
        if (light.length == 1) return light[0];
        float f = Math.max(0f, Math.min(1f, across)) * (light.length - 1);
        int i = (int) Math.floor(f), j = Math.min(light.length - 1, i + 1);
        return light[i] + (light[j] - light[i]) * (f - i);
    }

    /**
     * How strongly a soft patch of night sky is drawn behind the moon, 0 .. 1, for the wallpaper's brightness there:
     * none on dark photos (brightness ≤ 0.40, the moon already shows), full on bright ones (≥ 0.80), smooth between.
     * Without it a pale moon, its see-through dark side and its whitish halo vanish into a white photo.
     */
    public static float moonBackdrop(float light) {
        float t = Math.max(0f, Math.min(1f, (light - 0.40f) / 0.40f));
        return t * t * (3 - 2 * t);
    }

    // ---- The moon -------------------------------------------------------------------------------------------------

    /** The moon's phase at {@code now}: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    public static float moonPhase(long now) {
        double days = (now - NEW_MOON_MS) / (double) DAY_MS;
        double p = (days / SYNODIC_DAYS) % 1.0;
        if (p < 0) p += 1.0;
        return (float) p;
    }

    // ---- Weather --------------------------------------------------------------------------------------------------

    /** What a kind of weather shows in the sky. */
    public static final class Effects {
        /** How many clouds drift across, and whether they are rain-grey. */
        public final int clouds;
        public final boolean greyClouds;
        /** Falling rain drops (drizzle: fewer, shorter, fainter) and snowflakes. */
        public final int rain;
        public final boolean drizzle;
        public final int snow;
        /** Now and then a flash (thunderstorm). */
        public final boolean lightning;
        /** A pale haze low across the sky (fog). */
        public final boolean haze;
        /** How clearly the sun or moon shows through: 1 clear, less behind cloud. */
        public final float bodyAlpha;
        /** Stars twinkle at night when the sky is clear enough. */
        public final boolean stars;

        Effects(int clouds, boolean greyClouds, int rain, boolean drizzle, int snow, boolean lightning, boolean haze,
                float bodyAlpha, boolean stars) {
            this.clouds = clouds;
            this.greyClouds = greyClouds;
            this.rain = rain;
            this.drizzle = drizzle;
            this.snow = snow;
            this.lightning = lightning;
            this.haze = haze;
            this.bodyAlpha = bodyAlpha;
            this.stars = stars;
        }

        /** Whether anything moves (so the view needs frames), besides the slow sun or moon. */
        public boolean animated() {
            return clouds > 0 || rain > 0 || snow > 0 || lightning || stars;
        }

        /**
         * Whether one cloud stays partly in front of the sun or moon (17 Nov 2026): whenever the weather dims it and
         * has clouds, so the dimming has a cloud to explain it ({@link #veilAt}).
         */
        public boolean veil() {
            return clouds > 0 && bodyAlpha > 0f && bodyAlpha < 1f;
        }

        /** Falling rain or snow needs smooth frames; drifting clouds and stars don't. */
        public boolean fast() {
            return rain > 0 || snow > 0 || lightning;
        }
    }

    /** The effects for a kind of weather; null (weather off, or nothing recent) is a clear sky. */
    public static Effects effects(WeatherCode.Kind kind) {
        if (kind == null) kind = WeatherCode.Kind.CLEAR;
        switch (kind) {
            case PARTLY_CLOUDY:
                return new Effects(3, false, 0, false, 0, false, false, 1f, true);
            case CLOUDY:
                return new Effects(6, false, 0, false, 0, false, false, 0.62f, false);
            case FOG:   // the sun shows at half strength (0.3 before 22 Nov 2026)
                return new Effects(2, false, 0, false, 0, false, true, 0.5f, false);
            case DRIZZLE:
                return new Effects(5, true, 45, true, 0, false, false, 0.3f, false);
            case RAIN:
                return new Effects(6, true, 110, false, 0, false, false, 0.2f, false);
            case SNOW:
                return new Effects(4, true, 0, false, 70, false, false, 0.3f, false);
            case STORM:
                return new Effects(7, true, 140, false, 0, true, false, 0f, false);
            case CLEAR:
            default:
                return new Effects(0, false, 0, false, 0, false, false, 1f, true);
        }
    }

    // ---- Fog haze ---------------------------------------------------------------------------------------------------

    /**
     * The fog haze's vertical gradient (22 Nov 2026): where its stops sit across the sky box (0 top, 1 bottom) and
     * each stop's alpha. It thickens low in the sky and thins out again before the sky's bottom edge, so it never ends
     * in a hard line above the apps band (before, it was thickest right at the edge and looked like a white panel).
     */
    public static final float[] HAZE_STOPS = {0.30f, 0.62f, 0.80f, 1f};
    public static final int[] HAZE_ALPHA = {0, 90, 90, 0};

    // ---- The cloud in front of the sun or moon ----------------------------------------------------------------------

    /** How far the cloud in front of the sun or moon sways either side, in body radii, and how long one sway takes. */
    public static final float VEIL_SWAY = 0.35f;
    public static final long VEIL_SWAY_MS = 40_000L;

    /**
     * Where the cloud in front of the sun or moon goes (its image's top-left), for a body at ({@code x}, {@code y})
     * of radius {@code r} and a cloud image {@code w} × {@code h}: its tallest puff (which starts 6% down the image)
     * a little right of the body's middle and its top just above the centre, so the lower part of the disc is behind
     * cloud and the top still shows. It sways gently sideways with {@code t} (milliseconds), and rides with the body.
     */
    /**
     * The cloud in front of the sun or moon is more see-through than the drifting clouds (22 Nov 2026: at their
     * alpha it hid the dimmed sun), so the sun still reads through it. Alpha 0..255, before the horizon fade.
     */
    public static int veilAlpha(boolean greyClouds) {
        return greyClouds ? 165 : 140;
    }

    public static float[] veilAt(float x, float y, float r, float w, float h, long t) {
        float sway = (float) Math.sin(2 * Math.PI * (t % VEIL_SWAY_MS) / VEIL_SWAY_MS) * VEIL_SWAY * r;
        float middle = x + 0.6f * r + sway;
        float top = y - 0.25f * r - 0.06f * h;
        return new float[]{middle - w / 2f, top};
    }

    // ---- Helpers --------------------------------------------------------------------------------------------------

    private static float fraction(long now, long from, long to) {
        if (to <= from) return 0.5f;
        return Math.max(0f, Math.min(1f, (now - from) / (float) (to - from)));
    }

    private static double mod360(double d) {
        double r = d % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static long julianToMillis(double julian) {
        return Math.round((julian - 2440587.5) * DAY_MS);
    }
}
