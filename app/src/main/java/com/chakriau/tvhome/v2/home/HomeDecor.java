package com.chakriau.tvhome.v2.home;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.eyes.EyeSpots;
import com.chakriau.tvhome.v2.eyes.EyesStore;
import com.chakriau.tvhome.v2.eyes.EyesView;
import com.chakriau.tvhome.v2.festive.BannerView;
import com.chakriau.tvhome.v2.festive.Banners;
import com.chakriau.tvhome.v2.festive.DecorTest;
import com.chakriau.tvhome.v2.festive.DecorTestStore;
import com.chakriau.tvhome.v2.festive.LightsStore;
import com.chakriau.tvhome.v2.festive.LightsView;
import com.chakriau.tvhome.v2.festive.SpecialDayStore;
import com.chakriau.tvhome.v2.festive.SpecialDays;
import com.chakriau.tvhome.v2.sky.SkyState;
import com.chakriau.tvhome.v2.sky.SkyStore;
import com.chakriau.tvhome.v2.sky.SkyView;
import com.chakriau.tvhome.v2.ui.Fonts;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The decorations a theme hangs over its wallpaper and under its page: the sky (5.10), the special day's banner (5.4),
 * festive lights (5.3) and the eyes (5.2). Each is an optional part of Home with its own on/off in Settings › Theme
 * ({@link ThemeParts}); the content (special days, eye personality) is shared. The theme says where they go with a
 * {@link Layout} in design pixels; they never take focus and are animated only while Home is visible.
 *
 * <p>While a test from the phone page's Test tab runs (sky, banner, lights: Requirements.md 5.14), they show the test
 * instead, and a small "Test from the phone" label in the bottom corner says so, so a test is never taken for the
 * real weather or a real special day.
 */
public final class HomeDecor {

    /** Where the decorations go, in 1920 × 1080 design pixels. */
    public static final class Layout {
        /** The banner's box (1200 × 340 in Theme 1). */
        public final HomeKit.Box banner;
        /** Where eyes may sit; they keep clear of the banner's art. */
        public final HomeKit.Box eyesArea;
        /** The eyes' radius at Medium size, and how far apart the two pairs' heights must be. */
        public final int eyeRadius, eyesMinDy;
        /** The sky: the sun and moon rise at its left edge and set at its right; rain falls from top to bottom. */
        public final HomeKit.Box sky;

        public Layout(HomeKit.Box banner, HomeKit.Box eyesArea, int eyeRadius, int eyesMinDy, HomeKit.Box sky) {
            this.banner = banner;
            this.eyesArea = eyesArea;
            this.eyeRadius = eyeRadius;
            this.eyesMinDy = eyesMinDy;
            this.sky = sky;
        }
    }

    private final HomeKit kit;
    private final ThemeParts parts;
    private final Layout layout;
    private final EyesStore eyesStore;
    private final LightsStore lightsStore;
    private final SpecialDayStore specialDays;
    private final SkyStore skyStore;
    private final DecorTestStore decorTests;
    private final Random random = new Random();
    private SkyView sky;
    private String skyKey = "";
    private EyesView eyes;
    private LightsView lights;
    private BannerView banner;
    private boolean bannerShowing, eyesPlaced;
    private String bannerKey = "";
    private TextView testLabel;

    public HomeDecor(HomeKit kit, ThemeParts parts, Layout layout) {
        this.kit = kit;
        this.parts = parts;
        this.layout = layout;
        String themeId = parts.theme().id;
        eyesStore = new EyesStore(kit.activity, themeId);
        lightsStore = new LightsStore(kit.activity, themeId);
        specialDays = new SpecialDayStore(kit.activity);
        skyStore = new SkyStore(kit.activity);
        decorTests = new DecorTestStore(kit.activity);
    }

    /** Adds the sky: right over the wallpaper, under the theme's dimming, so it darkens with the wallpaper. */
    public void addSkyTo(FrameLayout root) {
        sky = new SkyView(kit.activity, kit.scale);
        sky.setVisibility(View.GONE);
        HomeKit.Box s = layout.sky;
        sky.setArea(kit.px(s.left), kit.px(s.top), kit.px(s.right), kit.px(s.bottom));
        root.addView(sky, HomeKit.match());
    }

