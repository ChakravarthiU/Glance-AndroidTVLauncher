# Launcher V2.0 — Requirements

This file is the single source of truth for **what** V2 does and why: features and decisions. How it is built
(architecture, Theme 1's measurements, storage, the phone API) is in [`DeveloperNotes.md`](DeveloperNotes.md). Start
each development session by reading both. The design mockups, banner sources and the sky preview are in
[`design/`](../design/).

V2 is a fresh Android TV launcher, written from scratch. It replaces an earlier launcher (V1, package
`com.chakriau.tvhome`), which can stay installed on the TV as a fallback. V2's first look is **Theme 1**: the V1 look (photo wallpaper, glass apps bar, eyes, festive
lights) rebuilt so that **every feature is visible and can be switched off in Settings**, with no hidden config files.

## 1. Principles

1. **Visible or it doesn't exist.** Every feature has a row in Settings with an on/off switch or a value and a
   one-line description. No `config.xml` or `config.json` switches the TV owner can't see.
2. **One launcher, many themes.** Looks are themes chosen in Settings, never separate launcher apps.
3. **A theme changes how things look, never what works.** Apps, inputs, key handling and settings data are shared.
4. **Built for a 1 GB TV.** Decode images at display size, animate only while Home is visible and only when
   something moves, free memory when Home is hidden.
5. **Home is not edited in place.** Apps are arranged only in Settings.
6. **Never stranded.** A crash loop falls back to a plain Home (safe mode); everything set up can be backed up and
   restored from the phone.

## 2. Project setup

