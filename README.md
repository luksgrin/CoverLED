# CoverLED — a software notification LED for the Galaxy Z Flip cover screen

Older Galaxy phones had a notification LED: glance at the closed phone, know something is waiting,
guess the app from the color. CoverLED brings that back on the Galaxy Z Flip's cover screen.

**Tested on:** Galaxy Z Flip5 · Android 16 · One UI 8.0. No root, no Good Lock / MultiStar.

## What it does

| You… | The LED… |
|---|---|
| get a notification while the phone is closed | shows a dot in that app's color (up to 6 apps; a white 6th dot means "more") |
| dismiss / read the notification | disappears |
| **tap the dot** | hides and reveals Samsung's cover screen and notifications |
| **press the side key** to wake the cover | hides — you wanted the screen |
| leave the cover alone until it sleeps | comes back ~2 s after the screen goes dark |
| get a *new* notification while it's hidden | comes back immediately |
| open the phone | disappears; returns when you close it |
| plug in the charger | adds "⚡ 79 % · 36 min to full" under the dot |

### Colors
Like the original Galaxy LED, the color comes from the app when possible:
**your choice** → the color the notification declares for the LED (`ledARGB`, legacy) → the notification's
accent color → dominant color of the app icon → orange. Auto results are cached per app. The *App colors &
ignore list* screen lists every app that has notified: pick a color (or *Auto*), tick **Priority** so the
app always gets a dot, or **Ignore** so it never does.
(Notification-channel light colors are a system-only API and can't be read by a third-party app.)

### Beat, shape, arrangement, position
- **Beat style**: *Blink* (hard on/off), *Breathe* (fade in/out, default) or *Lub-dub* (strong pulse,
  pause, softer pulse). Beat length (default 1.4 s) and dark gap (default 2.5 s) are adjustable;
  blinking can be turned off entirely.
- **Arrangement** for several apps: *Row*, *Shape* (2 pair · 3 triangle · 4 square · 5 pentagon · 6 hexagon)
  or *One dot, cycling colors* (a single indicator that takes the next app's color on every beat).
  Dots are ordered priority apps first, then by when each app started notifying.
- **Dot size** 8–64 dp; **Brightness** of the indicator screen (default 5 %).
- **Custom shape**: load your own PNG instead of a circle; it is tinted with each app's color.
  Rules: transparent background (that *is* the shape), drawn in **white** (gray = dimmer),
  ≤ 1024×1024 px, ≤ 2 MB, square canvas works best. Stored downscaled to 128×128 privately.
- **Dot position**: drag the dot on a scaled outline of the cover, with a live preview on the real cover.

## Install and set up

1. Build (below) or take `app-debug.apk` from the latest GitHub Actions run, install it.
2. Open CoverLED and tap **Grant notification access** → allow CoverLED.
3. Tap **Allow display over other apps** → allow. This one is required: Android otherwise blocks an
   app in the background from putting anything on the screen, and that is exactly what the LED is.

Or over adb:
```bash
adb shell cmd notification allow_listener dev.lucas.coverled/dev.lucas.coverled.LedNotificationListener
adb shell appops set dev.lucas.coverled SYSTEM_ALERT_WINDOW allow
```

## How it works

```
Android notifications ─▶ LedNotificationListener ─▶ NotificationState (package → keys, first-seen order)
                                                          │
                            FoldState (hinge sensor) ─────┤
                                                          ▼
                                                IndicatorCoordinator
                          pending ∧ closed ∧ not snoozed → IndicatorController.show(colors)
                                                          │
                                                          ▼
                       CoverIndicatorActivity on the cover display (showWhenLocked, black, dots)
```

- The cover screen is a second Android display (id 1, 748×720 on the Flip5). The indicator is a
  black full-screen activity launched there with `ActivityOptions.setLaunchDisplayId` +
  `showWhenLocked`/`turnScreenOn`. Samsung's own AOD is system-only, so the panel is fully on while a
  dot is pending; the black background, low brightness and blinking keep the cost down.
- Fold state comes from the public hinge-angle sensor (`DeviceStateManager` is a system API here).
- Only package names and notification keys are stored — never notification content.

```
app/src/main/java/dev/lucas/coverled/
  LedNotificationListener.kt  NotificationListenerService; hosts the coordinator
  NotificationState.kt        pending notifications per package (SharedPreferences, no content)
  AppColors.kt                package → color (user / learned / icon), priority, ignore list
  FoldState.kt                closed/open via TYPE_HINGE_ANGLE
  IndicatorCoordinator.kt     show/hide/snooze decisions, re-show after screen-off
  IndicatorController.kt      launches/hides the indicator on the cover display
  CoverDisplays.kt            resolves the cover display
  CoverIndicatorActivity.kt   the LED: beat styles, cycling, charging line
  DotView.kt                  circles or custom shape, row / polygon layouts
  Settings.kt                 all user preferences
  ShapeLoader.kt              validates/imports the custom PNG shape
  MainActivity.kt             home: setup status + categories (also the adb debug entry point)
  SettingsActivity.kt         one category per screen: beat & brightness, layout & size, shape, developer
  ColorsActivity.kt           per-app color / priority / ignore editor
  PositionActivity.kt         drag-to-place the dot with live preview
  TestNotification.kt         our own test notification
  Insets.kt                   edge-to-edge padding helper (keeps content clear of the camera cutout)
```

## Build

Gradle 9.1 · AGP 8.13 · Kotlin 2.2 · JDK 17+ (Android Studio's bundled JDK works). Open the repo
root in Android Studio and run, or:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # once
./gradlew installDebug
```

CI (`.github/workflows/android.yml`) runs `assembleDebug` + `lintDebug` on every push and uploads the APK.

### Debugging over adb (phone can stay closed)
```bash
adb logcat -s CoverLED
adb shell cmd notification post -t Hi test1 "hello"                        # a real notification from another package
adb shell am start -n dev.lucas.coverled/.MainActivity --ei testnotif 1   # our own test notification (0 = cancel)
adb shell am start -n dev.lucas.coverled/.MainActivity --ez clearall true # dismiss everything pending
adb shell am start -n dev.lucas.coverled/.MainActivity --ei autoshow 3   # force 3 dots (bypasses state; 0 = hide)
adb shell screencap -d <physical id> /sdcard/c.png   # id from: adb shell dumpsys display | grep uniqueId
```

## Documents
- [`docs/spec.md`](docs/spec.md) — original specification, revised as the design changed
- [`docs/device-test-results.md`](docs/device-test-results.md) — what was verified on the device, and how

## Known limitations
- Can't use Samsung's low-power AOD (system-only), so a pending dot keeps the cover panel on.
  Battery impact not yet measured overnight.
- Channel light colors are unreadable by third-party apps; colors fall back to accent/icon.
- Tested on one device/firmware; other Z Flip generations may differ (display id, permissions).
