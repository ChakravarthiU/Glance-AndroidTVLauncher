package com.chakriau.tvhome.v2.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.system.Inputs;
import com.chakriau.tvhome.v2.system.NetworkStatus;
import com.chakriau.tvhome.v2.system.TopBar;
import com.chakriau.tvhome.v2.system.TopBarStore;
import com.chakriau.tvhome.v2.ui.Fonts;

/**
 * The right of Theme 1's top bar (Requirements.md 5.9): Inputs, Network and Settings as glass pills, styled as
 * icons, text or both (the theme's top-button style). Inputs shows only when the TV has sources; Network follows
 * the connection live while Home is visible; a press on Settings opens the TV's settings or the launcher's (Settings
 * › System), a hold the other.
 */
public final class TopButtons {

    private final HomeKit kit;
    private final ThemeParts parts;
    private final TopBarStore store;
    private final InputsMenu inputsMenu;
    private final Runnable networkUpdate = this::updateNetwork;
    private Pill inputsPill, networkPill, settingsPill;
    private Icons.Wifi wifiGlyph;
    private Icons.Ethernet ethernetGlyph;
    private ConnectivityManager.NetworkCallback networkCallback;
    private TvInputManager.TvInputCallback inputCallback;

    public TopButtons(HomeKit kit, ThemeParts parts) {
        this.kit = kit;
        this.parts = parts;
        this.store = new TopBarStore(kit.activity);
        this.inputsMenu = new InputsMenu(kit);
    }