| Item | Value |
| --- | --- |
| Folder | `Launcher_V2.0/` |
| Package name (applicationId) | `com.chakriau.tvhome.v2` — installs alongside V1 |
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 34 |
| Language | Java 17 (same as V1; no Kotlin runtime in the APK) |
| Libraries | Lottie 6.5.2 (banners), RecyclerView 1.3.2 (Settings › Apps), ZXing core 3.5.3 (QR code) |
| UI resolution | Designed at 1920 × 1080 and scaled (Android TV renders at 1080p, also on 4K panels) |
| Fonts | Inter (Light, Regular, Medium, Bold), OFL licence, bundled |
| Build | GitHub Actions: **V2 release** (signed APK, every push to `main`, published on the rolling GitHub Release `v2-latest`, since release files don't count against the Actions storage quota) and **V2 check** (tests and an unsigned build, every other branch) |
| Signing | One fixed release keystore in GitHub Secrets, so every build installs as an update (`adb install -r`). Never the debug key. |
| Tests | Plain JVM unit tests for all logic (ordering, dates, scheduling, sky maths, backup format, safe mode) |

## 3. Decisions made

| Decision | Choice |
| --- | --- |
| One launcher or several | One launcher; looks are themes picked in Settings |
| Package name | `com.chakriau.tvhome.v2`, so V2 installs next to V1 with clean data and easy rollback |
| Rearranging apps on Home | Not possible on Home; done only in Settings › Apps (TV or phone) |
| Newly installed apps | Go to Home (the app grid), at the end of the Home order |
| Order of Home apps | The order set in Settings |
| Hold OK on an app on Home | Shows App info only |
| Top-right buttons | Inputs, Network (Wi-Fi name) and Settings only |
| Top-button style | Icons, text or both, per theme; all three buttons follow it |
| Settings button | Press opens the TV's settings, hold opens the launcher's Settings (or the other way round, Settings › System) |
| Date format | Weekday, day and month, no year (`Fri, 6 Nov`) by default |
| Eye placement | Random spots at different heights, not in a fixed row |
| Banner format | One Lottie JSON file per banner: embedded still art, vector moving parts, two replaceable text lines |
| Settings levels | Your content is global; a theme's own settings only for parts the active theme draws |
| What a theme owns | Every visible part of Home (top bar, wallpaper, apps bar, Home apps, Continue watching, banner, festive lights, eyes, sky): whether and how it is drawn. The content in them is global (section 7) |
| Festivals | Suggestions for special days only; a festival does nothing until added |
| Where special days are set | Only on the phone, so the TV isn't crowded and a birthday can stay a surprise |
| Sun and moon times | Worked out on the TV from the weather's city; the moon takes the night |
| Weather source | Open-Meteo by default; OpenWeatherMap with the owner's key, chosen in Settings |
| Backups | Made and restored from the phone page; the TV keeps its own phone-page PIN |
| Safe mode | Three crashes in ten minutes; ends by choice or with a newer version |

## 4. Home screen (Theme 1)

Home is one vertical page over a fixed wallpaper. The first screen shows everything down to the top of Continue
watching; pressing Down scrolls to the rest. Mockups: `design/theme1-home.png`, `design/theme1-home-scrolled.png`;
measurements in DeveloperNotes.md section 7.

From top to bottom:

1. **Top bar** — time, date and weather on the left; Inputs, Network and Settings buttons on the right, 40 px from
   the top; the festive lights hang behind it.
2. **Festive lights** — light strings along the top edge, when on.
3. **Sky, banner and eyes** — between the top bar and the apps bar: the sun or moon crossing from left to right with
   the weather's clouds, rain or snow; the special day's banner in the middle; two pairs of eyes at random spots.
4. **Apps bar** — 6 to 8 apps on a glass band. Focus starts here (on Settings while the bar is empty).
5. **Continue watching** — one row, up to 10 cards.
6. **Apps** — a grid of the apps set to Home, in the order set in Settings, at the Apps bar's tile size and as many
   to a row as the bar has tiles (6 to 8).

When scrolled down, the wallpaper dims, the top bar gives way to a small clock and weather in the corner, and the
apps bar peeks at the top edge. Back or Up returns to the apps bar.

## 5. Features

Every feature below has a switch or a value in Settings (on the TV and on the phone page); turned off, it is gone
from Home.

### 5.1 Wallpapers from phone
- Settings › Phone shows a **QR code** with the phone page's address and PIN; the phone's browser opens it. No typing
  on the TV. The page is served (port 8766) while the launcher's Settings is open.
- Photos are cropped to 16:9 on the phone, then stored at exactly 1920 × 1080 with a 320 × 180 thumbnail (up to 100).
- Tick one or more photos, on the TV (OK on a photo) or on the phone (each photo's "On Home" box, "Show
  now" to tick it and put it on Home at once, Tick all / Untick all; the photo on screen is marked "Showing"). New
  uploads are ticked straight away. One photo stays fixed; several rotate: every 5, 15 (default), 30 or 60
  minutes, in turn or shuffled, with or without a cross-fade. Rotation happens only while Home is visible.
- A special day's own photos (section 5.13) show instead of the ticked ones on its days, even with Wallpaper off.
- Wallpaper off, or nothing ticked, shows a built-in dusk-and-mountains drawing.
- The PIN locks for 5 minutes after 5 wrong tries and can be changed in Settings › Phone.

### 5.2 Eyes with personality
- Two pairs of eyes at random spots between the top bar and the apps bar, at clearly different heights. They never
  cover the clock, buttons, banner or apps, never take focus and never block input; they move to new spots each time
  the wallpaper changes.
- Idle: both pairs glance around, blink, and now and then look at each other. While the remote moves they follow
  the focused item, then drift back to glancing 3.5 s after the last move.
- Personality sets shape, blink rate, glances and speed: Curious (default), Playful, Sleepy, Angry, Mischievous
  (winks), Sparkling, Shy, Determined. Size: Small, Medium, Large, Extra large. Both per theme.

### 5.3 Festive lights
- Modes: **Off**, **Always on**, **On special days** (default: the special days whose Lights is on, section 5.13).
- Three swags of coloured bulbs along the top edge, behind the top bar; each bulb blinks at its own pace (lit about
  two thirds of a 1.3 to 2.8 second cycle).
- The built-in list of festivals (Diwali, Navratri & Dussehra, Holi, Pongal & Sankranti, Ganesh Chaturthi, Onam,
  Ugadi & Gudi Padwa, Raksha Bandhan, Janmashtami, Eid al-Fitr, Eid al-Adha, Christmas, New Year, Republic Day,
  Independence Day) are only suggestions for special days. Lunar dates ship as a table to 2030
  (`festive/Festivals`); they need updating for 2031 on.

### 5.4 Decorative banner
- A special day's banner (section 5.13), hanging in the middle of Home on its days; on/off per theme.
- Designs: Diwali, Simple, Birthday, Christmas; each special day picks one (or no banner) and two lines of text.
  See section 6.

### 5.5 Apps bar
- 6 to 8 apps on a glass band; tiles are sized as if there were at least 6.
- **Focus:** the focused app (Apps bar and Home grid) grows 20%, its border turns slowly through the app's own colour
  (sampled from its banner) to white, and small sparkles burst from its centre towards its edge as they fade
  (`home/FocusFx`, animated only while focused and Home is visible).
- Which apps and their order are set only in Settings › Apps.

### 5.6 Home apps and hidden apps
- Every installed app is in one of three states: **Apps bar**, **Home** or **Hidden**.
- New apps go to Home, at the end of the Home order. An uninstalled app keeps its place and returns there if
  reinstalled.
- On Home there is no move or rearrange mode. Hold OK shows App info only.

### 5.7 Continue watching
- One row of up to 10 cards: the latest item from each app, newest first. Both limits can be changed (items from each
  app: 1, 2, 3 or 5; items in the row: 5, 10, 15 or 20), and each app can be left out.
- A card shows the programme's picture, the app, title, episode, time left and a progress bar; OK resumes it in its
  app (only through an activity of the app that wrote it; otherwise the app opens).
- Show: **Started only** (default) or **Everything in Play Next** (many apps add next episodes at 0%).
- Source: Android's Watch Next programs, which need `READ_TV_LISTINGS` (asked once at first run, again from
  Settings, or granted by `Scripts/Install-Package.ps1`). On the Sony TV this reads every app's entries. When
  Play Next can't be read or is empty, the row becomes **Recently opened**: the last apps opened from Home.

### 5.8 Time, date and weather
- Time, date and time zone come from the TV; 12 or 24 hours; date `Fri, 6 Nov`, `Friday, 6 November` or `6 Nov`.
  No internet is needed for them: the TV keeps its own clock.
- **Clock not set yet** (as in V1): while the TV's clock is before 1 Jan 2025 (a box with no clock
  battery starts at 1970 or its build date after a power cut, until it reaches the internet), the time, divider and
  date are hidden, keeping their places, and the corner clock shows only the weather. They appear as soon as the TV
  sets its clock (its time-changed broadcast), with no polling.
