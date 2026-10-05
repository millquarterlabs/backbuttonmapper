# Back Button Mapper

A tiny Wear OS app for the Galaxy Watch Ultra 2 (and other Galaxy Watches) that makes a
**long press of the Back (lower) button open Google Wallet** instead of Samsung Wallet.
Short presses still go back as usual.

It uses an accessibility service. On the Ultra 2 the firmware handles the long press itself
before any app can stop it, and always opens something: Samsung Wallet (often several windows
in a row) or Android's "Default wallet app" picker. The service leaves the button alone, waits
for that burst of windows to settle, and then brings Google Wallet to the front, once. A black
overlay covers the screen from 0.4 s into the press until Google Wallet is on top (at most
3 s), so Samsung's screens stay hidden. Short presses are untouched.

### Recommended: disable Samsung Wallet for a clean "direct mode"

The flicker exists only because Samsung Wallet's watch app owns the long press. Disable it
(reversible, no root) and the firmware has nothing to open; the app then opens Google Wallet as
soon as you hold the button, with no overlay. Short presses are left completely to the watch. The app switches modes on its own
and shows the current one on its main screen.

```sh
adb shell pm disable-user --user 0 com.samsung.android.samsungpay.gear
# undo:
adb shell pm enable com.samsung.android.samsungpay.gear
```

A Samsung Wallet update can re-enable it; the app then falls back to the overlay mode until
you run the command again. While Samsung Health is on screen the app ignores the button in both
modes, so holding it still ends a workout.

The main screen shows an event log (keys and windows the service saw), which helps when
something doesn't work.

The app has no internet permission and reads nothing on screen; it only sees the Back key and
the package name of the window that comes to the front.

## Get the APK

Every push builds the APK in GitHub Actions and publishes it to the `latest` pre-release:

https://github.com/millquarterlabs/backbuttonmapper/releases/download/latest/backbuttonmapper.apk

To build locally instead: install Android Studio (or the Android SDK + JDK 17) and run
`./gradlew assembleDebug`. The APK lands in `app/build/outputs/apk/debug/`.

## Install on the watch (adb over Wi-Fi)

1. On the watch: **Settings › About watch › Software information**, tap **Software version**
   7 times to unlock Developer options.
2. **Settings › Developer options**: turn on **ADB debugging** and **Debug over Wi-Fi**.
   Keep the watch on the same Wi-Fi network as your computer (turn Bluetooth off briefly if
   the watch won't stay on Wi-Fi).
3. Under **Wireless debugging**, tap **Pair new device** and note the pairing code and
   IP:port, then on your computer (needs
   [platform-tools](https://developer.android.com/tools/releases/platform-tools)):

   ```sh
   adb pair <ip>:<pairing-port>        # enter the code from the watch
   adb connect <ip>:<port>             # the port shown on the Wireless debugging screen
   adb install -r backbuttonmapper.apk
   ```

4. Open **Back Button Mapper** on the watch, tap **Open accessibility settings**, and turn the
   service on (on Galaxy Watches it's under **Accessibility › Installed apps** or similar).

   If the watch's settings make that awkward, enable it from adb instead. This *replaces* the
   list of enabled accessibility services, so only do it if you don't use others:

   ```sh
   adb shell settings put secure enabled_accessibility_services \
     com.millquarterlabs.backbuttonmapper/.BackButtonService
   adb shell settings put secure accessibility_enabled 1
   ```

5. Turn ADB debugging off again when you're done (it drains battery).

Updates install over the old version with `adb install -r` (the debug signing key is
committed, so every build has the same signature) and the service stays enabled.

## Known limits

- **Without direct mode, the screen goes black for about a second** during a long press while
  Samsung's screens open and close underneath.
- **Samsung may switch the service off.** Some battery/"unused app" features disable
  accessibility services after updates or reboots. Open the app to check its status.
- **Google Wallet must be installed** on the watch and set up for payments.
- While the service is on, Samsung Wallet can't be opened at all: every Samsung Wallet window
  is redirected to Google Wallet.
