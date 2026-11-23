package com.chakriau.tvhome.v2.settings;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.chakriau.tvhome.v2.weather.City;
import com.chakriau.tvhome.v2.weather.LocationChoice;
import com.chakriau.tvhome.v2.weather.TimeFormats;
import com.chakriau.tvhome.v2.weather.Weather;
import com.chakriau.tvhome.v2.weather.WeatherClient;
import com.chakriau.tvhome.v2.weather.WeatherCode;
import com.chakriau.tvhome.v2.weather.WeatherSource;
import com.chakriau.tvhome.v2.weather.WeatherStore;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings › Time, date &amp; weather (Requirements.md 5.8, 8): weather on/off, location (automatic, or a city chosen
 * from a search), what is showing and from where, the TV's location permission, °C/°F, the clock face (digital or analog),
 * rolling digits, 12/24-hour clock and the date format. Time, date and time zone themselves come from the TV.
 */
final class TimeWeatherPage implements SettingsPage {

    static final int LOCATION_REQUEST = 71;

    private final Activity activity;
    private final WeatherStore store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Ui ui;
    private View first;
    private View weatherRow, sourceRow, modeRow, cityRow, nowRow, tvRow, unitRow, faceRow, rollRow, clockRow, dateRow;
    private boolean busy;

    TimeWeatherPage(Activity activity) {
        this.activity = activity;
        this.store = new WeatherStore(activity);
    }

    @Override
    public String title() {
        return "Time, date & weather";
    }

    @Override
    public View createView(Ui ui) {
        this.ui = ui;
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(ui.pageHeader("Time, date & weather",
                "The clock, date and weather in the top bar. Time and time zone come from the TV."));

        weatherRow = add(box, ui.toggleRow("Weather", "Temperature and sky in the top bar",
                store::enabled, store::setEnabled, this::refreshValues), 40);
        first = weatherRow;
        WeatherSource[] sources = WeatherSource.values();
        String[] sourceNames = new String[sources.length];
        for (int i = 0; i < sources.length; i++) sourceNames[i] = sources[i].label;
        sourceRow = add(box, ui.choiceRow("Weather from", "", sourceNames, () -> store.provider().ordinal(), i -> {
            if (sources[i] == store.provider()) return;
            if (sources[i] == WeatherSource.OPEN_WEATHER_MAP && store.owmKey() == null) {
                Toast.makeText(activity, "Add your OpenWeatherMap key on the phone page first (Settings tab)",
                        Toast.LENGTH_LONG).show();
                return;
            }
            store.setProvider(sources[i]);
            refreshValues();
            updateNow();
        }, null), 12);
        modeRow = add(box, ui.choiceRow("Location", "Automatic: the TV's location, else its internet address "
                + "(skipped while a VPN is on)", new String[]{"Automatic", "Set by hand"},
                () -> store.automatic() ? 0 : 1, i -> {
                    boolean auto = i == 0;
                    if (auto == store.automatic()) return;
                    if (!auto && store.byHand() == null) {
                        searchCity();   // "set by hand" needs a city first
                        return;
                    }
                    store.setAutomatic(auto);
                    refreshValues();
                    updateNow();
                }, null), 12);
        cityRow = add(box, ui.settingRow("City", "Search for a city. Used when set by hand, or when nothing "
                + "automatic works", "", v -> searchCity()), 12);
        nowRow = add(box, ui.settingRow("Now showing", "", "", v -> updateNow()), 12);
        tvRow = add(box, ui.settingRow("Use the TV's location", "Only if the TV has a location service; most don't",
                "", v -> askLocation()), 12);
        unitRow = add(box, ui.choiceRow("Units", "Temperature in Celsius or Fahrenheit",
                new String[]{"°C", "°F"}, () -> store.fahrenheit() ? 1 : 0, i -> store.setFahrenheit(i == 1),
                this::refreshValues), 12);
        TimeFormats.Face[] faces = TimeFormats.Face.values();
        String[] faceNames = new String[faces.length];
        for (int i = 0; i < faces.length; i++) faceNames[i] = faces[i].label;
        faceRow = add(box, ui.choiceRow("Clock face", "Digital numbers, or an analog dial", faceNames,
                () -> store.clockFace().ordinal(), i -> store.setClockFace(faces[i]), this::refreshValues), 12);
        rollRow = add(box, ui.toggleRow("Rolling digits", "Digits roll up when the time changes (digital face)",
                store::clockRoll, store::setClockRoll, this::refreshValues), 12);
        boolean tv24 = android.text.format.DateFormat.is24HourFormat(activity);
        TimeFormats.Clock[] clocks = TimeFormats.Clock.values();
        String[] clockNames = new String[clocks.length];
        for (int i = 0; i < clocks.length; i++) {
            clockNames[i] = clocks[i] == TimeFormats.Clock.AUTO
                    ? clocks[i].label + (tv24 ? " (24-hour)" : " (12-hour)") : clocks[i].label;
        }
        clockRow = add(box, ui.choiceRow("Clock", "12 or 24 hours (digital face)", clockNames, () -> store.clock().ordinal(),
                i -> store.setClock(clocks[i]), this::refreshValues), 12);
        TimeFormats.DateStyle[] dates = TimeFormats.DateStyle.values();
        String[] dateNames = new String[dates.length];
        for (int i = 0; i < dates.length; i++) {
            dateNames[i] = new SimpleDateFormat(dates[i].pattern, Locale.getDefault()).format(new Date());
        }
        dateRow = add(box, ui.choiceRow("Date format", "Weekday, day and month; never the year", dateNames,
                () -> store.dateStyle().ordinal(), i -> store.setDateStyle(dates[i]), this::refreshValues), 12);

        refreshValues();
        ScrollView scroll = new ScrollView(ui.context);
        scroll.addView(box);
        return scroll;
    }

