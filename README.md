# TV Home V2 — an Android TV launcher

A home screen (launcher) for Android TV in plain Java, built for a 1 GB memory budget. Its look is a theme chosen in
its own Settings, every feature can be seen and switched off, and it is set up from a phone by scanning a QR code.

**Theme 1** (the first look) has:
- a photo wallpaper from your phone;
- a top bar with the time, date and weather, plus Inputs, Network and Settings;
- a sky where the sun or moon crosses Home with the weather;
- special-day banners, festive lights and two pairs of playful eyes;
- a glass Apps bar, Continue watching, and the rest of your apps in a grid.

| Folder | What it is |
| --- | --- |
| [`app/`](app/) | The launcher's source (package `com.chakriau.tvhome.v2`) and its unit tests |
| [`Documents/`](Documents/) | [`Requirements.md`](Documents/Requirements.md): what it does and why. [`DeveloperNotes.md`](Documents/DeveloperNotes.md): how it is built, enough for a person or an AI agent to work on it or rebuild Theme 1 |
| [`Scripts/`](Scripts/) | `Setup-Signing.ps1` (one-time signing key setup), `Install-Package.ps1` (install the newest build on the TV), `TvAdb.ps1` (helpers both use) |
| [`design/`](design/) | Mockups, layout concepts for future themes, the banner sources and generator, and a browser preview of the sky |
| `.github/workflows/` | **V2 release** (signed APK on every push to `main`) and **V2 check** (tests and a build on other branches) |

## What you need

