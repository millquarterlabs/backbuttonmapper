# Back Button Mapper

A tiny Wear OS app for the Galaxy Watch Ultra 2 (and other Galaxy Watches) that makes a
**long press of the Back (lower) button open Google Wallet** instead of Samsung Wallet.
Short presses still go back as usual.

It works the same way the paid remapper apps do: an accessibility service listens for the
Back key, swallows it, and decides what to do:

- **Short press** → replayed as a normal "back".
- **Held for 0.5 s** → short buzz, Google Wallet opens.
- **Fallback:** if the watch firmware opens Samsung Wallet anyway (it handles the key before
  accessibility services see it), the service notices Samsung Wallet appearing right after a
  Back press and opens Google Wallet on top of it. Expect a brief flash of Samsung Wallet in
  that case.

The app has no internet permission and reads nothing on screen; it only sees the Back key and
the package name of the window that comes to the front.

## Get the APK

Every push builds the APK in GitHub Actions. Open the latest run of **Build APK** under the
repo's Actions tab and download the `backbuttonmapper-apk` artifact (a zip containing
`app-debug.apk`).

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
   adb install -r app-debug.apk
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

- **Samsung's firmware decides.** If a One UI Watch update routes the Back long press past
  accessibility services, only the fallback works (Samsung Wallet flashes, then Google Wallet
  opens).
- **Double press of Back.** Because the service replays short presses itself, Samsung's
  "double press" shortcut on the lower key may stop working while the service is on.
- **Samsung may switch the service off.** Some battery/"unused app" features disable
  accessibility services after updates or reboots. Open the app to check its status.
- **Google Wallet must be installed** on the watch and set up for payments.
- The fallback triggers on any Samsung Wallet window opened within 3 s of a Back press, so
  opening Samsung Wallet from the app list right after pressing Back also redirects.