- **Clock face:** **Digital** (default), **Analog**, or **Analog with seconds hand**, chosen in
  Settings › Time, date & weather and on the phone. The analog dial is glass like the buttons and apps band, with
  hour ticks, white hour and minute hands and a thin amber seconds hand; the hands click forward with a slight
  overshoot each minute (or tick each second with the seconds hand, only while Home shows).
- **Rolling digits** (on by default, on/off in the same places): on the digital face, each character
  that changes rolls up — the old one slides up and fades, the new one comes up from below — rightmost first, like
  an odometer (11:36 → 11:37 rolls only the 6; 11:59 → 12:00 rolls 9, 5, 1 in turn). AM/PM rolls the same way
  (only the A/P). The minutes keep a fixed width, so AM/PM and the date don't move as they change. No roll when Home appears after being hidden: the time is simply shown. The small corner clock
  (scrolled down) stays plain text.
- Location, in order: the TV's location service (if allowed), IP geolocation (skipped while a VPN is on), the last
  good city, a city set by hand (searched with Open-Meteo geocoding).
- **Weather from:** **Open-Meteo** (default, free, no key) or **OpenWeatherMap** with the owner's own
  API key (free "current weather" API). The key is pasted on the phone page (Settings tab), tested on saving, stored
  only on the TV (and in backups), and never shown in full. If OpenWeatherMap fails (key not active yet, quota used
  up, no answer), that reading comes from Open-Meteo and Settings says why. Chosen because Open-Meteo's
  model-based weather showed Drizzle in Bengaluru while OpenWeatherMap showed Few clouds.
- Kept in °C and shown in °C or °F; fetched at most hourly while Home is visible, retried after 5 minutes on failure,
  hidden once 3 hours old. Either source's conditions become the same eight kinds (Clear, Partly cloudy, Cloudy,
  Fog, Drizzle, Rain, Snow, Thunderstorm) for the icon and the sky.

### 5.9 Top buttons
- **Inputs** (only when the TV has sources) opens a list of HDMI ports (with CEC device names and Connected / Not
  connected), AV and others, then Live TV, and switches straight to the chosen one.
- **Network** shows the Wi-Fi name with its signal (or Ethernet / Offline) and opens network settings. The name needs
  location permission (Settings › System, or `adb shell pm grant … ACCESS_FINE_LOCATION`).
