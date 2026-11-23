package com.chakriau.tvhome.v2.settings;

import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.wallpapers.NetworkAddress;
import com.chakriau.tvhome.v2.wallpapers.QrCode;
import com.chakriau.tvhome.v2.wallpapers.UploadServer;
import com.chakriau.tvhome.v2.wallpapers.WallpaperStore;

/**
 * Settings › Phone, the first page: one QR code that opens the phone page, where every setting can be changed, photos
 * added, special days set (banner, lights and wallpaper for each) and apps arranged. The page is served
 * while Settings is open ({@link SettingsActivity} runs the server); the PIN in the code unlocks it.
 */
final class PhonePage implements SettingsPage {

    /** What the page needs from Settings: whether the phone page is being served, and a new PIN. */
    interface Host {
        boolean phoneServerRunning();

        void setPin(String pin);
    }

    private static final int QR = 380;

    private final SettingsActivity activity;
    private final Host host;
    private final WallpaperStore pins;
    private ImageView qr;
    private TextView address, pinText;
    private View first;

    PhonePage(SettingsActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.pins = new WallpaperStore(activity);
    }

    @Override
    public String title() {
        return "Phone";
    }

    @Override
    public View createView(Ui ui) {
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Phone", "Set everything up from your phone: settings, wallpaper photos, special "
                + "days and apps."));

        LinearLayout card = new LinearLayout(ui.context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(ui.px(36), ui.px(36), ui.px(36), ui.px(36));
        card.setBackground(ui.round(Ui.CARD, 22));
        FrameLayout qrBox = new FrameLayout(ui.context);
        qrBox.setBackground(ui.round(Color.WHITE, 16));
        qrBox.setPadding(ui.px(16), ui.px(16), ui.px(16), ui.px(16));
        qr = new ImageView(ui.context);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrBox.addView(qr, new FrameLayout.LayoutParams(ui.px(QR), ui.px(QR)));
        card.addView(qrBox, ui.lp(Ui.WRAP, Ui.WRAP));

        LinearLayout texts = new LinearLayout(ui.context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(ui.px(44), ui.px(10), 0, 0);
        texts.addView(ui.text("Scan with the phone's camera", 36, Ui.TEXT, true));
        texts.addView(ui.text("The phone must be on the same Wi-Fi as the TV.", 24, Ui.TEXT_2, false),
                ui.lp(Ui.WRAP, Ui.WRAP, ui.px(16)));
        texts.addView(ui.caps("Or open", 20, Ui.TEXT_3), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(40)));
        address = ui.text("", 30, Ui.TEXT, false);
        texts.addView(address, ui.lp(Ui.WRAP, Ui.WRAP, ui.px(10)));
        texts.addView(ui.caps("PIN", 20, Ui.TEXT_3), ui.lp(Ui.WRAP, Ui.WRAP, ui.px(32)));
        pinText = ui.text("", 48, Ui.TEXT, true);
        pinText.setLetterSpacing(0.14f);
        texts.addView(pinText, ui.lp(Ui.WRAP, Ui.WRAP, ui.px(8)));
        texts.addView(ui.text("The phone page works while the launcher's Settings is open.", 22, Ui.TEXT_3, false),
                ui.lp(Ui.WRAP, Ui.WRAP, ui.px(32)));
        card.addView(texts, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        box.addView(card, ui.lp(ui.px(1180), Ui.WRAP, ui.px(40)));

        first = ui.settingRow("Change PIN", "The phone will ask for the new PIN; five wrong PINs lock the page for "
                + "5 minutes", null, v -> changePin());
        box.addView(first, ui.lp(ui.px(1180), Ui.WRAP, ui.px(16)));

        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        this.ui = ui;
        show();
        return scroll;
    }

    private Ui ui;

    private void show() {
        if (qr == null) return;
        String ip = NetworkAddress.lanIPv4();
        String pin = pins.pin();
        pinText.setText(pin);
        if (ip == null || !host.phoneServerRunning()) {
            qr.setImageBitmap(null);
            address.setText(ip == null ? "Connect the TV to Wi-Fi or Ethernet first."
                    : "The phone page could not start. Close Settings and open it again.");
            return;
        }
        String url = "http://" + ip + ":" + UploadServer.PORT + "/";
        address.setText(url);
        // The PIN rides after '#': the phone page fills it in, and it is never sent over the network in a URL.
        qr.setImageBitmap(QrCode.make(url + "#pin=" + pin, ui.px(QR)));
    }

    private void changePin() {
        String pin = pins.newPin();
        host.setPin(pin);
        show();
        Toast.makeText(activity, "New PIN: " + pin + ". Scan the code again on the phone.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean focusFirst() {
        return first != null && first.requestFocus();
    }

    @Override
    public boolean onBack() {
        return false;
    }

    @Override
    public void onShown() {
        show();
    }

    @Override
    public void onDestroy() {
    }
}
