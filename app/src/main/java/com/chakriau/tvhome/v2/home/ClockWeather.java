package com.chakriau.tvhome.v2.home;

import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chakriau.tvhome.v2.ui.Fonts;
import com.chakriau.tvhome.v2.weather.TimeFormats;
import com.chakriau.tvhome.v2.weather.Weather;
import com.chakriau.tvhome.v2.weather.WeatherCode;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The left of Theme 1's top bar (Requirements.md 5.8): the time (12 or 24 hours; digital with rolling digits, or an
 * analog dial), a divider, the date without the year, and the weather (outline icon, temperature, city). Fetches new weather at most hourly, only while Home is
 * visible; a failed fetch is tried again after 5 minutes, and weather older than 3 hours is hidden. The time and date
 * stay hidden until the TV's clock is set ({@link TimeFormats#clockSet}); the TV's time-changed broadcast
 * ({@link MinuteTicker}) shows them as soon as it is.
 *
 * <p>Sizes are Theme 1's (mockup {@code design/theme1-home.png}); another theme can reuse it as it is or read
 * {@link #cornerText()} and {@link #reading()} to draw the same information its own way.
 */
public final class ClockWeather {

    /** Told when the clock or the weather on show changes (for a corner clock, or the sky). */
    public interface Listener {
        void changed(boolean weatherChanged);
    }

    private final HomeKit kit;
    private final WeatherStore store;
    private final Listener listener;
    private final Runnable refreshTick = this::refresh;
    private RollingText clock, ampm;
    private LinearLayout digital;
    private AnalogClock analog;
    private View divider;
    private TextView date;
    private LinearLayout weatherBox;
    private Icons.Weather weatherIcon;
    private TextView temperature, city;
    private String weatherText = "";
    private String cornerText = "";
    private WeatherStore.Reading shown;
    private boolean loading, visible;

    /** The analog dial's diameter, design pixels (the digital time is about this tall). */
    static final int ANALOG_SIZE = 136;

    public ClockWeather(HomeKit kit, Listener listener) {
        this.kit = kit;
        this.store = new WeatherStore(kit.activity);
        this.listener = listener;
    }

    /** Adds the time, divider, date and weather to a horizontal bar; the weather takes the room left over. */
    public void addTo(LinearLayout bar) {
        // Digital: time and AM/PM whose changed characters roll up (RollingText), baseline-aligned as before.
        digital = new LinearLayout(kit.activity);
        digital.setOrientation(LinearLayout.HORIZONTAL);
        digital.setBaselineAligned(true);
        clock = rolling(128, Fonts.Weight.LIGHT);
        clock.setLetterSpacing(-0.02f);
        clock.setFixedDigits(2);   // the minutes sit in equal slots, so AM/PM doesn't shift as they change
        ampm = rolling(40, Fonts.Weight.REGULAR);
        String[] amPm = new java.text.DateFormatSymbols(Locale.getDefault()).getAmPmStrings();
        ampm.reserve(amPm[0].toUpperCase(Locale.getDefault()), amPm[1].toUpperCase(Locale.getDefault()));
        ampm.setPadding(kit.px(12), 0, 0, 0);
        digital.addView(clock);
        digital.addView(ampm);
        bar.addView(digital);

        // Analog: a glass dial the height of the digits, shown instead when chosen in Settings.
        analog = new AnalogClock(kit.activity, kit.scale);
        analog.setVisibility(View.GONE);
        bar.addView(analog, new LinearLayout.LayoutParams(kit.px(ANALOG_SIZE), kit.px(ANALOG_SIZE)));

        divider = new View(kit.activity);
        divider.setBackgroundColor(Color.argb(90, 255, 255, 255));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(kit.line(2), kit.px(72));
        dlp.leftMargin = kit.px(32);
        dlp.rightMargin = kit.px(32);
        dlp.topMargin = kit.px(16);
        bar.addView(divider, dlp);

        date = kit.text("", 36, HomeKit.TEXT, Fonts.Weight.REGULAR);
        LinearLayout.LayoutParams datelp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        datelp.topMargin = kit.px(22);
        bar.addView(date, datelp);

        // Weather: icon, temperature and city. Hidden when off or nothing recent is known.
        weatherBox = new LinearLayout(kit.activity);
        weatherBox.setOrientation(LinearLayout.HORIZONTAL);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new Icons.Weather(kit.px(48), HomeKit.TEXT);
        ImageView icon = new ImageView(kit.activity);
        icon.setImageDrawable(weatherIcon);
        weatherBox.addView(icon, new LinearLayout.LayoutParams(kit.px(48), kit.px(48)));
        temperature = kit.text("", 54, HomeKit.TEXT, Fonts.Weight.LIGHT);
        temperature.setPadding(kit.px(16), 0, kit.px(14), 0);
        weatherBox.addView(temperature);
        city = kit.text("", 34, HomeKit.TEXT, Fonts.Weight.REGULAR);
        city.setSingleLine(true);
        city.setEllipsize(TextUtils.TruncateAt.END);
        city.setMaxWidth(kit.px(320));
        weatherBox.addView(city);
        weatherBox.setVisibility(View.GONE);
        // The weather takes the room left between the date and the buttons; a long city name is cut short there.
        FrameLayout weatherSlot = new FrameLayout(kit.activity);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        wlp.leftMargin = kit.px(40);
        wlp.topMargin = kit.px(14);
        weatherSlot.addView(weatherBox, wlp);
        bar.addView(weatherSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        updateClock(false);
    }

    private RollingText rolling(float sizePx, Fonts.Weight weight) {
        return new RollingText(kit.activity, kit.px(sizePx), HomeKit.TEXT, Fonts.get(kit.activity, weight),
                kit.px(6), kit.px(1));
    }

    /** "24° Chennai     9:41 PM": what the small clock shows when Home is scrolled down. */
    public String cornerText() {
        return cornerText;
    }

    /** The weather on show, or null when it is off or nothing recent is known. */
    public WeatherStore.Reading reading() {
        return shown;
    }

    public void onShown() {
        visible = true;
        showWeather();
        refresh();
        updateClock(false);   // the time may have jumped while hidden: show it, don't roll to it
        if (analog != null) analog.start();
    }

    public void onHidden() {
        visible = false;
        kit.main.removeCallbacks(refreshTick);
        if (analog != null) analog.stop();
        if (clock != null) {
            clock.finish();
            ampm.finish();
        }
    }

    /** Each minute (and on a time-zone change): the digits roll, or the hands click forward, while Home shows. */
    public void onMinute() {
        updateClock(visible);
    }

    public void onDestroy() {
        kit.main.removeCallbacks(refreshTick);
        if (analog != null) analog.stop();
    }

    /** Shows the saved reading, if weather is on and it is recent (no weather rather than stale weather). */
    private void showWeather() {
        WeatherStore.Reading r = store.enabled() ? store.reading() : null;
        if (r == null || !TimeFormats.showable(r.fetchedAt, System.currentTimeMillis())) {
            weatherBox.setVisibility(View.GONE);
            weatherText = "";
            shown = null;
        } else {
            WeatherCode.Kind kind = WeatherCode.kind(r.code);
            weatherIcon.set(kind, r.day);
            String deg = WeatherCode.degrees(r.celsius, store.fahrenheit());
            temperature.setText(deg);
            city.setText(r.city);
            weatherBox.setContentDescription(deg + " " + kind.label + " in " + r.city);
            weatherBox.setVisibility(View.VISIBLE);
            weatherText = deg + " " + r.city;
            shown = r;
        }
        updateClock(false);
        listener.changed(true);
    }

    /** Fetches new weather when the hourly refresh is due; runs only while Home is visible. */
    private void refresh() {
        kit.main.removeCallbacks(refreshTick);
        if (!store.enabled()) return;
        WeatherStore.Reading r = store.reading();
        long now = System.currentTimeMillis();
        long fetchedAt = r == null ? 0 : r.fetchedAt;
        if (TimeFormats.refreshDue(fetchedAt, now) && !loading) {
            loading = true;
            kit.worker.execute(() -> {
                WeatherStore.Reading fresh = Weather.update(kit.activity);
                kit.main.post(() -> {
                    loading = false;
                    if (kit.activity.isDestroyed() || !visible) return;
                    showWeather();
                    // Success: next refresh in an hour. Failure: try again in a few minutes.
                    kit.main.postDelayed(refreshTick, fresh != null ? TimeFormats.REFRESH_MS : 5 * 60_000L);
                });
            });
            return;
        }
        kit.main.postDelayed(refreshTick, TimeFormats.untilRefresh(fetchedAt, now));
    }

    /** @param animate roll the digits / move the hands to the new time (only while Home shows). */
    private void updateClock(boolean animate) {
        if (clock == null) return;
        Date now = new Date();
        TimeFormats.Face face = store.clockFace();
        if (!TimeFormats.clockSet(now.getTime())) {
            // Not set yet (no clock battery, no internet since power-on): keep the places, show nothing wrong.
            digital.setVisibility(face.analog() ? View.GONE : View.INVISIBLE);
            analog.setVisibility(face.analog() ? View.INVISIBLE : View.GONE);
            divider.setVisibility(View.INVISIBLE);
            date.setVisibility(View.INVISIBLE);
            cornerText = weatherText;
            listener.changed(false);
            return;
        }
        divider.setVisibility(View.VISIBLE);
        date.setVisibility(View.VISIBLE);
        boolean h24 = store.clock().is24(DateFormat.is24HourFormat(kit.activity));
        Locale locale = Locale.getDefault();
        boolean roll = animate && store.clockRoll() && !face.analog();
        String t = new SimpleDateFormat(h24 ? "H:mm" : "h:mm", locale).format(now);
        String a = h24 ? "" : new SimpleDateFormat("a", locale).format(now).toUpperCase(locale);
        clock.setText(t, roll);
        ampm.setText(a, roll);
        ampm.setVisibility(h24 ? View.GONE : View.VISIBLE);
        digital.setVisibility(face.analog() ? View.GONE : View.VISIBLE);
        analog.setVisibility(face.analog() ? View.VISIBLE : View.GONE);
        analog.setSeconds(face.seconds());
        if (face.analog()) analog.update(animate);
        date.setText(new SimpleDateFormat(store.dateStyle().pattern, locale).format(now));   // no year
        String time = t + (a.isEmpty() ? "" : " " + a);
        cornerText = weatherText.isEmpty() ? time : weatherText + "     " + time;
        listener.changed(false);
    }
}