    /** Adds the eyes, lights and banner (in that order, so the banner draws on top) to the theme's root. */
    public void addTo(FrameLayout root) {
        eyes = new EyesView(kit.activity, kit.px(layout.eyeRadius));
        eyes.setVisibility(View.GONE);
        root.addView(eyes, HomeKit.match());

        lights = new LightsView(kit.activity);
        lights.setVisibility(View.GONE);
        root.addView(lights, HomeKit.match());

        banner = new BannerView(kit.activity);
        banner.setVisibility(View.GONE);
        HomeKit.Box b = layout.banner;
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(kit.px(b.width()), kit.px(b.height()));
        blp.leftMargin = kit.px(b.left);
        blp.topMargin = kit.px(b.top);
        root.addView(banner, blp);

        // "Test from the phone · 12 min left": bottom right, over everything, never scrolling.
        testLabel = kit.text("", 24, HomeKit.TEXT, Fonts.Weight.MEDIUM);
        testLabel.setShadowLayer(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC10131F);
        bg.setStroke(kit.px(2), 0x66FFD34D);
        bg.setCornerRadius(kit.px(22));
        testLabel.setBackground(bg);
        testLabel.setPadding(kit.px(22), kit.px(10), kit.px(22), kit.px(10));
        testLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        tlp.rightMargin = kit.px(40);
        tlp.bottomMargin = kit.px(36);
        root.addView(testLabel, tlp);
    }

    /** Puts the test label in front of everything the theme added after the decorations. */
    public void bringTestLabelToFront() {
        if (testLabel != null) testLabel.bringToFront();
    }

    /** Home became visible, or Settings may have changed something: show what should show, and start it. */
    public void onShown() {
        applySky();
        applyBanner();
        applyEyes();
        applyLights();
        applyTestLabel();
    }

    public void onHidden() {
        if (sky != null) sky.stop();
        eyes.stop();
        lights.stop();
        banner.stop();   // released while Home is hidden (Requirements.md 6.4)
        bannerKey = "";
    }

    /** A new minute: a special day may have started or ended. */
    public void onMinute() {
        applySky();
        applyLights();
        if (applyBanner()) applyEyes();
        applyTestLabel();
    }

    /** The weather on show changed (null: off or nothing recent). */
    public void onWeather(WeatherStore.Reading reading) {
        applySky();
    }

    /** Memory is short and Home is in the background: the sky's cloud images go, and are made again when shown. */
    public void onTrimMemory() {
        if (sky != null) {
            sky.release();
            skyKey = "";
        }
    }

    /**
     * The wallpaper changed: the eyes move to new spots, and the sky learns how bright the photo is behind it
     * ({@code skyLight}: {@code SkyMath.skyLight}, null for the built-in drawing), so the moon stands out on a pale one.
     */
    public void onWallpaperChanged(float[] skyLight) {
        if (sky != null) sky.setLight(skyLight);
        moveEyes(true);
    }

    /** The page scrolled by {@code y} screen pixels: the decorations scroll with it. */
    public void setScroll(int y) {
        if (sky != null) sky.setTranslationY(-y);
        eyes.setTranslationY(-y);
        lights.setTranslationY(-y);
        banner.setTranslationY(-y);
    }

    /** The eyes follow the focused item while the remote moves. */
    public void follow(View focused) {
        if (focused == null || eyes.getVisibility() != View.VISIBLE) return;
        int[] f = new int[2], e = new int[2];
        focused.getLocationInWindow(f);
        eyes.getLocationInWindow(e);
        eyes.lookAt(f[0] - e[0] + focused.getWidth() / 2f, f[1] - e[1] + focused.getHeight() / 2f);
    }

    // ---- Sky ------------------------------------------------------------------------------------------------------

    /** The sun or moon where it is now, and the weather's clouds, rain or snow (or the phone page's test). */
    private void applySky() {
        if (sky == null) return;
        // A test from the phone shows even with the sky off in Settings › Theme, so it can be seen first.
        if (!parts.has(Themes.Part.SKY) || (!parts.on(Themes.Part.SKY) && skyStore.test() == null)) {
            sky.stop();
            sky.setVisibility(View.GONE);
            skyKey = "";
            return;
        }
        SkyState s = skyStore.now();
        if (!s.key().equals(skyKey)) {
            skyKey = s.key();
            sky.setState(s);
        }
        sky.setVisibility(View.VISIBLE);
        sky.start();
    }

    // ---- Festive lights -------------------------------------------------------------------------------------------

    /**
     * Lights on, off, or only on special days that want them (Settings › Theme, Requirements.md 5.3); or as the
     * phone's test says.
     */
    private void applyLights() {
        boolean on = parts.has(Themes.Part.FESTIVE_LIGHTS) && DecorTest.lightsOn(decorTests.test(),
                System.currentTimeMillis(),
                SpecialDays.lightsOn(lightsStore.mode(), LocalDate.now(), specialDays.days()));
        if (on) {
            lights.setVisibility(View.VISIBLE);
            lights.start();
        } else {
            lights.stop();
            lights.setVisibility(View.GONE);
        }
    }

