# TV Home V2 — Developer Notes

Everything needed to understand, change or rebuild Launcher V2 and its Theme 1, written so an AI agent (or a person)
can start from this file alone. [`Requirements.md`](Requirements.md) says **what** the launcher does and why; this
file says **how** it is built. When they disagree, the code wins, then Requirements.md; fix the other two.

Contents: 1 Start here · 2 Rules of the house · 3 Build, test and install · 4 Repository map · 5 Architecture ·
6 Themes and how to add one · 7 Theme 1 spec · 8 Settings on the TV · 9 Phone page and API · 10 Storage ·
11 Backup and restore · 12 Safe mode · 13 Sky · 14 Banners · 15 Testing · 16 Checklist for a change ·
17 Weather sources · 18 Home preview and phone tests

---

## 1. Start here

- **What it is:** an Android TV launcher (the Home screen app) in plain Java, package `com.chakriau.tvhome.v2`,
  for a Sony BRAVIA (Android 9, about 2 GB RAM, budgeted as 1 GB). It installs next to the earlier launcher
  (V1, package `com.chakriau.tvhome`), which can stay installed as a fallback.
- **The look is a theme.** Home's look is a `HomeTheme` class picked in Settings; Theme 1 (`home/Theme1Home`) is the
  only one so far. Everything a theme draws is built from shared "part" classes, so a second theme mostly re-arranges
  them (section 6).
- **Everything is visible.** Every feature has an on/off or a value in the launcher's own Settings and on the phone
  page. There are no hidden config files.
- **The phone page** (served by the TV on port 8766 while Settings is open, reached by a QR code) is where photos,
  special days, backups and tests of the sky, banner and lights on Home (section 18) are managed.
- **Read next:** section 5 for the code map, section 7 if you are drawing anything, section 10 before touching saved
  data.

## 2. Rules of the house

1. **Java 17, no Kotlin.** Min SDK 26 (Android 8), target/compile 34. Anything newer than API 26 needs a version
   check or must be avoided (e.g. `PackageInfo.versionCode`, not `getLongVersionCode()`; the release build runs lint).
2. **Design pixels.** Home and Settings are drawn in a 1920 × 1080 design and scaled with `px(designPx)`
   (`HomeKit.px`, `settings/Ui.px`). All sizes in this file are design pixels.
3. **Built for a 1 GB TV.** Decode images at display size, never full size. Animate only while Home is visible
   (`onShown` / `onHidden`), and only when something moves. Free what can be rebuilt in `onTrimMemory`. No blur
   while animating (blur is fine once, into a bitmap).
4. **A theme changes how things look, never what works.** Opening apps, App info, inputs, Back and the Home key go
   through `HomeTheme.Actions` (implemented by `MainActivity`). Settings data is shared by every theme.
5. **Logic in plain Java, tested.** Ordering, dates, scheduling, maths, formats: put them in classes with no Android
   types so `src/test` can run them on the JVM (the Android jar is stubs only there, so tests can't use `org.json`
   or views). Android classes stay thin.
6. **One file per concern; keep `MainActivity` small.** V1's 3,700-line `MainActivity` is what to avoid.
7. **Comments say why, and point to Requirements.md sections** (`Requirements.md 5.7`). User-facing text is plain,
   short English ("Hold OK on Settings…"), sentence case, no jargon.
8. **Never sign with the debug key.** Builds that install on the TV come only from CI with the fixed release key.
9. **Settings keys are forever.** Never rename a saved key or file; add a migration if a meaning changes
   (`ThemeMigration`, `SpecialDayMigration` are the examples).

## 3. Build, test and install

