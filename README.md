# LAUNCHER

Minimal by default. Instant when you need it.

LAUNCHER is a monochrome, cyberpunk-corpo Android application launcher. The
screen stays completely empty until you act. One tap expands a full-screen
honeycomb of your installed apps, growing outward from the center as a
complete, symmetric hex shape, with per-app color tagging and a fast
outside-in reveal built for clarity over clutter.

This repository holds both the real Android app and the HTML/CSS/JS
interaction prototype (`demo.html`, `statusbar-demo.html`,
`phone-demo-redmagic11pro.html`) used to lock in the visual direction before
implementation. The prototype stays in the repo as a design reference.

------------------------------------------------------------
STATUS / LICENSE
------------------------------------------------------------

Open-source software licensed under the GNU General Public License v3.0.

Early-stage. The Android app covers the core launcher loop — real installed
apps, real launching, HOME registration, persistence, immersive mode. Search,
folders, notification badges, wallpaper support, and accessibility polish are
not built yet.

------------------------------------------------------------
SUPPORT THE PROJECT
------------------------------------------------------------

Ko-fi: https://ko-fi.com/eversorhn
PayPal: https://paypal.me/FAMarco
Bitcoin: `bc1qv92c3eyeqvhgfnez7spfd7v2aytkhpshsl65yv`

------------------------------------------------------------
LOCAL FIRST / PRIVATE BY DESIGN
------------------------------------------------------------

- Runs entirely on your device. No backend, no server component.
- No account, no telemetry, no analytics.
- No network access beyond reading your device's own connectivity/signal
  state for the status readout — nothing is sent anywhere.
- Nothing you do in the app leaves your phone.

------------------------------------------------------------
OFFICIAL SOURCE
------------------------------------------------------------

Author: eVersor-HN
Official repository: https://github.com/eVersor-HN/LAUN

------------------------------------------------------------
DOWNLOAD / INSTALL
------------------------------------------------------------

Android app:
1. Build from source (see BUILD FROM SOURCE below) or wait for a tagged
   release.
2. Install the APK on your device.
3. Select LAUNCHER as your default Home app when Android prompts you, or
   set it manually in system settings.

HTML prototype (design reference only, not the real app):
1. Open `demo.html` directly in a modern browser.
2. Click anywhere on the empty screen to expand the launcher grid.

------------------------------------------------------------
FEATURES
------------------------------------------------------------

CONTROL
- Empty by default — the launcher only appears when you ask for it.
- Adjustable tile size and tile count, always a complete symmetric honeycomb.
- Per-app color tagging via long-press, without losing the label.
- Settings (size, count, HUD visibility, immersive mode) persist across
  restarts.

WORKFLOW
- Full-screen honeycomb grid, spaced evenly, growing outward from the center.
- Fast outside-in reveal motion tuned for a single, deliberate open action.
- Press and drag across tiles to preview, release to launch.
- Tap the background (not a tile) to collapse the grid again.

PRIVACY
- Fully local. Nothing is transmitted, stored remotely, or tracked.

------------------------------------------------------------
BUILD FROM SOURCE
------------------------------------------------------------

Requirements: JDK 17, Android SDK (compileSdk/targetSdk 36, minSdk 26).

```
git clone https://github.com/eVersor-HN/LAUN.git
cd LAUN
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Install it with `adb install app-debug.apk`, or open the project in Android
Studio and run it directly.

The HTML prototype needs no build step — `demo.html` and its variants are
static files you can open directly in a browser.

------------------------------------------------------------
SECURITY / PRIVACY NOTES
------------------------------------------------------------

- Reads your installed-app list and icons via the standard Android
  `PackageManager` — required for any launcher, used only to display and
  launch your own apps.
- Reads battery level and network connectivity state for the status
  readout; requests the `ACCESS_NETWORK_STATE` permission for this and
  nothing else.
- No user data is collected, stored remotely, or transmitted.

------------------------------------------------------------
SYSTEM REQUIREMENTS
------------------------------------------------------------

- Android 8.0 (API 26) or newer.
- No GPU, minimum memory, or special hardware requirement.
- The HTML prototype runs in any current desktop browser supporting CSS
  `clip-path` and custom properties.

------------------------------------------------------------
LICENSE
------------------------------------------------------------

GNU General Public License v3.0. See [LICENSE](LICENSE) for the full text.

------------------------------------------------------------
THIRD-PARTY NOTICES
------------------------------------------------------------

- Typefaces "Chakra Petch" and "JetBrains Mono", used in the HTML prototype
  via Google Fonts, each under the SIL Open Font License 1.1.
- Android app built on AndroidX / Jetpack Compose (Apache License 2.0).
