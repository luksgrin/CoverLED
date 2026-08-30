# CoverLED — a software notification LED for the Galaxy Z Flip cover screen

Older Galaxy phones had a notification LED: glance at the closed phone, know something is waiting,
guess the app from the color. CoverLED brings that back on the Galaxy Z Flip's cover screen.

**Tested on:** Galaxy Z Flip5 (Android 16, One UI 8.0) and Galaxy Z Flip7 (Android 16, One UI 8.5).
No root, no Good Lock / MultiStar.

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
- **Position**: drag the dot *and* the charging line on a scaled outline of the cover, with a live preview on
  the real cover. The shaded area marks the cameras — a hint only; you can place things there, at the
  risk of the cameras hiding part of them. By default the charging line sits just above it.

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

- The cover screen is a second Android display (id 1; 748×720 on the Flip5, 948×1048 on the Flip7). The indicator is a
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

## Device notes

Facts established on real hardware (Android 16), useful when adding another generation:

| | Z Flip5 · One UI 8.0 | Z Flip7 · One UI 8.5 |
|---|---|---|
| Cover display | id 1 · 748×720 · density 340 | id 1 · 948×1048 · density 420 |
| Camera cutout (cover) | bottom-right `(379,654)–(748,720)`, inset 66 px | bottom-right `(428,828)–(948,1048)`, inset 220 px |
| Background launch from the listener | blocked (`BAL_BLOCK`, procstate `BOUND_FOREGROUND_SERVICE`) until `SYSTEM_ALERT_WINDOW` is granted → `BAL_ALLOW_VISIBLE_WINDOW` | same; reason reported as `BAL_ALLOW_SAW_PERMISSION` |
| Good Lock / MultiStar needed | no | no |

Other things learned the hard way:
- `DisplayManager.getDisplays()` can omit the cover display while it is off — also query
  `DISPLAY_CATEGORY_PRESENTATION` and `getDisplay(1)` (see `CoverDisplays.kt`).
- The cover has a 5 s screen timeout; `FLAG_KEEP_SCREEN_ON` on the indicator is what keeps it lit.
- After a side-key wake, Samsung's lock screen / charging AOD sits above the indicator and it never
  resumes by itself — hence the snooze / re-show logic in `IndicatorCoordinator`.
- `DeviceStateManager` and `NotificationListenerService.getNotificationChannel` are system APIs on
  SDK 36; the hinge-angle sensor and `Notification.ledARGB`/`color` are the public substitutes.
- `adb shell screencap -d` takes the *physical* display id (`uniqueId` in `dumpsys display`), not `1`.

## Languages
The UI follows the system language. Included: English, Spanish, German, French, Italian, Portuguese,
Japanese (`res/values-<lang>/strings.xml`); the charging duration ("1 h 43 min") is formatted by ICU
for any locale. To add a language, copy `values-es/strings.xml` to `values-<lang>/` and translate.

## License
GPL-3.0 — see [`LICENSE`](LICENSE).

## Known limitations
- Can't use Samsung's low-power AOD (system-only), so a pending dot keeps the cover panel on.
  Battery impact not yet measured overnight.
- Channel light colors are unreadable by third-party apps; colors fall back to accent/icon.
- Tested on Flip5 and Flip7 only; other generations may differ (display id, permissions).