    /** Adds the three buttons, 80 px high with 20 px between them, to the end of a horizontal bar. */
    public void addTo(LinearLayout bar) {
        inputsPill = new Pill("Inputs", new Icons.Monitor(kit.px(34), HomeKit.TEXT, false));
        inputsPill.view.setOnClickListener(inputsMenu::show);
        inputsPill.view.setVisibility(View.GONE);   // shown once the TV is known to have inputs
        wifiGlyph = new Icons.Wifi(kit.px(34), HomeKit.TEXT);
        ethernetGlyph = new Icons.Ethernet(kit.px(34), HomeKit.TEXT);
        networkPill = new Pill("Wi-Fi", wifiGlyph);
        networkPill.label.setMaxWidth(kit.px(300));
        networkPill.view.setOnClickListener(v -> kit.actions.openNetworkSettings());
        settingsPill = new Pill("Settings", new Icons.Gear(kit.px(34), HomeKit.TEXT));
        settingsPill.view.setOnClickListener(v -> openSettings(false));
        settingsPill.view.setOnLongClickListener(v -> {
            openSettings(true);
            return true;
        });
        for (Pill pill : new Pill[]{inputsPill, networkPill, settingsPill}) {
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    kit.px(80));
            if (pill != inputsPill) plp.leftMargin = kit.px(20);
            bar.addView(pill.view, plp);
        }
        applyStyle();
    }

    /** The Settings button: where focus starts while the Apps bar is empty (the way to fill it). */
    public View settingsButton() {
        return settingsPill.view;
    }

    public void onShown() {
        applyStyle();   // Settings › Theme may have changed it
        updateInputsButton();
        updateNetwork();
        startWatching();
    }

    public void onHidden() {
        stopWatching();
        inputsMenu.dismiss();
    }

    public void onDestroy() {
        inputsMenu.dismiss();
        stopWatching();
    }

    private void openSettings(boolean hold) {
        TopBar.SettingsPress press = store.settingsPress();
        if (hold) press = press.other();
        if (press == TopBar.SettingsPress.TV) kit.actions.openSystemSettings();
        else kit.actions.openSettings();
    }

    private void applyStyle() {
        TopBar.Style st = parts.topButtonStyle();
        for (Pill p : new Pill[]{inputsPill, networkPill, settingsPill}) p.applyStyle(st);
        settingsPill.setText("Settings");   // refreshes the hold hint too
    }

    /** Reads the network off the main thread (the Wi-Fi details can take a few milliseconds on a TV). */
    private void updateNetwork() {
        kit.worker.execute(() -> {
            NetworkStatus st = NetworkStatus.read(kit.activity);
            kit.main.post(() -> {
                if (kit.activity.isDestroyed()) return;
                networkPill.setGlyph(st.kind == NetworkStatus.Kind.ETHERNET ? ethernetGlyph : wifiGlyph);
                wifiGlyph.set(st.level, st.kind == NetworkStatus.Kind.OFFLINE);
                networkPill.setText(st.label);
            });
        });
    }

    private void updateInputsButton() {
        boolean any = Inputs.any(kit.activity);
        boolean wasFocused = inputsPill.view.hasFocus();
        inputsPill.view.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any && wasFocused) networkPill.view.requestFocus();
    }

    /** Listens for network and input changes while Home is visible. */
    private void startWatching() {
        if (networkCallback == null) {
            ConnectivityManager cm = (ConnectivityManager) kit.activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network n) {
                        later();
                    }

                    @Override
                    public void onLost(Network n) {
                        later();
                    }

                    @Override
                    public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                        later();
                    }

                    private void later() {
                        // Signal changes come often: one update half a second after the last.
                        kit.main.removeCallbacks(networkUpdate);
                        kit.main.postDelayed(networkUpdate, 500);
                    }
                };
                try {
                    cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkCallback);
                } catch (RuntimeException e) {
                    networkCallback = null;
                }
            }
        }
        if (inputCallback == null) {
            TvInputManager tim = (TvInputManager) kit.activity.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                inputCallback = new TvInputManager.TvInputCallback() {
                    @Override
                    public void onInputAdded(String id) {
                        updateInputsButton();
                    }

                    @Override
                    public void onInputRemoved(String id) {
                        updateInputsButton();
                    }
                };
                tim.registerCallback(inputCallback, kit.main);
            }
        }
    }

    private void stopWatching() {
        kit.main.removeCallbacks(networkUpdate);
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) kit.activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            try {
                if (cm != null) cm.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            networkCallback = null;
        }
        if (inputCallback != null) {
            TvInputManager tim = (TvInputManager) kit.activity.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) tim.unregisterCallback(inputCallback);
            inputCallback = null;
        }
    }

    /** A glass pill: an icon, a label, or both. Focused, it turns white with dark content and grows 6%. */
    private final class Pill {
        final LinearLayout view;
        final ImageView icon;
        final TextView label;
        private final GradientDrawable bg = new GradientDrawable();
        private Icons.Glyph glyph;

        Pill(String text, Icons.Glyph glyph) {
            view = new LinearLayout(kit.activity);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER);
            view.setFocusable(true);
            bg.setCornerRadius(kit.px(40));
            view.setBackground(bg);
            icon = new ImageView(kit.activity);
            view.addView(icon, new LinearLayout.LayoutParams(kit.px(34), kit.px(34)));
            label = kit.text("", 31, HomeKit.TEXT, Fonts.Weight.MEDIUM);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            view.addView(label);
            setGlyph(glyph);
            setText(text);
            style(false);
            view.setOnFocusChangeListener((v, has) -> style(has));
        }

        void setGlyph(Icons.Glyph g) {
            glyph = g;
            icon.setImageDrawable(g);
            style(view.hasFocus());
        }

        void setText(String t) {
            label.setText(t);
            view.setContentDescription(this == settingsPill ? t + ". " + store.settingsPress().holdHint : t);
        }

        void applyStyle(TopBar.Style st) {
            icon.setVisibility(st.icon() ? View.VISIBLE : View.GONE);
            label.setVisibility(st.text() ? View.VISIBLE : View.GONE);
            label.setPadding(st.icon() && st.text() ? kit.px(16) : 0, 0, 0, 0);
            if (st.text()) view.setPadding(kit.px(st.icon() ? 28 : 32), 0, kit.px(32), 0);
            else view.setPadding(kit.px(23), 0, kit.px(23), 0);   // icon only: a round button
            view.setMinimumWidth(st.text() ? 0 : kit.px(80));
        }

        void style(boolean focused) {
            bg.setColor(focused ? Color.WHITE : Color.argb(40, 255, 255, 255));
            bg.setStroke(kit.line(2), focused ? Color.WHITE : Color.argb(70, 255, 255, 255));
            int fg = focused ? HomeKit.TEXT_ON_WHITE : HomeKit.TEXT;
            if (glyph != null) glyph.setColor(fg);
            label.setTextColor(fg);
            label.setShadowLayer(focused ? 0 : kit.px(6), 0, kit.px(1), Color.argb(90, 0, 0, 0));
            view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
        }
    }
}