| What | How |
| --- | --- |
| Unit tests + release APK (unsigned locally) | `./gradlew test assembleRelease` in the launcher folder (`.\gradlew.bat` on Windows) |
| Signed APK | GitHub Actions › **V2 release** on every push to `main` that changes the launcher (not `design/` or `*.md`); published as `tvhome-v2.apk` on the rolling GitHub Release **v2-latest** (replaced each build; a Release, because release files don't count against the Actions storage quota) |
| Branch check | GitHub Actions › **V2 check** on every other branch and PR: tests + unsigned R8 build, errors shown as annotations |
| Install on the TV | `.\Scripts\Install-Package.ps1 -TvHost <tv ip>` (newest V2 release build of the repository it is run from; `-Repo`, `-Apk file`, `-SetHome`, `-RollbackToV1`, `-OpenSettings`, `-Reinstall`) |
| Signing key | `.\Scripts\Setup-Signing.ps1`, once per repository; key in `%USERPROFILE%\.android\tvhome-v2-release.jks` and GitHub Secrets |
| Screenshots | `adb shell screencap -p /sdcard/tv.png` then `adb pull` (Requirements.md 9) |

`versionCode` is the CI run number, so every CI build installs over the last (`adb install -r`) and keeps its data.

**Without the Android SDK** (an agent's sandbox): the Java sources compile with plain `javac` against an
`android.jar` (API 34) plus tiny stubs for Lottie, RecyclerView and ZXing, and the JUnit tests then run with
`org.junit.runner.JUnitCore`. That catches compile errors and test failures; the V2 check workflow is the real
build. Libraries: `com.airbnb.android:lottie:6.5.2`, `androidx.recyclerview:recyclerview:1.3.2`,
`com.google.zxing:core:3.5.3`, `junit:junit:4.13.2`.

## 4. Repository map

The launcher folder is self-contained, so it can be the root of its own repository:

```
README.md                 build, set up and install
AGENTS.md                 short notes for AI agents
LICENSE                   MIT (the Inter fonts: OFL)
Documents/
  Requirements.md         what V2 does and the decisions made
  DeveloperNotes.md       this file
Scripts/
  Install-Package.ps1     download + install the newest CI build on the TV
  Setup-Signing.ps1       one-time release key setup (per repository)
  TvAdb.ps1               shared helpers for both (adb, repository detection, downloads)
design/                   mockups, layout concepts, banner sources + generator, sky preview
app/src/main/
  AndroidManifest.xml     TvHomeApp, MainActivity (HOME), SettingsActivity
  assets/banners/         the four Lottie banners (diwali, simple, birthday, christmas)
  assets/fonts/           Inter Light/Regular/Medium/Bold (OFL)
  assets/upload/index.html   the phone page (one file: HTML, CSS, JS)
  java/com/chakriau/tvhome/v2/   see section 5
app/src/test/             JVM unit tests, one per logic area
.github/workflows/        v2-release.yml (signed, main), v2-check.yml (branches), for when this folder is a
                          repository's root
```

## 5. Architecture

### 5.1 Runtime flow

```
TvHomeApp.onCreate ── CrashLog.install (records crashes, decides safe mode)
                  └─ Backup.applyPending (puts a staged restore in place, before any setting is read)

MainActivity (HOME)
  ├─ AppsModel (singleton): installed apps + their state (Apps bar / Home / Hidden) and order → Snapshot
  ├─ theme = CrashLog.safeMode ? SafeHome : ThemeStore.active().create()     (a HomeTheme)
  ├─ WatchingRow: Watch Next (Play Next) → Continue watching row, or Recently opened   (not in safe mode)
  ├─ onStart → theme.onShown(), refresh apps → theme.showApps(snapshot), watching → theme.showWatching(row)
  ├─ onStop  → theme.onHidden()   (all animation stops; banner released)
  └─ implements HomeTheme.Actions: open app, App info, Settings, TV settings, network, inputs, resume

SettingsActivity (the launcher's own Settings; Menu key, or hold OK on Home's Settings button)
  ├─ left menu of SettingsPage classes, grouped: Phone · <theme name> · Your content
  ├─ while open: UploadServer on :8766 = the phone page (PIN-protected) → PhoneApi for JSON calls
  └─ HomePreviewActivity over it: the active theme as on Home, nothing opens; the server keeps running under it
```

### 5.2 Packages

| Package | What lives there |
| --- | --- |
| `(root)` | `TvHomeApp` (process start), `MainActivity` (Home), `HomePreviewActivity` (Home over Settings, section 18), `R` ids |
| `apps/` | `AppCatalog` (installed launchable apps), `AppStates` (pure: Apps bar ≤ 8 / Home / Hidden, ordering, new apps go to Home), `AppStore` (saves states), `AppsModel` (singleton snapshot + listeners), `AppArt` (banner/icon loading, cached), `AppLauncher`, `AppMenu` |
| `home/` | The theme system and Home's shared parts: `HomeTheme` (interface), `Themes` (theme list, `Part` enum), `ThemeStore` (active theme), `ThemeParts` (per-theme on/off and button style), `ThemeMigration`; parts `HomeKit`, `ClockWeather` (+ `RollingText`, `AnalogClock`, `ClockMath`), `TopButtons`, `InputsMenu`, `AppTiles`, `FocusFx`, `WatchingRowView`, `HomeDecor`, `MinuteTicker`, `WallpaperView`, `DefaultWallpaper`, `HomeScroller`, `Icons`; and `Theme1Home` |
| `eyes/` | `EyesView` (drawing, glancing, following focus), `Personality` (eight), `EyeSize`, `EyeSpots` (pure: random non-overlapping spots), `EyesStore` |
| `festive/` | Special days: `SpecialDays` (pure: days, dates, which banner/lights/photos today), `SpecialDayStore`, `SpecialDayMigration`, `Festivals` (built-in festival dates to 2030, lights modes), `Banners` (pure: designs, text limits), `BannerView` (Lottie), `LightsView`, `LightsStore`, `DecorTest` (pure: the phone's banner/lights test), `DecorTestStore` |
| `sky/` | `SkyMath` (pure: sunrise/sunset, arc, moon phase, weather effects), `SkyState` (pure: what shows now, phone test), `SkyStore`, `SkyView` |
| `watching/` | `WatchNext` (reads Play Next), `ContinueWatching` (pure: filtering, ordering, labels), `WatchingRow` (loads, builds rows), `Artwork` (programme pictures), `Resume`, `WatchingStore` |
| `weather/` | `Weather` (location → fetch, OpenWeatherMap with Open-Meteo fallback), `LocationChoice` (pure: TV location → IP → last good → by hand), `WeatherClient` (Open-Meteo, OpenWeatherMap, IP geolocation), `WeatherSource` (pure: which service, key checks), `OwmCode` (pure: OpenWeatherMap ids → WMO codes), `WeatherCode` (WMO codes → kinds), `TimeFormats` (pure: clock, date, refresh timing, `clockSet`), `City`, `WeatherStore` |
| `wallpapers/` | `Photos` (stored 1920 × 1080 + 320 × 180 thumbs, ≤ 100), `PhotoSize`, `Rotation` (pure), `WallpaperStore`, `UploadServer` (the phone page's HTTP server), `PinGuard` (pure: 5 wrong → 5-minute lock), `QrCode`, `NetworkAddress` |
| `phone/` | `PhoneApi` (JSON endpoints), `PhoneSettings` (every setting as sections/controls, shared shape with the TV pages) |
| `settings/` | `SettingsActivity`, `Ui` (the Settings look), one `*Page` per menu item |
| `system/` | `Inputs` + `InputLabels` (HDMI/AV/tuner list and switching), `NetworkStatus`, `SystemScreens` (TV settings intents), `TopBar` (pure: button style, Settings press), `TopBarStore` |
| `backup/` | `BackupFormat` (pure: file format rules), `Backup` (write ZIP, stage, apply, restart) |
| `safe/` | `SafeMode` (pure: three crashes in ten minutes), `CrashLog` (records, decides, last problem), `SafeHome` (plain Home) |
| `ui/` | `Fonts` (Inter weights from assets) |

### 5.3 Threads

- UI on the main thread. Each theme part gets one shared worker (`HomeKit.worker`) for network status and weather.
- `AppsModel.refresh()`, Watch Next reads and image decoding run off the main thread and post results back.
- `UploadServer` has its own accept thread and a pool of 2 for requests; `PhoneApi` runs there and posts TV updates
  to the main thread through `PhoneApi.Tv`.

## 6. Themes and how to add one

A theme is **the whole look of Home** (Requirements.md 7). It owns every visible part — whether it is drawn, where
and how — and none of the content (apps and order, photos, special days, Watch Next, weather).

**`HomeTheme`** (interface): `createView(activity, actions, art)`, `showApps(snapshot)`, `showWatching(row)`,
`onShown()`, `onHidden()`, `onHomePressedAgain()`, `onTrimMemory()`, `onDestroy()`, `onBack()`, and
`refreshDecor()` (default no-op: a phone test changed while showing; Theme 1 re-applies `HomeDecor`).

**`Themes.Part`**: `TOP_BAR`, `WALLPAPER`, `APPS_BAR`, `HOME_APPS` (structural: drawn whenever the theme has them),
`CONTINUE_WATCHING`, `BANNER`, `FESTIVE_LIGHTS`, `EYES`, `SKY` (optional: on/off per theme in `ThemeParts`, default
on). Settings › Theme and the phone page list a theme's optional parts automatically from this enum.

**Shared parts** (all take a `HomeKit`):

| Part class | Gives you | Theme decides |
| --- | --- | --- |
| `HomeKit` | `px()`, `text()` in Inter with Home's shadow, `match()`, `isIn()`, `Box`, main handler, worker, `actions` | — |
| `ClockWeather` | time, divider, date, weather icon/temperature/city added to a horizontal bar; `cornerText()`, `reading()`; hourly fetch while visible; time and date hidden (places kept) until `TimeFormats.clockSet` | where the bar goes |
| `TopButtons` (+ `InputsMenu`) | Inputs / Network / Settings glass pills, live network, inputs list; `settingsButton()` | where they go |
| `AppTiles` | `tile(app, w, h, labelPx, grow)` with banner art, focus grow + `FocusFx` border and sparkles | sizes, grid |
| `WatchingRowView` | Continue watching header, card row, caption; keeps focus across updates | where the row goes |
| `HomeDecor` | sky, banner, eyes, lights layers, per-part on/off, minute updates, eyes following focus, the phone tests and their "Test from the phone" label (call `bringTestLabelToFront()` after adding the page) | a `HomeDecor.Layout` (boxes) |
| `WallpaperView` | ticked photos (or the special day's), rotation and cross-fade, built-in drawing when off | — |
| `MinuteTicker` | a callback each minute and on time-zone change while started | what moves on it |
| `HomeScroller` | vertical page scroller with a policy function and scroll listener | the scroll policy |

**Adding a theme:**
1. Make `home/<Name>Home implements HomeTheme`. Build a `HomeKit` and `ThemeParts(activity, Themes.<NAME>)` in
   `createView`; compose the parts above; keep layout constants at the top in design pixels.
2. Declare it in `Themes`: `new Theme("<id>", "<Name>", "<one line>", EnumSet.of(parts…), appsBarMax, () -> new
   <Name>Home())` and add it to `Themes.ALL`. Use a lambda, not a method reference (keeps `Themes` loadable in JVM
   tests). The id is saved: never change it.
3. Per-theme values save under the theme id automatically (`parts_<id>`, `eyes_<id>`, `lights_<id>`).
4. A part the theme leaves out is hidden from Settings › Theme; its content pages stay. A theme-only part (e.g. a
   city theme's hero images) gets its own `Part` value, its own store and Settings page, listed only for that theme.
5. Add a test in `ThemesTest` for the declaration; add the theme to Requirements.md 7 and section 7 here.

## 7. Theme 1 spec

Mockups: `design/theme1-home.png` (first screen), `design/theme1-home-scrolled.png` (scrolled), and
`design/theme1-settings-apps.png`. All values are design pixels on 1920 × 1080 (code: `home/Theme1Home` and the part
classes). Font is Inter; weights in brackets.

### 7.1 Layers, back to front

1. `WallpaperView` (full screen) → 2. `SkyView` (sky box) → 3. dim (black, alpha 0 → 0.5 as the page scrolls) →
4. `EyesView` → 5. `LightsView` → 6. `BannerView` (banner box) → 7. the scrolling page (top bar, apps band, Continue
watching, Apps grid, footer) → 8. corner clock (top right, shown when scrolled). Layers 2 and 4–6 translate with
the page's scroll.

### 7.2 First screen (scrollY = 0)

| Item | Position and size |
| --- | --- |
| Page margins | 110 left and right; content starts 40 from the top; 90 bottom padding |
| Top bar | y 40, height 150, full content width |
| Sky box | x 0–1920, y 180–612; sun/moon arc from x 60 (rise) to 1860 (set), horizon y 588, peak y 250 |
| Banner box | x 360, y 230, 1200 × 340 |
| Eyes area | x 110–1810, y 210–604; eye radius 38 (× size: Small 0.75, Medium 1, Large 1.25, Extra large 1.5); the two pairs ≥ 70 apart in height; banner art (box inset 40 sides, 10 top, 20 bottom) avoided |
| Apps band | y 620, full content width (1700), height = tile height at 6 tiles + 60; padding 30; corner radius 46 |
| Bar tiles | width = (1700 − 60 − (n−1)·26) / max(6, n), height = width × 9/16 (≈ 251 × 141 at 6); gap 26; 6–8 tiles |

### 7.3 Below the band (scrolling)

| Item | Spec |
| --- | --- |
| Continue watching | 64 below the band; header "Continue watching" / "Recently opened" (36, Bold); cards 378 × 212, gap 26, radius 22, elevation 6; row bleeds to the screen edges (inset 110); 22 room above/below; caption (26, Regular, TEXT_2) "3 of 10 · latest item from each app, up to 10" |
| Card | programme picture (centre-crop) over the app banner; bottom scrim (72% of height, rgba(6,8,16) 215 → 120 → 0); app chip top-left (20, Bold, caps, bg rgba(10,12,22,170), radius 9, margin 16); title (29, Bold); subtitle (21, Regular, rgba(235,238,246,225)) e.g. `S2 E6 · 24 min left`; progress 6 high, white on white-70; focused: scale 1.08, z +16, white 5 border, "Resume" chip (white, 21 Bold dark) top-right |
| Apps grid | header "Apps" (36, Bold) 64 below; grid 30 below, padding 30 to line up with the bar; columns = max(6, bar count), same tile size and gaps as the bar; empty: "No apps on Home…" |
| Footer | 56 below: "Hold OK on an app: App info · Hold OK on Settings: …" (26, TEXT_2) |
| Scrolling | Down into Continue watching or the grid scrolls so the band peeks 70 at the top; the grid keeps the focused row 150 from the top / 100 from the bottom; 300 ms animation (`HomeScroller`). Dim = 0.5 × progress; corner clock fades in over the last 40% (34, Regular; "24° Chennai     9:41 PM"; top 78, right 110). Up or Back returns to the bar |

### 7.4 Top bar

| Item | Spec |
| --- | --- |
| Clock | 128 (Light), letter-spacing −0.02; AM/PM 40 (Regular) 12 to its right, baseline-aligned; 12/24 h per Settings. Both are `RollingText`: changed characters (compared from the right, `ClockMath.changed`) roll up by one line height over 520 ms each, rightmost first, each next one 70 ms later; old fades out, new fades in, `easeOutBack` overshoot 1.3, clipped to the view. Rolls only on the minute tick while Home shows and Rolling digits is on. The minutes sit in two slots as wide as the widest digit (Inter Light "4", ≈ 80; "1" is ≈ 50), glyphs centred (`setFixedDigits(2)`), and AM/PM is as wide as the wider of AM and PM (`reserve`), so AM/PM and the date don't shift each minute; they may move when the hour gains or loses a digit or narrows (e.g. 10 → 11) |
| Analog face | instead of the digital time when chosen: `AnalogClock` 136 × 136; dial white α34, rim 2 white α70; 12 ticks 9 in from the rim (12/3/6/9: 13 long, 4 wide, white; others 7 long, 2.5 wide, α170); hour hand 0.50r × 8, minute 0.76r × 5 (tails 0.10r, white, Home's shadow); seconds 0.84r × 2.5, tail 0.20r, #FFD34D; centre dot 7 white (+3.5 amber with seconds). Hands move to the new minute over 600 ms (`easeOutBack`); with seconds, a 180 ms tick each second between `start()`/`stop()` (Home shown), and the minute hand creeps with the seconds |
| Divider | 2 × 72, white alpha 90, margins 32, 16 down |
| Date | 36 (Regular), 22 down; `Fri, 6 Nov` / `Friday, 6 November` / `6 Nov`, never the year |
| Weather | 40 after the date, 14 down: outline icon 48, temperature 54 (Light) padded 16/14, city 34 (Regular) max 320 ellipsized; hidden when off or older than 3 h |
| Pills | Inputs (monitor icon; only if the TV has sources), Network (Wi-Fi waves with signal level, or Ethernet / Offline; label max 300), Settings (gear); height 80, gap 20, radius 40; icon 34; label 31 (Medium); padding 28/32 (icon + text), 32/32 (text), 23 (icon only, min width 80); idle bg white α40, stroke 2 white α70; focused bg white, content #101424, scale 1.06 (120 ms) |
| Inputs list | popup 600 wide, 14 under the button; bg rgba(22,27,44,244), stroke 2 white α60, radius 26; "INPUTS" 21 (Medium, spacing 0.14); rows ≥ 72 high, icon 32, label 29, state 21 ("● Connected" #78DC96 / "Not connected"); focused row white with dark text; lines between kinds |

### 7.5 Tiles and focus

| Item | Spec |
| --- | --- |
| App tile | face: gradient TL→BR #34406E → #242E52, radius 18, elevation 6; banner centre-cropped, or icon (padding 18% sides, 14% top, 42% bottom) over the label (bar 24 / grid 28, Medium, 14% from the bottom) |
| Focus | scale 1.2, z +16 (160 ms); `FocusFx`: 5 border turning from the app's colour (sampled from its banner) to white every 2.6 s; sparkles from the centre (one per 110 ms, up to 16, 900 ms flight) toward the edge, fading; drawn in front at elevation 12; paused when Home hides |
| Band glass | white α34, stroke 2 white α56; empty bar hint (28, TEXT_2): "The Apps bar is empty. Hold OK on Settings…" |

### 7.6 Colours and text

TEXT white; TEXT_2 rgba(220,226,240,200); dark text on white #101424; every Home text has a shadow (radius 6,
dy 1, black α90). Light bulbs: #FFD34D #FF5C7A #4BE3A0 #4FA8FF #FF9A3D #B98BFF.

### 7.7 Decorations

| Part | Spec |
| --- | --- |
| Festive lights | 3 swags along the top edge, sag 72, bulbs every 56 (19 × 27, glow 36), each lit ≈ 68% of its own 1.3–2.8 s cycle with 180 ms fades; 15 fps; drawn behind the top bar. Modes Off / Always on / On special days (default) |
| Eyes | two pairs at random spots (`EyeSpots`); eight personalities (Curious default, Playful, Sleepy, Angry, Mischievous, Sparkling, Shy, Determined) set shape, blink and glance timing, pupil reach, speed; follow focus while the remote moves, back to glancing 3.5 s after; 30 fps moving, 10 fps idle; fade to new spots when the wallpaper changes |
| Banner | Lottie 1200 × 340 at the banner box; `RenderMode.HARDWARE`, composition frame rate, async off; text on `LINE_1`/`LINE_2` (line 2 in capitals), scaled down to fit 600 wide (to 40% at most); released when Home hides |
| Sky | section 13 |
| Wallpaper | ticked photos, one fixed or rotating every 5/15/30/60 min, in turn or shuffled, 1.5 s cross-fade; a special day's own photos replace them on its days; built-in dusk-and-mountains drawing when off or empty |

## 8. Settings on the TV

`SettingsActivity`: a left menu (grouped, non-focusable headers) and the page on the right; a page opens when its
item is focused, Right/OK goes in, Back comes out, Back again closes. Every control applies at once. Look
(`settings/Ui`): background #0E0F13, menu #15161C, cards #1C1E25 (focused #252833), accent #6CC6F5, text white /
#A9ADB8 / #707480; rows ≥ 84 high, 1100 wide, radius 14. On/off rows show a radio "On/Off" (OK flips); choice rows
show the value with a dropdown arrow (OK opens the list).

| Group | Page (class) | Controls |
| --- | --- | --- |
| — | Phone (`PhonePage`) | QR code (address + `#pin=`), address, PIN, Change PIN |
| *theme name* | Theme (`ThemePage`) | theme choice; Preview Home (opens `HomePreviewActivity`); optional parts (Continue watching, Banner, Festive lights mode, Eyes, Sky); top-button style; the parts always drawn |
| | Eyes (`EyesPage`) | personality, size, live preview (only if the theme has eyes) |
| Your content | Apps (`AppsPage`) | tabs Apps bar · n of 8 / Home / Hidden; per app: move up/down, move to another tab, App info |
| | Wallpapers (`WallpapersPage`) | photos (OK ticks, hold OK removes), interval, order, cross-fade, wallpaper off, special-day photo today |
| | Continue watching (`ContinueWatchingPage`) | show (Started only / Everything), items per app, items in the row, Read Play Next, apps shown |
| | Special days (`SpecialDaysPage`) | today's banner (OK opens the Home preview), today's state, count and next date (names only on the phone) |
| | Time, date & weather (`TimeWeatherPage`) | weather on/off, weather from (Open-Meteo / OpenWeatherMap; the key is added on the phone), location (automatic / by hand, city search), °C/°F, clock face, rolling digits, clock (12/24 h), date format, status (with the source and any OpenWeatherMap problem) |
| | System (`SystemPage`) | Settings button press, show Wi-Fi name, default Home app, TV settings, About (version), Problems (safe mode / last problem; OK leaves safe mode or forgets it) |

## 9. Phone page and API

`UploadServer` serves `assets/upload/index.html` at `/` on **port 8766** while Settings is open (any page). Every
`/api/` call needs header `X-Pin: <6 digits>`; 5 wrong PINs lock for 5 minutes (429). The QR code carries the PIN
after `#pin=`, which the page reads and never sends in a URL. JSON bodies ≤ 64 KB; photo uploads ≤ 20 MB; restores
≤ 300 MB. Tabs: **Settings · Wallpapers · Special days · Apps · Test · Backup**.

| Method and path | Body / query | Answer |
| --- | --- | --- |
| `GET /api/photos` | — | `{max, enabled, current, photos: [{id, ticked}]}` (also the PIN check) |
| `GET /api/thumb/{id}` | — | 320 × 180 JPEG |
| `POST /api/upload` | JPEG body; `?tick=0` to leave it unticked | `{id}` |
| `DELETE /api/photo/{id}` | — | removes it (and from special days) |
| `GET /api/settings` | — | `[{id, title, group?, controls: [{key, label, type: toggle\|choice\|info, value, note?, options?}]}]` |
| `POST /api/setting` | `{key, value}` (bool for toggles, index for choices) | `Done` or the problem |
| `GET /api/special` | — | `{days: […], designs: [{id,label}], suggestions: [festivals]}` |
| `POST /api/special` | `{id?, name, from, to, yearly, design, line1, line2, lights, photos: [ids]}` | `Saved: <when>` |
| `DELETE /api/special/{id}` | — | removes the day |
| `POST /api/special-preview` | `{design, line1, line2, lights?}` | a 15-min banner (and lights) test; opens the Home preview |
| `GET /api/apps` | — | `{barMax, bar, home, hidden}` lists of `{pkg, label}` |
| `POST /api/app` | `{pkg, action: bar\|home\|hidden\|up\|down}` | moves it |
| `GET /api/city?q=` | — | Open-Meteo city search results |
| `POST /api/city` | `{name, latitude, longitude, detail}` | weather follows that city (set by hand) |
| `POST /api/weather-key` | `{key}` (16–64 letters/digits) | saves it, makes OpenWeatherMap the source, tests it: `Saved. The key works…` or `Saved, but: <problem>…` |
| `DELETE /api/weather-key` | — | forgets the key; back to Open-Meteo |
| `GET /api/sky` | — | `{theme, drawn, on, place, sunrise, sunset, body, percent, moon, weather, showing, drawing, reading: {enabled, code?, said?, kind?, city?, from?, minutesAgo?, used?}, bannerToday, bannerDrawn, bannerOn, lightsDrawn, lightsToday, test?: {minutesLeft, kind, minute, banner, line1, line2, lights}, kinds, designs}` |
| `POST /api/home-test` | `{kind: "" \| KIND, minute: -1 \| 0..1439, banner: real \| none \| design id, line1, line2, lights: real \| on \| off, show}` | sets the sky and decor tests (15 min, one `until`); `show` opens the Home preview, else an open one updates |
| `DELETE /api/home-test` | — | ends both tests |
| `POST /api/show-home` | — | opens the Home preview |
| `POST /api/sky-test` | `{kind: "" \| KIND, minute: -1 \| 0..1439}` | the sky part only (kept for older pages); opens the Home preview |
| `DELETE /api/sky-test` | — | back to the real sky |
| `GET /api/backup` | `?photos=1` to include photos | ZIP download (`Content-Disposition` has the name) |
| `POST /api/tick` | `{ids: [..] \| ["*"], ticked}` | ticks or unticks photos for Home (`"*"`: all) |
| `POST /api/show-now` | `{id}` | ticks the photo and makes it the one on Home now (`WallpaperStore.setCurrent`) |
| `GET /api/backup-info` | — | `{photos, photosMb}` |
| `POST /api/restore` | ZIP body | stages it; the launcher restarts ~3 s later |
| `GET /api/problem` | — | `{safeMode, last?: {when, version, text}}` |
| `POST /api/problem` | `{action: leave\|clear}` | leaves safe mode / forgets the last problem |

Adding a setting: add the control in `PhoneSettings.build` (it is then on the phone automatically, with its setter)
and the matching row on the TV page. Keys used by the phone (`part.<PART>`, `lights.mode`, `top.style`,
`theme.active`, `wallpaper.*`, `cw.*`, `weather.*` (incl. `weather.face`, `weather.roll`), `eyes.*`, `top.press`) are part of the page's contract.

## 10. Storage

All state is in SharedPreferences (files in `shared_prefs/<name>.xml`) plus photos in `files/wallpapers/`. Nothing is
in Android's auto-backup (`allowBackup=false`); use the phone page's Backup.

| File | Keys (type) | Owner |
| --- | --- | --- |
| `apps` | `states` (string: per package state + order) | `AppStore` |
| `themes` | `active` (theme id), `version` (migration done) | `ThemeStore` |
| `parts_<themeId>` | `on.<PART>` (bool, default true), `top_button_style` (string) | `ThemeParts` |
| `eyes_<themeId>` | `personality`, `size` (enum names) | `EyesStore` |
| `lights_<themeId>` | `mode` (`OFF` / `ALWAYS` / `FESTIVAL_DAYS`, shown as "On special days") | `LightsStore` |
| `special_days` | `days` (string list; format in `SpecialDays.format/parse`) | `SpecialDayStore` |
| `wallpapers` | `enabled`, `interval` (min), `shuffle`, `crossfade`, `ticked` (ids), `current`, `shownAt`, `pin` (kept on restore) | `WallpaperStore` |
| `weather` | `enabled`, `fahrenheit`, `clock`, `date`, `automatic`, `by_hand`, `clock_face` (`DIGITAL` / `ANALOG` / `ANALOG_SECONDS`, default digital), `clock_roll` (bool, default true), `last_good` (cities `name\|lat\|lon\|detail`), `source` (where the place came from), `provider` (`OPEN_METEO` / `OPEN_WEATHER_MAP`), `owm_key` (secret: backed up, never shown in full or logged), `reading_c/code/day/city/at` (code is always WMO), `reading_provider`, `provider_problem` | `WeatherStore` |
| `continue_watching` | `mode`, `per_app`, `total`, `blocked` (set), `recent` (recently opened), `asked_permission` | `WatchingStore` |
| `top_bar` | `settings_press` | `TopBarStore` |
| `sky_test` | `test` (`KIND\|minute\|until`) — not backed up | `SkyStore` |
| `decor_test` | `banner` (`REAL`/`NONE`/`DESIGN`), `design` (id), `line1`, `line2`, `lights` (`REAL`/`ON`/`OFF`), `until` (long) — not backed up | `DecorTestStore` |
| `safe_mode` | `crashes` (times), `safe`, `safe_version`, `last_text`, `last_at`, `last_version` — not backed up | `CrashLog` |
| Old, read once by migrations | `festive`, `banners`, `banner_<themeId>`, `eyes_theme1` (`enabled`), `continue_watching` (`enabled`), `top_bar` (`style`) | `ThemeMigration`, `SpecialDayMigration` |

Photos: `files/wallpapers/<id>.jpg` (1920 × 1080, centre-cropped) and `files/wallpapers/thumbs/<id>.jpg`
(320 × 180); ids `[0-9a-z]` checked by `Photos.validId`; at most 100.

## 11. Backup and restore

Format (`backup/BackupFormat`): a ZIP with `backup.json` = `{format: 1, app: "tvhome-v2", version, created,
photos, prefs: {<file>: {<key>: [type, value]}}}` (types `b i l f s` and `set` with a list), plus
`photos/<id>.jpg` and `photos/thumbs/<id>.jpg` when photos are included. Every prefs file is included except
`safe_mode`, `sky_test` and `decor_test`; `wallpapers/pin` is never written or restored.

Restore: the upload is saved to the cache, then `Backup.stage` checks it all (manifest app and format, entry names,
photo ids, JPEG headers, sizes, ≤ 210 entries) and stages `files/restore/settings.json` + photos, writing `ready`
last. The launcher restarts (`Backup.restart`: an alarm for `MainActivity`, then the process ends);
`TvHomeApp` calls `Backup.applyPending` before anything reads a setting: every included file is cleared (kept keys
stay) and refilled with `commit()`, photos replace the TV's if the backup has them, and the stage is deleted. A
failure is recorded as a problem and the stage is dropped, so it can't fail at every start.

## 12. Safe mode

`CrashLog.install` (first thing in `TvHomeApp`) wraps the default uncaught-exception handler: it saves the crash
(time, version, first 40 lines of the stack trace) with `commit()` and passes it on. `SafeMode.shouldEnter`: three
crashes within ten minutes. Then `MainActivity` builds `SafeHome` (dark page, explanation, last problem, buttons
"Try normal Home again", "Launcher settings", "TV settings", and the Apps bar + Home apps as text tiles) and skips
Watch Next. It ends with "Try normal Home again" (Home, Settings › System › Problems, or the phone page's Backup tab),
or by itself when a higher `versionCode` is installed. `MainActivity.onStart` recreates itself when safe mode starts
or ends.

## 13. Sky

`sky/` (Requirements.md 5.10). `SkyStore.now()` → `SkyState.at(now, zone, place, realKind, test)`:

- **Place:** the weather's city (`last_good` when automatic, else `by_hand`, else the other); none → 6:00 / 18:00.
- **Sun/moon:** `SkyMath.sunEvents` (NOAA sunrise equation, −0.833° horizon, ±2 min). Sun from sunrise to sunset;
  moon from sunset to the next sunrise, in its real phase (synodic month from the 6 Jan 2000 new moon). Progress 0 →
  1 maps to x 60 → 1860 of the sky box, y on a half-sine from the horizon (box bottom − 24) to the peak (top + 70);
  fades in/out over the first/last 4%.
- **Sun** (`SkyArt`, cached bitmaps): radius 46 × (1 + 0.25·warmth); warmth = `SkyMath.warmth`, 0 high .. 1 on the
  horizon over the first/last 18% of the arc, in 8 steps (one bitmap per step). Layers: halo to 4.4r (alpha 0.30 →
  0.10 at 35% → 0), bloom to 1.9r (alpha 0.75), disc with a radial gradient from a point 0.15r up-left (core
  #FFFCF0 → edge #FFD678; on the horizon #FFC48C → #FF783C). Rays: a separate bitmap of 12 tapered rays from 0.9r,
  alternately 3.6r and 2.5r long (2.4r on the horizon), alpha 0.22 fading out; turned once every 240 s and breathing
  ±15% over 12 s, at 12 fps.
- **Moon** (`SkyArt`): radius 40; the surface `assets/sky/moon.webp` (256 × 256, generated by
  `design/sky/make_moon.py`: maria in their near-side places, craters, Tycho's rays, no licence) drawn into a bitmap,
  then a shadow layer (black, alpha 0.84) with the lit part cut out by an opaque DST_OUT path blurred by 0.07r is
  taken off with DST_OUT, so the unlit side keeps 16% (earthshine) and the sky shows through. Lit path: right half
  of the disc and back along a half-ellipse of width r·|cos 2πf| (crescent bulging right, gibbous left), mirrored
  for a waning moon. Remade per 1% of phase. Halo: radius r·(2.2 + 1.4·lit), alpha 0.10 + 0.22·lit, colour
  #DCE4FF, `lit = SkyMath.moonLit` (0 new, 1 full).
- **Moon over a pale photo:** `WallpaperView` measures each photo once when decoded (shrunk to
  64 × 36, the theme's sky rows from `setSkyBand`, 16 column means of Rec. 709 luminance: `SkyMath.skyLight`) and
  hands it to `SkyView.setLight` through `HomeDecor.onWallpaperChanged(skyLight)` (null for the built-in drawing).
  At the moon's x, `k = SkyMath.moonBackdrop(SkyMath.lightAt(...)) × alpha` (smoothstep 0.40 → 0.80); if k > 0.01 a
  radial patch of #0C1228 is drawn first, radius 2.8r, alpha 0.92k / 0.88k at 1.02r / 0.45k at 1.6r / 0.14k at 2.2r
  / 0, and the halo's alpha is × (1 − 0.6k). Dark photos (≤ 0.40) are unchanged.
- **Why not Lottie:** the moon's look is a texture and its phase changes daily, which Lottie can only do
  by embedding a picture and animating a mask through value callbacks; cached bitmaps give the same look for two
  bitmap draws a frame.
- **Weather** (`SkyMath.effects`): clouds / grey / rain / drizzle / snow / lightning / haze / body alpha / stars —
  Clear 0 clouds, stars at night; Partly cloudy 3; Cloudy 6, body 0.62; Fog 2 + low haze (`HAZE_STOPS`/`HAZE_ALPHA`: thickest low in the sky, fading out above the band), 0.5; Drizzle 5 grey + 45
  short drops, 0.3; Rain 6 grey + 110 drops, 0.2; Snow 4 grey + 70 flakes, 0.3; Storm 7 grey + 140 drops +
  lightning, sun hidden. Unknown weather = clear. `Effects.veil()` (clouds > 0 and 0 < body alpha < 1): one cloud
  (the 300 × 110 image) stays in front of the sun or moon, placed by `SkyMath.veilAt`: its tallest puff 0.6r right
  of the body's centre, its top 0.25r above it, swaying ±0.35r over 40 s; drawn after the body and before the
  drifting clouds, at `SkyMath.veilAlpha` (140 white, 165 grey) × the
  horizon fade.
- **Drawing** (`SkyView`): three cloud images (300 × 110, 220 × 84, 380 × 130, blurred once) drifting 7–17 px/s,
  starting 40 below the top bar; rain/snow clipped to the box and fading over its last 20%; lightning: soft-edged
  flash, double now and then, every 4–12 s; 28 twinkling stars at night when clear. 30 fps with rain/snow/lightning,
  12 fps with only clouds, stars or the sun's turning rays, no frames for a clear night (the moon moves on the minute
  tick).
- **Test:** the phone's Test tab saves `SkyState.Test(kind, minute, until = now + 15 min)`; `HomeDecor` uses it
  (on Home and in the Home preview) until it ends. See section 18.
- **Design check:** `design/sky/preview.html` is a browser copy of `SkyView` over the mockup
  (`?kind=RAIN&body=SUN&p=0.4&t=3000`, `&bg=<image>` for another wallpaper, e.g. a white one); keep it in step when
  changing the sky.

## 14. Banners

Lottie JSON, one per design (`assets/banners/<id>.json`, 50–117 KB): still art embedded as WebP plus vector moving
parts and two text layers `LINE_1` / `LINE_2`. Template: canvas 1200 × 340, 30 fps, 2–4 s seamless loop; line 1
Inter Bold 64 centred at x 600, baseline 188; line 2 Inter Regular 26 letter-spaced, baseline 228; text safe zone
x 300–900, y 110–240. Generator and art sources: `design/banners/` (README there). New designs: add a
`make_banner.py` variant, copy the JSON into assets, add a `Banners.Design` value; check it with the phone page's
Test tab (banner: the design, lines typed there) or "Show on the TV's Home" on the Special days tab.

## 15. Testing

JVM tests in `app/src/test`, one class per logic area: `AppStatesTest`, `AppMenuTest`, `EyesLogicTest`,
`BannersTest`, `FestivalsTest`, `SpecialDaysTest`, `ThemesTest`, `TopBarLogicTest`, `WallpaperLogicTest`,
`ContinueWatchingTest`, `WeatherLogicTest`, `OpenWeatherMapTest`, `SkyTest`, `BackupFormatTest`, `SafeModeTest`,
`DecorTestTest`, `ClockMathTest` (159 tests). Views and
Android stores aren't unit-tested; check them on the TV (screenshots, Requirements.md 9) after the V2 release build.

## 16. Checklist for a change

1. Read the relevant Requirements.md section and the classes named here.
2. Put new logic in a plain-Java class with a test; keep Android classes thin.
3. New saved values: new key (never rename), defaults that match today's behaviour, and a line in section 10.
4. Anything animated: only while Home is visible, only when moving; check memory on a 1 GB budget.
5. A new feature: a Settings row on the TV **and** the phone (via `PhoneSettings`), on/off where it makes sense.
6. Run the tests; push to a branch and wait for **V2 check**; merge to `main` for the signed build.
7. Update Requirements.md (feature, decision) and this file (map, spec, storage, API).

## 17. Weather sources

`Weather.update` (off the main thread) finds the place (`LocationChoice`), then the current weather:

| Source | Request | What is used |
| --- | --- | --- |
| Open-Meteo (default) | `api.open-meteo.com/v1/forecast?latitude&longitude&current=temperature_2m,weather_code,is_day&timezone=auto` | °C, WMO code, day/night |
| OpenWeatherMap (owner's key) | `api.openweathermap.org/data/2.5/weather?lat&lon&units=metric&appid=<key>` (free current-weather API) | `main.temp`, `weather[0].id` → WMO via `OwmCode`, `weather[0].icon` ending `n` = night |

With OpenWeatherMap chosen, a failure (401: key not accepted, often a new key not active yet; 429: too many requests;
no answer) falls back to Open-Meteo for that reading and saves the reason in `provider_problem`, shown in Settings
and on the phone. Readings always store a WMO code, so the icon, the sky and saved readings don't care which service
answered. The key never appears in logs (only `HTTP <status>` or the exception's class is logged) or in full on any
screen (`WeatherSource.masked`). City search always uses Open-Meteo's geocoding (no key).

## 18. Home preview and phone tests

Requirements.md 5.14. Seeing a banner, the lights, the sun, moon or weather means seeing **Home**, so there are no
preview dialogs any more.

- **`HomePreviewActivity`** (root package; not exported, not HOME): builds `ThemeStore.active().create()` exactly as
  `MainActivity` does, with `AppsModel.current()`, a `WatchingRow` and its own `AppArt`. Its `HomeTheme.Actions` open
  nothing (a toast says it is a preview) except `openSettings` = finish. Back → `theme.onBack()`, then finish; Menu
  finishes; `onStop` finishes it (a preview is never returned to). Safe mode: a toast, no preview. Static helpers
  (main thread): `open(from, whenGone)`, `refresh()` (→ `theme.refreshDecor()`), `close()`, `isOpen()`.
- **Keeping the phone page:** `SettingsActivity.openHomePreview()` sets `keepServerForPreview`, so its `onStop` leaves
  `UploadServer` running under the preview; `onStart` clears it. The preview's `whenGone` stops the server if Settings
  isn't on screen (the Home key from the preview; normally `MainActivity`'s `singleTask` + `clearTaskOnLaunch`
  destroys both, and `onDestroy` stops it anyway).
- **`PhoneApi.Tv`:** `changed()`, `showHome()` (open the preview, or refresh the open one), `testChanged()` (refresh
  an open preview and the page on screen).
- **Tests:** the sky part is `SkyState.Test` in `sky_test` (unchanged); banner and lights are `festive/DecorTest` in
  `decor_test` (`DecorTestStore`). `/api/home-test` writes both with the same `until`; a choice of "real" for
  everything clears them. `DecorTest.banner(test, now, real)` and `DecorTest.lightsOn(test, now, real)` are what
  `HomeDecor` asks; a design with no text gets `SAMPLE_LINE1`.
- **`HomeDecor` rules:** a test shows even when the part is off in Settings › Theme (`parts.has` is still required);
  the label "Test from the phone · n min left" (24 Medium, bg #CC10131F, stroke 2 #66FFD34D, radius 22, padding
  22/10, bottom-right 40/36, not scrolling) shows while a sky test (theme has a sky) or a decor test that changes
  something is active, updated on `onShown` and each minute. Themes call `decor.bringTestLabelToFront()` after adding
  their page.
- **Explaining the real sky:** `GET /api/sky` adds `reading` (the stored WMO code described by
  `WeatherCode.describe`, its kind, the service, city and age; `used` = recent enough to draw) and `drawing`
  (`SkyMath.Effects` in words). OpenWeatherMap's "few clouds" (801) becomes WMO 1, Mainly clear, which is Partly
  cloudy: 3 white clouds.