    // ---- Banner ---------------------------------------------------------------------------------------------------

    /**
     * Today's special day's banner, the phone's test banner, or nothing. True if the banner appeared or went, so the
     * eyes need new spots. A test shows even with the banner off in Settings › Theme, so a design can be seen first.
     */
    private boolean applyBanner() {
        Banners.Shown s = null;
        DecorTest test = decorTests.test();
        if (parts.has(Themes.Part.BANNER)) {
            Banners.Shown real = parts.on(Themes.Part.BANNER)
                    ? SpecialDays.banner(LocalDate.now(), specialDays.days()) : null;
            s = DecorTest.banner(test, System.currentTimeMillis(), real);
        }
        String key = s == null ? "" : s.design.id + "|" + s.line1 + "|" + s.line2;
        boolean was = bannerShowing;
        bannerShowing = s != null;
        if (!key.equals(bannerKey)) {
            bannerKey = key;
            banner.show(s);
            banner.setVisibility(s == null ? View.GONE : View.VISIBLE);
            if (s != null) banner.start();
            else banner.stop();
        }
        if (was != bannerShowing) eyesPlaced = false;
        return was != bannerShowing;
    }

    // ---- Test label -----------------------------------------------------------------------------------------------

    /** Shows how long the phone's test has left, or hides the label when none is running. */
    private void applyTestLabel() {
        if (testLabel == null) return;
        long now = System.currentTimeMillis();
        long until = 0;
        SkyState.Test sky = skyStore.test();
        if (sky != null && parts.has(Themes.Part.SKY)) until = sky.until;
        DecorTest decor = decorTests.test();
        if (decor != null && decor.changesAnything()) until = Math.max(until, decor.until);
        if (until <= now) {
            testLabel.setVisibility(View.GONE);
            return;
        }
        long minutes = Math.max(1, (until - now + 59_999) / 60_000);
        testLabel.setText("Test from the phone \u00b7 " + minutes + " min left");
        testLabel.setVisibility(View.VISIBLE);
    }

    // ---- Eyes -----------------------------------------------------------------------------------------------------

    /** Settings › Theme or Eyes may have changed: on or off, the personality and the size. */
    private void applyEyes() {
        if (!parts.shows(Themes.Part.EYES)) {
            eyes.stop();
            eyes.setVisibility(View.GONE);
            return;
        }
        eyes.setPersonality(eyesStore.personality());
        float r = eyeRadius();
        if (r != eyes.radius()) {
            eyes.setRadius(r);
            eyesPlaced = false;   // a new size needs new spots that fit
        }
        if (!eyesPlaced) moveEyes(false);
        eyes.setVisibility(View.VISIBLE);
        eyes.start();
    }

    /**
     * New random spots in the eyes' area, one pair each side, at different heights, clear of the banner's art.
     * Animated: a quick fade out and back in.
     */
    private void moveEyes(boolean animated) {
        HomeKit.Box a = layout.eyesArea, b = layout.banner;
        EyeSpots.Box area = new EyeSpots.Box(kit.px(a.left), kit.px(a.top), kit.px(a.right), kit.px(a.bottom));
        List<EyeSpots.Box> avoid = new ArrayList<>();
        if (bannerShowing) {
            // The banner's art: cloth, diyas, balloons (a little inside its box).
            avoid.add(new EyeSpots.Box(kit.px(b.left + 40), kit.px(b.top + 10), kit.px(b.right - 40),
                    kit.px(b.bottom - 20)));
        }
        float r = eyeRadius();
        float[] s = EyeSpots.pick(random, area, avoid, EyesView.pairWidth(r), EyesView.pairHeight(r),
                kit.px(layout.eyesMinDy));
        eyesPlaced = true;
        if (!animated || eyes.getVisibility() != View.VISIBLE) {
            eyes.animate().cancel();
            eyes.setAlpha(1f);
            eyes.setPairs(s[0], s[1], s[2], s[3]);
            return;
        }
        eyes.animate().cancel();
        eyes.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            eyes.setPairs(s[0], s[1], s[2], s[3]);
            eyes.surprise();
            eyes.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** The theme's eye size, times Settings › Eyes › Size. */
    private float eyeRadius() {
        return kit.px(layout.eyeRadius * eyesStore.size().scale);
    }
}
