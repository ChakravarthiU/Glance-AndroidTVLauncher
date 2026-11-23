package com.chakriau.tvhome.v2.festive;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.FrameLayout;

import com.airbnb.lottie.AsyncUpdates;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.RenderMode;
import com.airbnb.lottie.TextDelegate;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.airbnb.lottie.value.ScaleXY;
import com.chakriau.tvhome.v2.ui.Fonts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays a banner (Requirements.md section 6): one Lottie file from {@code assets/banners/}, looping, with the owner's
 * two lines set on its LINE_1 and LINE_2 text layers. A line too wide for the template's safe zone is scaled down to
 * fit. Playback uses the settings the step 1 probe measured on the Sony TV: hardware rendering, redrawn only at the
 * banner's own 30 fps, async updates off. Nothing is kept between {@link #start} and {@link #stop}: the banner is
 * loaded on start and let go on stop (about 1.6 MB of decoded art), and compositions are not cached.
 */
public final class BannerView extends FrameLayout {

    private static final String TAG = "TvHomeBanner";
    /** The template's text safe zone is 600 px wide (x 300 to 900). */
    private static final float SAFE_W = 600;

    private final Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Typeface> fonts = new HashMap<>();
    private LottieAnimationView lottie;
    private Banners.Shown shown;
    private String loadedAsset;
    private boolean active;

    public BannerView(Context context) {
        super(context);
        setFocusable(false);
        setClipChildren(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Keys are the fName values in the banner files' font lists (design/banners/make_banner.py).
        fonts.put("Inter-Bold", Fonts.get(context, Fonts.Weight.BOLD));
        fonts.put("Inter-Regular", Fonts.get(context, Fonts.Weight.REGULAR));
    }

    /** What to show; null shows nothing. Takes effect at once if playing, else on {@link #start}. */
    public void show(Banners.Shown s) {
        shown = s;
        setContentDescription(s == null ? null : s.line1 + (s.line2.isEmpty() ? "" : ", " + s.line2));
        if (active) apply();
    }

    public void start() {
        active = true;
        apply();
    }

    /** Stops and lets the banner go (Home hidden, or memory short). */
    public void stop() {
        active = false;
        if (lottie != null) {
            lottie.cancelAnimation();
            removeView(lottie);
            lottie = null;
            loadedAsset = null;
        }
    }

    private void apply() {
        if (shown == null) {
            stop();
            active = true;
            return;
        }
        String asset = shown.design.asset();
        if (lottie == null) {
            lottie = new LottieAnimationView(getContext());
            lottie.setFocusable(false);
            lottie.setRenderMode(RenderMode.HARDWARE);
            lottie.setUseCompositionFrameRate(true);
            lottie.setAsyncUpdates(AsyncUpdates.DISABLED);
            lottie.setCacheComposition(false);
            lottie.setRepeatCount(LottieDrawable.INFINITE);
            lottie.setFontMap(fonts);
            lottie.setFailureListener(e -> Log.w(TAG, "banner failed to load: " + e));
            addView(lottie, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        setText();
        if (!asset.equals(loadedAsset)) {
            loadedAsset = asset;
            lottie.setAnimation(asset);   // parsed off the main thread; plays once loaded
        }
        lottie.playAnimation();
    }

    private void setText() {
        String l1 = shown.line1, l2 = shown.line2.toUpperCase(Locale.getDefault());
        TextDelegate text = new TextDelegate(lottie) {
            @Override
            public String getText(String layerName, String input) {
                if ("LINE_1".equals(layerName)) return l1;
                if ("LINE_2".equals(layerName)) return l2;
                return input;
            }
        };
        text.setCacheText(false);
        lottie.setTextDelegate(text);
        fit("LINE_1", l1, fonts.get("Inter-Bold"), 64, 0.02f);
        fit("LINE_2", l2, fonts.get("Inter-Regular"), 26, 0.16f);
    }

    /** Scales a text layer down (around its centred baseline) so the line fits the safe zone. */
    private void fit(String layer, String text, Typeface face, float size, float tracking) {
        measure.setTypeface(face);
        measure.setTextSize(size);
        measure.setLetterSpacing(tracking);
        float s = Banners.fitScale(measure.measureText(text), SAFE_W);
        lottie.addValueCallback(new KeyPath(layer), LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(s, s)));
    }
}