    private View add(LinearLayout box, View row, int top) {
        box.addView(row, ui.lp(ui.px(1100), Ui.WRAP, ui.px(top)));
        return row;
    }

    private static void set(View row, String value) {
        if (Ui.valueOf(row) != null) Ui.valueOf(row).setText(value);
    }

    /** The note under a row (second text of its text column). */
    private static void note(View row, String text) {
        View texts = ((LinearLayout) row).getChildAt(0);
        if (texts instanceof LinearLayout && ((LinearLayout) texts).getChildCount() > 1) {
            ((android.widget.TextView) ((LinearLayout) texts).getChildAt(1)).setText(text);
        }
    }

    private void refreshValues() {
        if (ui == null) return;
        Ui.refreshAll(weatherRow);
        Ui.refreshAll(sourceRow);
        String key = store.owmKey();
        note(sourceRow, store.provider() == WeatherSource.OPEN_WEATHER_MAP
                ? "OpenWeatherMap, with your key (ends " + WeatherSource.masked(key) + ")"
                : key == null ? "Open-Meteo: free, no key. OpenWeatherMap needs your key, added on the phone page"
                : "Open-Meteo. Your OpenWeatherMap key is saved; choose OpenWeatherMap to use it");
        Ui.refreshAll(modeRow);
        City hand = store.byHand();
        set(cityRow, hand == null ? "Choose…" : hand.name);
        set(tvRow, Weather.locationAllowed(activity) ? "Allowed" : "Not allowed");
        Ui.refreshAll(unitRow);
        Ui.refreshAll(faceRow);
        Ui.refreshAll(rollRow);
        Ui.refreshAll(clockRow);
        Ui.refreshAll(dateRow);

        WeatherStore.Reading r = store.reading();
        if (!store.enabled()) {
            set(nowRow, "Off");
            note(nowRow, "Weather is off");
        } else if (r == null) {
            set(nowRow, busy ? "Looking…" : "Nothing yet");
            note(nowRow, "OK to look now");
        } else {
            set(nowRow, busy ? "Looking…" : WeatherCode.degrees(r.celsius, store.fahrenheit()) + "  " + r.city);
            LocationChoice.Source src = store.source();
            String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.fetchedAt));
            String problem = store.provider() == WeatherSource.OPEN_WEATHER_MAP ? store.providerProblem() : null;
            note(nowRow, (src == null ? "" : "Place from " + src.label + " · ") + WeatherCode.kind(r.code).label
                    + " from " + store.readingProvider().label + " · updated " + when + " · OK to refresh"
                    + (problem == null ? "" : "\n" + problem));
        }
    }

    /** Fetches weather now (Refresh, or after the place changed). */
    private void updateNow() {
        if (busy || !store.enabled()) return;
        busy = true;
        refreshValues();
        worker.execute(() -> {
            WeatherStore.Reading r = Weather.update(activity);
            main.post(() -> {
                busy = false;
                if (activity.isDestroyed()) return;
                refreshValues();
                if (r == null) {
                    Toast.makeText(activity, store.automatic() && store.byHand() == null
                            ? "No weather yet. Check the internet connection, or set the city by hand."
                            : "No weather yet. Check the internet connection.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // ---- City search ----------------------------------------------------------------------------------------------

    private void searchCity() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("City name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = ui.px(40);
        LinearLayout frame = new LinearLayout(activity);
        frame.setPadding(pad, ui.px(10), pad, 0);
        frame.addView(input, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Find a city")
                .setView(frame)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (d, w) -> runSearch(input.getText().toString()))
                .create();
        input.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action == EditorInfo.IME_ACTION_SEARCH || enter) {
                dialog.dismiss();
                runSearch(input.getText().toString());
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    private void runSearch(String query) {
        String q = query.trim();
        if (q.length() < 2) {
            Toast.makeText(activity, "Type at least two letters", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, "Searching for " + q + "…", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            List<City> found = WeatherClient.search(q);
            main.post(() -> {
                if (activity.isDestroyed()) return;
                if (found.isEmpty()) {
                    Toast.makeText(activity, "No city called " + q + " found (or no internet)",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < found.size(); i++) {
                    City c = found.get(i);
                    names[i] = c.detail.isEmpty() ? c.name : c.name + " — " + c.detail;
                }
                new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Choose " + q)
                        .setItems(names, (d, which) -> {
                            store.setByHand(found.get(which));
                            store.setAutomatic(false);   // choosing a city means "use this one"
                            refreshValues();
                            updateNow();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    // ---- TV location ----------------------------------------------------------------------------------------------

    private void askLocation() {
        if (Weather.locationAllowed(activity)) {
            Toast.makeText(activity, "Already allowed. It can be turned off in the TV's Apps settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
    }

    /** The permission dialog closed (SettingsActivity passes it on). */
    void onLocationAnswer() {
        refreshValues();
        if (Weather.locationAllowed(activity) && store.automatic()) {
            store.clearReading();
            updateNow();
        }
    }

    // ---- SettingsPage ---------------------------------------------------------------------------------------------

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
        refreshValues();
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
    }
}
