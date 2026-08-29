# CoverLED — software notification LED for the Galaxy Z Flip cover screen

Spec: [`samsung_z_flip_notification_led_spec.md`](samsung_z_flip_notification_led_spec.md)

**Target device:** Galaxy Z Flip5 · Android 16 · One UI 8.0
**Status:** Phases 1–9 ✅ working end-to-end on device (see [`PHASE2_RESULTS.md`](PHASE2_RESULTS.md)). Phase 10 polish: blink duty cycle + brightness ✅, per-app colors + ignore list ✅, overnight battery measurement ⏳.

## How it works

```
Android notifications ─▶ LedNotificationListener ─▶ NotificationState (pkg → keys)
                                                          │
                          FoldState (hinge sensor) ───────┤
                                                          ▼
                                               IndicatorCoordinator
                                     pending ∧ closed → IndicatorController.show(colors)
                                                          │
                                                          ▼
                     CoverIndicatorActivity on display 1 (showWhenLocked, black, dots, battery line)
```

```
app/src/main/java/dev/lucas/coverled/
  LedNotificationListener.kt  NotificationListenerService; hosts the coordinator
  NotificationState.kt        pending notifications per package (SharedPreferences, no content)
  AppColors.kt                package → color (user / learned / icon), ignore list, seen apps
  ColorsActivity.kt           per-app color editor + ignore list
  FoldState.kt                closed/open via TYPE_HINGE_ANGLE
  IndicatorCoordinator.kt     show/hide decision, re-show after screen-off
  IndicatorController.kt      launches/hides the indicator on the cover display
  CoverDisplays.kt            resolves the cover display
  CoverIndicatorActivity.kt   the LED (+ charging info while plugged in)
  DotView.kt                  draws the dots
  Settings.kt                 blink / brightness / battery-line preferences
  MainActivity.kt             setup buttons, settings, debug console
```

## Behavior (LED semantics)

| You do… | The LED… |
|---|---|
| get a notification while the phone is closed | shows a dot in that app's color (up to 4 apps) |
| dismiss / read the notification (phone open or from the cover) | disappears |
| **tap the dot** | hides and reveals Samsung's cover screen / notifications |
| **press the side key while the dot is dark → screen wakes** | hides — you wanted the screen, read your notifications |
| leave the cover alone until it sleeps | comes back ~2 s after the screen goes dark |
| get a *new* notification while hidden | comes back immediately |
| open the phone | disappears; returns when you close it (hinge < 15°) |
| plug in the charger | adds "⚡ 79 % · 36 min to full" under the dot (toggle in settings) |

### Colors
Like the original Galaxy LED, the color comes from the app when possible. Resolution order per app:
**your choice** (App colors screen) → color the notification declares for the LED (`ledARGB`, legacy) →
the notification's accent color → dominant/vibrant color of the app icon → orange. Auto results are
cached per app; "Auto" in the editor resets them. Apps show up in the editor after their first
notification; tick **Ignore** to keep an app from lighting the LED.
(Channel light colors are a system-only API, so they can't be read by a third-party listener.)

### Power settings (in the app)
- **Blink** (default on): dots visible 0.8 s, dark 3 s; both adjustable. The battery line does not blink.
- **Brightness** (default 5 %): window brightness while the LED is showing.
Changes apply live to a visible LED. Note the panel itself stays on while a dot is pending — this is
not Samsung's low-power AOD — so blink + low brightness are what keep the cost down.

## First-run setup on the phone
Open CoverLED and tap **Grant notification access** and **Allow display over other apps**
(the second one is required — Android otherwise blocks the LED launch from the background).
Or via adb:
```bash
adb shell cmd notification allow_listener dev.lucas.coverled/dev.lucas.coverled.LedNotificationListener
adb shell appops set dev.lucas.coverled SYSTEM_ALERT_WINDOW allow
```

## Build & install

Toolchain: Gradle 9.1 · AGP 8.13 · Kotlin 2.2 · JDK 17–25 (Android Studio's bundled JBR works).

Android Studio: open the **repo root**, let it sync, ▶ Run with the Flip5 selected.

Terminal (uses Android Studio's JDK; adjust if you have another):
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # once
./gradlew installDebug
adb shell am start -n dev.lucas.coverled/.MainActivity
```

### Driving it over adb (phone can stay closed)
```bash
adb shell cmd notification post -t Hi test1 "hello"                        # real e2e: another app's notification
adb shell am start -n dev.lucas.coverled/.MainActivity --ei testnotif 1   # post our own test notification (0 = cancel)
adb shell am start -n dev.lucas.coverled/.MainActivity --ez clearall true # dismiss all pending
adb shell am start -n dev.lucas.coverled/.MainActivity --ei autoshow 3   # force 3 dots (bypasses state)
adb shell am start -n dev.lucas.coverled/.MainActivity --ei autoshow 0   # hide
adb logcat -s CoverLED
# screenshot the cover panel (physical id from `adb shell dumpsys display | grep uniqueId`)
adb shell screencap -d 4630947181303254916 /sdcard/cover.png && adb pull /sdcard/cover.png
```

## Phase 2 test protocol

Log filter: `adb logcat -s CoverLED`

| # | Step | Records spec question |
|---|---|---|
| 1 | Open the app. Note the display list — expect two entries, the `[cover?]` one ≈ 720x748. | §20 Q2 |
| 2 | Tap **Show dot on cover NOW** with the phone open. Peek at the cover: does a blue dot show? | baseline |
| 3 | Tap **Hide indicator**. Dot must disappear. | — |
| 4 | Tap **Show dot on cover in 8 s**, close the phone, wait. **Does the dot appear on the closed, locked cover screen?** | §20 Q3 (the gating question) |
| 5 | Leave it closed 2–3 min. Does the dot survive the cover-screen timeout? Check logcat for `Indicator onStop`. | §20 Q4 |
| 6 | Double-tap / wake the cover. Does Samsung's clock/AOD replace the dot, or does the dot return? | §5.4 risk 3 |
| 7 | Open the phone. Is the indicator visible on the main screen (it shouldn't be)? Tap Hide. | — |
| 8 | Repeat 4 with **Show 3 dots in 8 s**. | §3.5 |
| 9 | Overnight: show the dot, close the phone, note battery % before/after 8 h. Compare with a night without it. | §20 Q6 |

### If step 4 fails (nothing on the cover, or `SecurityException` in the log)

1. Install **Good Lock** from Galaxy Store → **MultiStar** → *I ♥ Galaxy Foldable* →
   **Launcher Widget** / cover-screen app allowlist → enable **CoverLED**. Retry step 4.
2. If it still fails, note the exact logcat error and see spec §5.5 (overlay fallback).

Record results in `PHASE2_RESULTS.md` (step number → observed behavior) so the
spec's §5.4 risks can be closed out before Phase 3 (notification listener).

## Roadmap

Phase 3 `NotificationListenerService` → Phase 4 state manager → Phase 5 wire to
`IndicatorController.show/hide` gated on fold state → Phase 6 per-app colors →
Phase 7 clearing → Phase 8 multiple dots → Phase 9 tap → Phase 10 polish.

## CI

`.github/workflows/android.yml` builds `assembleDebug` + `lintDebug` on every push/PR and
uploads `app-debug.apk` as a workflow artifact (Actions tab → run → Artifacts), so an
installable APK is always available without a local SDK. Local Android Studio + adb is
still required for the device test protocol (logcat).