- A Windows PC with PowerShell (the scripts are PowerShell; the build itself runs on GitHub).
- [GitHub CLI](https://cli.github.com/), signed in: `winget install GitHub.cli`, then `gh auth login`.
- A JDK 17 or newer for `keytool` (Android Studio ships one in its `jbr` folder), used once for the signing key.
- [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`) on your PATH.
- On the TV: Settings › Device Preferences › About › tap **Build** seven times, then Developer options ›
  **Network debugging** on. Note the TV's IP address (Settings › Network).

## Setting up your own copy

Run these from the repository folder in PowerShell. The scripts work out which GitHub repository they belong to
from your clone, so they work in any fork or copy. Pass `-Repo <owner>/<name>` (or set `$env:TV_REPO`) to choose
one yourself.

1. **Fork or copy the repository** to your GitHub account and clone it. Make sure GitHub Actions is enabled for it
   (the Actions tab).
2. **Create the signing key, once per repository:**

   ```powershell
   .\Scripts\Setup-Signing.ps1
   ```

   This creates a release keystore at `%USERPROFILE%\.android\tvhome-v2-release.jks`, with its password next to it
   in `tvhome-v2-release.password.txt`. It then stores both in the repository's GitHub Secrets (`V2_KEYSTORE_BASE64`,
   `V2_KEYSTORE_PASSWORD`, `V2_KEY_ALIAS`, `V2_KEY_PASSWORD`), where the **V2 release** workflow reads them. To
   add them by hand instead, see [Signing secrets](#signing-secrets).
   **Back up those two files.** Every build is signed with this one key, so each new build installs over the last
   and keeps its settings.

   *Moving to a new repository?* Run the same command in the new clone. If the keystore is already on this PC,
   it is reused, so builds from the new repository still install over the V2 already on your TV. A lost key means
   uninstalling V2 from the TV once before a build with a new key will install. Back up from the phone page first,
   then restore.
3. **Build:** push to `main` (or run **V2 release** from the Actions tab). It tests and builds a signed APK and
   publishes it as `tvhome-v2.apk` on the GitHub Release **v2-latest**, which each build replaces.
4. **Install on the TV:**

   ```powershell
   .\Scripts\Install-Package.ps1 -TvHost 192.168.1.50 -SetHome
   ```

   Use your TV's IP address. The first time, accept the debugging prompt on the TV. The script downloads the newest
   build, installs it as an update, grants the Continue watching permission and opens it. `-SetHome` makes it the
   Home app (needed once).
5. **Set up from your phone:** open the launcher's Settings (it opens on **Phone**) and scan the QR code with your
   phone's camera. The phone and TV must be on the same Wi-Fi.

### Install-Package.ps1 options

| Option | What it does |
| --- | --- |
| `-TvHost <ip>` | The TV's IP address. Set `$env:TV_HOST` once to leave it out |
| `-Repo <owner>/<name>` | The repository to download from (by default, the one this clone came from) |
| `-Apk <file>` | Install an APK you already have instead of downloading one |
| `-Branch <name>` | Use the newest build of another branch (default `main`) |
| `-SetHome` | Make V2 the Home app. If the earlier launcher (V1) is installed, it is turned off |
| `-RollbackToV1` | Make V1 Home again, if you have it. Installs nothing |
| `-OpenSettings` | Open V2's own Settings on the TV after installing |
| `-AllowDowngrade` | Allow installing a build older than the one on the TV |
| `-Reinstall` | Uninstall first. **This wipes V2's settings**, so use it only for a build signed with a new key |

## GitHub Actions and signing secrets

The APK is built on GitHub, not on your PC. Two workflows in [`.github/workflows/`](.github/workflows/) do it:

| Workflow | File | Runs when | What it does |
| --- | --- | --- | --- |
| **V2 check** | [`v2-check.yml`](.github/workflows/v2-check.yml) | A push to any branch except `main`, a pull request, or by hand | Runs the unit tests and builds an unsigned release APK, so a broken change is caught before it reaches `main`. Uses no secrets |
| **V2 release** | [`v2-release.yml`](.github/workflows/v2-release.yml) | A push to `main`, or by hand | Runs the tests, builds the APK signed with your key, checks the signature and publishes it as `tvhome-v2.apk` on the GitHub Release **v2-latest** |

Both skip pushes that only change `design/` or Markdown files. To run one by hand: the repository's **Actions** tab,
pick the workflow, then **Run workflow**.

**What V2 release does, step by step:**
1. Checks out the code and sets up Java 17 and Gradle on a GitHub-hosted Ubuntu machine.
2. Decodes the keystore from the `V2_KEYSTORE_BASE64` secret into a temporary file. If the secret is missing, the
   run stops with "No signing key in GitHub Secrets".
3. Runs `./gradlew test assembleRelease`; [`app/build.gradle`](app/build.gradle) signs the APK with the keystore
   and the other three secrets, which it reads from environment variables.
4. Checks that the APK really is signed (`apksigner verify`).
5. Deletes the previous **v2-latest** release and creates a new one with the APK, the commit and the run number.
   The run number becomes the app's version code, so each build installs as an update.
6. Deletes the temporary keystore, even if a step failed.

A Release is used rather than an Actions artifact because release files don't count against the Actions storage
quota. `Install-Package.ps1` downloads from this release.

### Signing secrets

The signing key never goes in the repository: `*.jks` and `*.password.txt` are in `.gitignore`. It lives on your PC
(in `%USERPROFILE%\.android\` by default) and, for the build, in the repository's **GitHub Secrets**.

| Secret | Value |
| --- | --- |
| `V2_KEYSTORE_BASE64` | The keystore file (`tvhome-v2-release.jks`) encoded as Base64 text |
| `V2_KEYSTORE_PASSWORD` | The password on the `Password:` line of `tvhome-v2-release.password.txt` |
| `V2_KEY_ALIAS` | `tvhome-v2` |
| `V2_KEY_PASSWORD` | The same password as `V2_KEYSTORE_PASSWORD` (the keystore uses one password for both) |

**The easy way:** `.\Scripts\Setup-Signing.ps1` creates the key if needed and sets all four (see
[Setting up your own copy](#setting-up-your-own-copy)).

**By hand, on the GitHub website:**
1. Copy the keystore as Base64 to the clipboard (PowerShell):

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\tvhome-v2-release.jks")) | Set-Clipboard
   ```

2. On GitHub, open the repository's **Settings › Secrets and variables › Actions** and choose
   **New repository secret**. Name it `V2_KEYSTORE_BASE64` and paste the clipboard as the value.
3. Add the other three the same way, with the values from the table.

**By hand, with the GitHub CLI** (from your clone; add `--repo <owner>/<name>` to choose another repository):

```powershell
$jks = "$env:USERPROFILE\.android\tvhome-v2-release.jks"
$pw  = ((Get-Content ($jks -replace '\.jks$', '.password.txt')) -match '^Password: ') -replace '^Password: ', ''
gh secret set V2_KEYSTORE_BASE64   --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)))
gh secret set V2_KEYSTORE_PASSWORD --body $pw
gh secret set V2_KEY_ALIAS         --body 'tvhome-v2'
gh secret set V2_KEY_PASSWORD      --body $pw
```

GitHub never shows a secret again after it is saved, so the files on your PC are the only copy you can read back up.
Keep them backed up. `gh secret list` shows which secrets are set, without their values.

**If a run fails:**
- *No signing key in GitHub Secrets:* `V2_KEYSTORE_BASE64` is missing. Add the secrets, then run **V2 release**
  again from the Actions tab.
- *A wrong password or alias* fails at the signing step (`assembleRelease`). Check the values against the table.
- *403 when publishing the release:* set the repository's **Settings › Actions › General › Workflow permissions** to
  **Read and write permissions**.
- *Nothing runs in a fork:* GitHub turns Actions off in new forks until you enable them on the Actions tab, and
  secrets are never copied to a fork, so set them up there too.

## Using it

**Opening V2's Settings:** hold OK on **Settings** at the top right of Home, or press the remote's **Menu** key. A
short press opens the TV's own settings; you can swap these in Settings › System. Focus starts on Settings while the
Apps bar is empty. From the PC: `.\Scripts\Install-Package.ps1 -OpenSettings`, or
`adb shell am start -n com.chakriau.tvhome.v2/.settings.SettingsActivity`.

**The phone page** has every setting, wallpaper photos, special days (only ever listed on the phone) and the apps.
It works while the launcher's Settings is open on the TV.

**Sky:** the sun or moon crosses Home with the weather's clouds, rain or snow (Settings › Theme › Sky). The phone
page's **Test** tab says what the sky shows now and why (the real weather reading behind it). It can also test any
weather, time of day, banner and lights on the TV's real Home for 15 minutes, with a "Test from the phone" label in
the corner. Settings › Theme › Preview Home shows Home the same way from the remote.

**Weather:** Open-Meteo by default, with no key needed. To use OpenWeatherMap, paste your own free API key on the
phone page's Settings tab. The key is stored only on the TV and in your backups.

**Special days** (birthdays, festivals, anniversaries) each set their own banner, lights and wallpaper in one form on
the phone. Festivals appear as suggestions to start from. "Show on the TV's Home" shows the banner on Home before
saving.

**Backup:** the phone page's **Backup** tab downloads everything you set up as a `.zip`: settings, apps and their
order, special days, and the photos if ticked. It restores the same way after a reinstall. Make one now and then,
and always before `-Reinstall`.

**Safe mode:** if the launcher crashes three times in ten minutes, Home starts as a plain list of apps. "Try normal
Home again" is on that screen, in Settings › System › Problems and on the phone page's Backup tab, which also shows
what went wrong. Installing a newer build ends it by itself.

**Wi-Fi name on the Network button:** Android shares it only with apps allowed to use location. Allow it in
Settings › System › Show the Wi-Fi name, or from the PC:
`adb shell pm grant com.chakriau.tvhome.v2 android.permission.ACCESS_FINE_LOCATION`.

**Continue watching** reads what other apps put in the TV's Play Next row, which needs the `READ_TV_LISTINGS`
permission. `Install-Package.ps1` grants it; Home also asks once, and Settings › Continue watching › Read Play Next
asks again. From the PC: `adb shell pm grant com.chakriau.tvhome.v2 android.permission.READ_TV_LISTINGS`.

## Upcoming features

- **Upcoming movies and shows:** a row below Continue watching with new and upcoming movies and shows.

## Building locally and contributing

`./gradlew test assembleRelease` (`.\gradlew.bat` on Windows) runs the unit tests and builds a release APK. Local
builds are unsigned unless `V2_KEYSTORE_FILE`, `V2_KEYSTORE_PASSWORD`, `V2_KEY_ALIAS` and `V2_KEY_PASSWORD` are set;
they are never signed with the debug key.

Read [`Documents/Requirements.md`](Documents/Requirements.md), then
[`Documents/DeveloperNotes.md`](Documents/DeveloperNotes.md). Work on a branch, let **V2 check** pass, then merge to
`main`.

## Licence

MIT: see [`LICENSE`](LICENSE). You may use, change and share it, keeping the licence text. The bundled Inter fonts
are under the SIL Open Font Licence ([`OFL.txt`](app/src/main/assets/fonts/OFL.txt)).
