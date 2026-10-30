# Minimal Family TV Launcher

A lightweight Android TV / Google TV launcher with a top-left clock, top-right Wi-Fi strength indicator and Settings shortcut, wallpaper, and three remote-friendly app tiles: **YouTube, Netflix, and TiviMate**. No HDMI shortcuts. Setup status text disappears once the app is selected as Home.

Based on [badrikris/android-tv-launcher](https://github.com/badrikris/android-tv-launcher), upstream revision `ed931a9d32689da898db1ff1e08b0bd27e9c9ab9`. The upstream repository did not include a code license when this copy was prepared; this copy does not add a new license or grant additional rights.

## What is included

This repository ships with a neutral gradient background. No family photos, original upstream artwork, personal screenshots, APKs, signing keys, or local SDK paths are included. The app appears on the TV as **Home Test**. Version: 0.5.

## Build requirements

- JDK 17 (`JAVA_HOME` configured).
- Android SDK platform 35 and build tools; the Android Gradle plugin may also install build tools 34.
- Set `ANDROID_HOME` to your SDK, or create ignored `tv-home-test/local.properties` with `sdk.dir=C:/path/to/Android/Sdk` (use your real path).
- Internet access on the first build for Gradle dependencies.

On Windows, run from the repository folder:

```powershell
.\Build.ps1
```

Or build directly:

```powershell
cd tv-home-test
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

macOS/Linux: `cd tv-home-test`, then `chmod +x gradlew` and `./gradlew assembleDebug lintDebug testDebugUnitTest`.

APK output: `tv-home-test/app/build/outputs/apk/debug/app-debug.apk`.

## Add your own photo privately

Use a landscape JPG, PNG, or WebP, preferably **1920 x 1080 (16:9)** or smaller. Keep faces away from the bottom app row. The app uses `centerCrop`, so other aspect ratios may crop at the edges. Dark areas at the top and bottom help the clock and icons remain readable.

1. The tracked neutral background remains unchanged when you add a private wallpaper.
2. Keep your original photo outside the repository, or in the ignored `photos/` folder.
3. On Windows, run:

   ```powershell
   .\Set-Wallpaper.ps1 -PhotoPath "C:\path\to\your-photo.jpg"
   .\Build.ps1
   ```

   The script copies the image as `family_bg` into ignored `app/src/debug/res/drawable-nodpi/`. Android uses this private image for debug builds; the neutral main resource remains available in clean clones. Your source photo is not changed.
4. Install the newly built APK. Photos are embedded in APKs: do not publish your personal APK or screenshots.
5. To return to the neutral background, remove the copied `family_bg` image from the ignored debug resource directory and rebuild. Release builds continue to use the neutral background.

Manual setup on macOS/Linux: copy your photo to `app/src/debug/res/drawable-nodpi/family_bg.jpg` (or `.png` / `.webp`), under `tv-home-test/`. Keep the image ignored. The main resource does not need editing.

Never use `git add -f` on private images. `.gitignore` does not remove a photo that was already committed or tracked.

## Change the apps

Edit `TILE_ORDER` in `tv-home-test/app/src/main/java/dev/homescreen/test/HomeActivity.kt`. Current packages:

- YouTube: `com.google.android.youtube.tv`
- Netflix: `com.netflix.ninja`
- TiviMate: `ar.tvplayer.tv`

Apps must already be installed on the TV. Missing apps are skipped. Rebuild after changes.

## Install on a TV

Requires Android TV 12 or newer. Enable Developer options and debugging on the TV. Connect the PC and TV to the same network. On compatible TCL firmware:

```text
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r tv-home-test/app/build/outputs/apk/debug/app-debug.apk
```

Replace `TV_IP` with your TV address and accept the authorization prompt on the TV. Some firmware uses a different network debugging setup.

Open **Home Test**, check the tiles, then choose **Set as Home** and confirm in Android's dialog. If the Home button still opens Google TV, enable **Home Test redirect** in the TV's Accessibility settings. This optional service receives Google TV launcher window events and system Home-key/screen-power broadcasts; it cannot retrieve screen content. A brief Google TV flash may occur.

Home-button redirection targets `com.google.android.apps.tv.launcherx.home.HomeActivity`. It may need changes on other firmware. The screensaver is unchanged.

To undo: turn off Home Test redirect and uninstall Home Test. No root or system-app disabling is needed.

Debug APK updates must use the same signing key. A different computer's debug key may require uninstalling the old build before installing the new one.

## Version 0.3

- Clock at the top left; Wi-Fi indicator and Settings shortcut at the top right.
- Wi-Fi signal refreshes every five seconds while the launcher is visible; disconnected and Ethernet states are supported.
- Select the Wi-Fi icon to open network settings; select the gear to open TV settings. Use Up from an app tile to reach the header and Down to return.
- Uses normal ACCESS_NETWORK_STATE and ACCESS_WIFI_STATE permissions. No location permission or Wi-Fi scanning is requested.
- Home status text remains hidden once the app is selected as Home.
- Public APKs use the neutral background. Personal wallpaper APKs should stay private.

Download the latest public APK from this repository's GitHub Releases page. Install it over an earlier build only when its signing certificate matches; otherwise uninstall the older build first.
## Version 0.5: repeated Home presses and startup

Rapid repeated Home presses can return to Google TV without producing another window-state event. V0.5 also listens for system Home-key broadcasts and schedules the redirect 350 ms after the most recent press. Its startup handling tracks the current system boot count instead of relying on a three-minute boot window. Screen-off cancels pending launches, and ordinary same-boot service reconnects do not interrupt playback.

This Home-key broadcast behavior was verified on a TCL Smart TV Pro running Android 14; it is firmware-dependent. Under extreme continuous input, Google TV can remain visible for a few seconds before the launcher recovers. V0.5 does not guarantee an instantaneous transition or identical behavior on every TV.

### TCL auto-start permission

On the tested TCL firmware, the service could be listed as enabled while TCL blocked it from binding after startup. The launcher-specific `AUTO_START` operation was `ignore`. Check your TV before applying any command:

```text
adb shell cmd appops get dev.homescreen.test AUTO_START
```

If this operation exists and is blocked, allow auto-start for this launcher only and persist the setting:

```text
adb shell cmd appops set dev.homescreen.test AUTO_START allow
adb shell cmd appops write-settings
```

These are vendor-specific commands. If the operation is unknown, use the TV's per-app auto-launch settings or consult its firmware documentation; do not blindly alter other app permissions or disable system packages.

After updating the APK, toggle **Home Test redirect** off and on in the TV's Accessibility settings if it is listed as enabled but not running. Keep Home Test selected as the Home app. Some TCL firmware may still route Home to Google TV despite that role; the redirect handles that case.

For predictable startup on compatible TCL models, set **Settings > System > Power & Energy > Power On Behavior > Google TV Home Screen** rather than restoring an HDMI input. A brief Google TV screen can appear before Android starts the redirect.

### Verification

- `assembleDebug`, `lintDebug`, all 11 regression tests, and APK signature verification passed for both the private device build and the neutral-background public build.
- V0.5 was installed and tested on a TCL Smart TV Pro: repeated six-key bursts, 120 presses across twenty bursts, and sixty continuous Home events with bounded recovery.
- Settings and YouTube stayed open normally after the stress test. The final device run passed all 11 checks.
- An initial sixty-event check caught Google TV during a delayed transition. Repeated observations confirmed recovery by a later check around four seconds. The final run preserved the first observation and recovery delay rather than treating it as instantaneous.
- Home events were injected through ADB. Users should also verify their physical remote.
- Startup after OS restart and standby/remote wake were verified with the v0.4 startup implementation retained in v0.5. Physical unplug/replug was not performed.

The public release contains only the neutral background. Your photos, private APKs, signing keys, and local device connection information are not part of this repository.