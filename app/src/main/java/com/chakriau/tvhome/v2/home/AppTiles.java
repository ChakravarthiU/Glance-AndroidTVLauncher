package com.chakriau.tvhome.v2.home;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chakriau.tvhome.v2.apps.AppArt;
import com.chakriau.tvhome.v2.apps.AppInfo;
import com.chakriau.tvhome.v2.ui.Fonts;

import java.util.HashMap;
import java.util.Map;

/**
 * Theme 1's app tile (Requirements.md 5.5): the app's TV banner filling a rounded tile, or for apps with only an
 * icon, the icon over the label. Focused, it grows, lifts, and gets a border turning from the app's own colour to
 * white with sparkles bursting from its centre ({@link FocusFx}, drawn in a layer bigger than the tile so the rounded
 * corners don't cut them off). OK opens the app, hold OK shows App info, both through the shared
 * {@link HomeTheme.Actions}. A tile's tag is its package name.
 */
public final class AppTiles {

    private static final int TILE_TOP = Color.rgb(52, 64, 110);
    private static final int TILE_BG = Color.rgb(36, 46, 82);

    private final HomeKit kit;
    private final AppArt art;
    /** Each app's own colour, from its banner, for the focus border. */
    private final Map<String, Integer> colors = new HashMap<>();
    private FocusFx focusedFx;

    public AppTiles(HomeKit kit, AppArt art) {
        this.kit = kit;
        this.art = art;
    }

    /**
     * One tile, {@code w} × {@code h} screen pixels; {@code labelPx} is the label size in design pixels and
     * {@code grow} how much the focused tile grows (1.2 = 20%).
     */
    public View tile(AppInfo app, int w, int h, float labelPx, float grow) {
        FrameLayout t = new FrameLayout(kit.activity);
        t.setFocusable(true);
        t.setTag(app.packageName);
        t.setClipChildren(false);
        t.setClipToPadding(false);

        FrameLayout face = new FrameLayout(kit.activity);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{TILE_TOP, TILE_BG});
        bg.setCornerRadius(kit.px(18));
        face.setBackground(bg);
        face.setClipToOutline(true);
        face.setElevation(kit.px(6));
        t.addView(face, HomeKit.match());

        ImageView image = new ImageView(kit.activity);
        face.addView(image, HomeKit.match());
        TextView label = kit.text("", labelPx, HomeKit.TEXT, Fonts.Weight.MEDIUM);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(kit.px(12), 0, kit.px(12), 0);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        llp.bottomMargin = Math.round(h * 0.14f);
        face.addView(label, llp);

        FocusFx fx = new FocusFx(kit.activity, 0, kit.px(18), Math.max(2, kit.px(5)));
        Integer known = colors.get(app.packageName);
        if (known != null) fx.setColor(known);
        t.addView(fx, HomeKit.match());
        // In front of the tile's face: views with a higher elevation draw on top, whatever their order.
        fx.setElevation(kit.px(12));

        art.load(app, image, a -> {
            image.setImageDrawable(a.drawable);
            if (a.isBanner) {
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setPadding(0, 0, 0, 0);
                label.setText("");
            } else {
                // Icon in the upper part, the label under it.
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int side = Math.round(h * 0.18f);
                image.setPadding(side, Math.round(h * 0.14f), side, Math.round(h * 0.42f));
                label.setText(app.label);
            }
            if (!colors.containsKey(app.packageName) && a.drawable != null) {
                colors.put(app.packageName, FocusFx.colorOf(a.drawable));
            }
            Integer c = colors.get(app.packageName);
            if (c != null) fx.setColor(c);
        });

        t.setOnFocusChangeListener((v, has) -> {
            fx.setFocused(has);
            if (has) focusedFx = fx;
            else if (focusedFx == fx) focusedFx = null;
            v.animate().scaleX(has ? grow : 1f).scaleY(has ? grow : 1f)
                    .translationZ(has ? kit.px(16) : 0).setDuration(160).start();
        });
        t.setOnClickListener(v -> kit.actions.open(app));
        t.setOnLongClickListener(v -> {
            kit.actions.showAppInfo(app);
            return true;
        });
        t.setContentDescription(app.label);
        return t;
    }

    /** Home is visible again: the focused tile's highlight carries on. */
    public void onShown() {
        FocusFx.setPaused(false);
        if (focusedFx != null) focusedFx.invalidate();
    }

    public void onHidden() {
        FocusFx.setPaused(true);
    }
}