- **Settings**: press and hold open the TV's settings and the launcher's (which is which is set in Settings › System).
- Style: Icons only, Text only, or Icons and text, set per theme in Settings › Theme.

### 5.10 Sky
- An optional part of Theme 1, on by default (Settings › Theme › Sky), drawn over the wallpaper between the top bar
  and the apps bar, behind the banner and eyes.
- **Sun:** rises at the left edge at sunrise, crosses in an arc and sets at the right edge at sunset: a white-hot
  disc fading to gold, with a soft bloom, a wide halo and slowly turning rays; near the horizon it grows larger,
  oranger and dimmer. **Moon:** crosses the same way from sunset to the next sunrise, as a moon surface (seas,
  craters, Tycho's rays) in its real phase, the unlit part fading almost clear behind a soft terminator, with a halo
  that brightens towards full moon; stars twinkle on a clear night. Over a pale wallpaper a soft patch of night sky sits behind the moon, as strong as the photo is bright
  there, so the moon and its dark side always read; dark photos look as before. (Drawn as cached pictures rather
  than Lottie, see DeveloperNotes 13.) Sunrise and sunset are worked out on the TV for the weather's city (6:00 and 18:00 until a city is known).
- **Weather:** a few drifting clouds when partly cloudy, more when cloudy (the sun dimmer behind them;
  one cloud always sits partly in front of a dimmed sun or moon and rides with it, since a faded sun in open sky
  looked like a fault, and Cloudy dims it less, to 62% from 35%, so it still reads on a pale photo), drizzle or
  rain falling, snowflakes swaying, a low haze in fog (fading out before the apps band, never a hard edge), and lightning flashes in a storm. With weather off or not yet
  fetched, the sky is clear.
- Animated only while Home is visible, and only as fast as the weather needs (none on a clear day: the sun moves
  once a minute).
- **Testing:** the phone page's **Test** tab (section 5.14) shows what the sky draws now and why (the real weather
  reading behind it), and tests any weather at any time of day on Home itself.

### 5.11 Backup and restore
- The phone page's **Backup** tab downloads a backup file (`tvhome-backup-<date>.zip`) with everything set up:
  every setting, the apps and their order, special days, which photos are ticked, and (optionally, on by default)
  the wallpaper photos themselves.
- **Restore** uploads a backup file: it is checked in full first (anything wrong is reported and nothing changes),
  then the launcher restarts on the TV and puts everything back. What is on the TV is replaced; photos only if the
  backup has them. The TV keeps its own phone-page PIN.
- Not backed up: the crash record and safe mode (about this TV, not your setup) and a running test from the phone
  (5.14).

### 5.12 Safe mode
- Every crash is recorded (what failed, when, which version, where in the code). Three crashes within ten minutes
  start Home in **safe mode**: a plain page listing the Apps bar and Home apps as text tiles, with "Try normal Home
  again", "Launcher settings" and "TV settings", using none of the parts that could be failing (no photos, banner,
  sky, eyes, lights, weather or Continue watching).
- Safe mode ends with "Try normal Home again" (on Home, in Settings › System › Problems, or on the phone page's Backup
  tab), or by itself when a newer version of the launcher is installed.
- The last problem's report is shown on the phone page's Backup tab and summarised in Settings › System › Problems,
  where it can be forgotten.

### 5.13 Special days
One place for everything about a day, set on the phone page's **Special days** tab:

- **Your own dates:** each has a name, a date or a range of dates (once, or every year; a yearly range may cross New
  Year and last up to 60 days), a banner design (or No banner) with its first and second line, Lights on/off, and a
  wallpaper: **Default** (the usual ticked photos) or the day's own photos (any on the TV, or new uploads, which are
  not ticked so a surprise stays one; several take turns at the rotation interval). Listed soonest first, with Edit
  and Remove; "Show on the TV's Home" shows the banner (and the day's lights) on Home before saving (section 5.14).
- **Festival days:** the built-in festivals as small tiles with their next dates; tapping one fills in a new special
  day (name, dates, a matching design, the festival's greeting, Lights on) to change and save. Lunar festivals are
  filled in for this time only, fixed ones as every year. A tile says when it is already added.
- On a day with several special days, the newest saved one's banner shows, the lights come on if any wants them, and
  their photos take turns.
- The TV (Settings › Special days) shows only today's state, how many special days there are and the next date,
  never a day's name.
- Older setups kept these in three places (festival ticks and own dates, banner messages, special-day wallpapers);
  `festive/SpecialDayMigration` merges them once.

### 5.14 Home preview and tests from the phone
Banners, lights, the sun and moon, and the weather are checked **where they really go**, on Home, not in a popup.

- **Home preview:** Home exactly as it looks (the active theme, the real wallpaper, apps, Continue watching and
  decorations), opened over Settings: Settings › Theme › Preview Home, OK on Settings › Special days › Banner today,
  or from the phone. Nothing opens from it (OK on an app just says it is a preview); Back (once at the apps bar),
  Menu or the Settings button return to Settings; it closes when hidden (Home key, an input switch). The phone page
  keeps running under it, so the phone can keep changing what it shows.
- **Phone page › Test tab:**
  - **Home now:** what the sky draws ("3 white clouds, the sun"), the real weather behind it (what the service said,
    for example "Mainly clear (Partly cloudy) · OpenWeatherMap · Bengaluru · 12 min ago"), and **why** (real
    weather, a test, or no recent weather), plus sunrise and sunset, the moon, and today's banner and lights. This
    answers "are these clouds real?".
  - **Test on the TV's Home:** weather (or the real one), time of day (slider, with Now / Sunrise / Midday / Sunset /
    Night), banner (today's, none, or any design with its two lines) and lights (as today, on, off). **Show on the
    TV** opens the Home preview with the test; while a test runs, each change on the phone shows on the TV at once.
    **End the test** goes back to the real sky, banner and lights.
- A test lasts **15 minutes**, on the preview and on Home itself, then ends by itself. While one runs, Home shows a
  small **"Test from the phone · n min left"** label in the bottom-right corner, so a test is never mistaken for the
  real weather or a real special day. A test shows even when that part is turned off in Settings › Theme (it is not
  shown for parts the theme doesn't have).
- The Special days tab's "Show on the TV's Home" is the same kind of test (that banner and the day's lights).

## 6. Banners

Each banner is **one Lottie JSON file**: the launcher plays it in a loop and fills in the owner's two lines of text.
Built-in: Diwali, Simple (indigo cloth, gold rope), Birthday (bunting, bobbing balloons, confetti) and Christmas
(pine garland, twinkling baubles, snow), 50 to 117 KB each, in `app/src/main/assets/banners/`.

### 6.1 What's inside a banner file

| Layer | What it is | Animation |
| --- | --- | --- |
| `art` | Still artwork (cloth, garland, tassels, diya bowls…), embedded as one WebP image | None |
| moving parts | Vector flames and glows, sparkles, balloons, confetti, snow (per design) | Loop |
| `LINE_1`, `LINE_2` | Text layers | Replaced by the launcher at run time |

### 6.2 Banner template
- Canvas 1200 × 340 px, 30 fps, seamless loop of 2–4 seconds.
- Line 1: Inter Bold 64, centred at x 600, baseline y 188. Line 2: Inter Regular 26, letter-spaced, baseline y 228,
  shown in capitals.
- Text safe zone: x 300–900, y 110–240; line 1 up to 40 characters and line 2 up to 60 are typed on the phone; a line
  too wide for the zone is scaled down (to 40% at most).
- The file's `meta` object records the template name, text slots and safe zone.

### 6.3 Making a banner
1. Draw the still art inside the template (`design/banners/diwali-static.html`, `banner-art.html`).
2. Run `design/banners/make_banner.py` (standard library only): it embeds the art as WebP and adds the animated parts
   and the text layers. Steps in `design/banners/README.md`.
3. Preview frame by frame (`frames.js`) and, once installed, on Home with the phone page's Test tab (5.14).
4. Add the design to `festive/Banners` and copy the JSON into the app's assets.

### 6.4 Playback on the TV
- `lottie-android` with `RenderMode.HARDWARE`, `setUseCompositionFrameRate(true)` and async updates off (measured
  on the Sony TV: 29.5 fps at 10.4 ms a frame; the other settings pushed frames over 33 ms).
- Text through Lottie's `TextDelegate`; the Inter fonts ship in the app. No blur effects.
- Plays only while Home is visible; stopped and released when Home is hidden.

## 7. Themes and settings

A theme is **the whole look of Home**. Every visible part of Home belongs to the theme: whether it is drawn, where,
and how. What a part *contains* (your apps and their order, photos, special days, the Watch Next items, the weather)
is **your content**, shared by every theme. Switching themes changes the drawing, never the content.

### 7.1 The parts of Home

| Part | Theme 1 draws it as | Your content (global) | Per theme |
| --- | --- | --- | --- |
| Top bar | Time, date, weather on the left; Inputs, Network, Settings on the right | Clock face, rolling digits, clock and date format, weather and location, what a Settings press opens | Top-button style |
| Wallpaper | Full-screen photo that dims when scrolled | Photos, rotation, special-day photos | — |
| Apps bar | Glass band of 6 to 8 tiles; focus starts here | Which apps are in the bar, and their order | How many it can show (Theme 1: 8) |
| Home apps | Grid under Continue watching | Which apps are Home or Hidden, and their order | — |
| Continue watching | One row of cards | What to show, limits, which apps, Read Play Next | On/off |
| Banner | Hanging cloth between the top bar and the apps bar | Special days' banners | On/off |
| Festive lights | Light strings behind the top bar | Special days' Lights | Mode (Off / Always on / On special days) |
| Eyes | Two pairs at random spots | — | On/off, personality, size |
| Sky | Sun or moon arc with the weather, behind the banner | Weather, city (for sunrise and sunset) | On/off |

- **Structural parts** (top bar, wallpaper, apps bar, Home apps) are drawn whenever the theme has them. **Optional
  parts** (Continue watching, banner, festive lights, eyes, sky) have an on/off per theme.
- Each theme declares which parts it draws and its limits (`home/Themes`). A theme may leave a part out, and may add
  its own parts with their own content and Settings page, which exist only in that theme.
- The Apps bar list holds up to 8 apps for every theme; a theme that shows fewer shows the first ones in order.
- A part the active theme doesn't draw: its per-theme settings are hidden (kept for switching back); its content
  pages stay, with a line saying the current theme doesn't show it.
- What works never changes: OK opens, hold OK shows App info, Back, the Home key, Inputs and resume go through shared
  code (`HomeTheme.Actions`).
- Per-theme values are saved per theme, so two themes can have different eye personalities or lights modes.
- Code: each theme is a class behind `HomeTheme`, built from shared part classes (`home/`; DeveloperNotes.md
  section 6, "Adding a theme"). Ideas for later themes: `design/concepts/` (Calm, Cinema, Dashboard, Rail) and a city
  theme (a city skyline poster with apps in lit windows).

## 8. Settings

The launcher's Settings has a left menu in groups, with **Phone** first (and open when Settings opens). Each control
applies instantly. On/off settings show a radio button (OK flips it); settings with several choices show the current
one with a dropdown arrow (OK opens the list, OK picks, Back closes). The phone page uses the same controls.

| Group | Page | Controls |
| --- | --- | --- |
| — | Phone | QR code and address for the phone page, PIN, change PIN |
| **Theme** (header: the active theme's name) | Theme | Choose the theme; Preview Home (5.14); Continue watching, Banner, Festive lights (mode), Eyes, Sky; top-button style; the parts always drawn |
| | Eyes | Personality, size, live preview (only while the theme has eyes) |
| **Your content** | Apps | Tabs Apps bar (up to 8, ordered), Home (ordered), Hidden; per app: move up, move down, move to another tab, App info |
| | Wallpapers | Photos (OK ticks, hold OK removes), rotation interval, order, cross-fade, wallpaper off, whether a special day's photo shows today |
| | Continue watching | Whether the theme shows it, what to show, items from each app, items in the row, Read Play Next, which apps |
| | Special days | Today's banner (OK previews it on Home), today's state, how many special days and the next date |
| | Time, date & weather | Weather on/off, weather from (Open-Meteo / OpenWeatherMap), location (automatic or by hand), °C/°F, clock face (digital / analog / analog with seconds hand), rolling digits, 12/24-hour clock, date format, status and refresh |
| | System | Settings button press, show the Wi-Fi name, default Home app, TV settings, About, Problems (safe mode and the last problem) |

**Phone page** tabs: **Settings** (every setting above, in the same groups, plus the weather city search and the
OpenWeatherMap key),
**Wallpapers** (add, tick, show now and remove photos), **Special days** (section 5.13), **Apps** (Apps bar / Home / Hidden and
order), **Test** (section 5.14: what Home shows now and why, and tests of the sky, banner and lights on Home), **Backup** (section 5.11, and Problems from 5.12). Changes from the phone show on the
TV page at once.

## 9. Installing, switching and checking with ADB

Run from the laptop (Windows PowerShell). One-time: Android SDK Platform-Tools; on the TV, Developer options ›
Network debugging. The usual way is `Scripts/Install-Package.ps1` ([README](../README.md)); the raw commands are:

```powershell
# Connect (accept the prompt on the TV the first time)
adb connect 192.168.1.50:5555   # your TV's IP address

# Install or update V2 (tvhome-v2.apk from the v2-latest GitHub Release). -r keeps settings; add -d for an older version.
adb install -r .\tvhome-v2.apk

# Make V2 the Home app, turn V1 off (kept installed for rollback), go Home
adb shell cmd package set-home-activity com.chakriau.tvhome.v2/.MainActivity
adb shell pm disable-user --user 0 com.chakriau.tvhome
adb shell input keyevent KEYCODE_HOME

# Roll back to V1
adb shell pm enable com.chakriau.tvhome
adb shell cmd package set-home-activity com.chakriau.tvhome/.MainActivity

# Screenshot (don't use `exec-out ... >` in PowerShell, it corrupts the PNG)
adb shell screencap -p /sdcard/tv.png
adb pull /sdcard/tv.png .\tv.png
adb shell rm /sdcard/tv.png

# 30-second screen recording (Netflix/Prime record black; try --size 1280x720 if it fails)
adb shell screenrecord --time-limit 30 --size 1920x1080 --bit-rate 8000000 /sdcard/tv.mp4
adb pull /sdcard/tv.mp4 .\tv.mp4
adb shell rm /sdcard/tv.mp4

# A crash's full log (the phone page's Backup tab has the launcher's own report too)
adb logcat -d -b crash
```

- Disable V1 when switching: V1's Home-button helper (an accessibility service) keeps sending Home to V1 otherwise.
- Keep V1 installed until V2 has run well for a while. Safe mode (5.12) covers a crashing V2, but not one that can't
  start at all; then roll back with the commands above.

## 10. Risks and open questions

- **Continue watching:** readable on the Sony TV with `READ_TV_LISTINGS`; Recently opened is the fallback for TVs
  that don't share Play Next.
- **Banner smoothness on a weak chip:** keep vector parts few, no blur; check new banners on the TV with "Preview
  banner on the TV".
- **Sky cost:** heavy rain is the busiest case (140 drops and 7 clouds at 30 fps); check it on the TV with the Sky
  tab's test. The sky can be switched off per theme.
- **Home key on other TVs:** on the Sony TV the Home key opens V2. Other TVs may need V1's
  accessibility-service approach (`HomeKeyService`, `ServiceRepair`).
- **Location with a VPN on:** IP location gives the VPN server's city, so it is skipped while a VPN is active.
- **Festival dates** need new lunar dates for 2031 on (`festive/Festivals`).
- **Memory:** the Sony TV reports 1,946 MB of RAM (about 739 MB free); keep the 1 GB budget anyway.
- **Open:** may the Apps bar hold fewer than 6 apps?
- **Open:** a name for Theme 1 (ideas: Hearth, Aangan, Utsav, Glasshouse); it is one line in `home/Themes`.

## 11. Design files

| File | What it shows |
| --- | --- |
| `design/theme1-home.png` | Theme 1 Home: top bar with icon + text buttons, festive lights, Diwali banner, eyes at random spots, apps bar, Continue watching peeking |
| `design/theme1-home-scrolled.png` | Home scrolled down: Continue watching with Resume, Home apps grid |
| `design/theme1-settings-apps.png` | Settings › Apps: Apps bar tab and the per-app menu |
| `design/concepts/` | Four early layout concepts (Calm, Cinema, Dashboard, Rail) for future themes |
| `design/banners/` | The four banners' Lottie files and still art, `make_banner.py`, art and preview scripts, `all-banners-frame.png` (see its README) |
| `design/sky/preview.html` | A browser copy of the sky over the Theme 1 mockup: every weather, sun or moon, any point of the arc (`?kind=RAIN&body=SUN&p=0.4`) |

App names, artwork and scores in the mockups are placeholders.
