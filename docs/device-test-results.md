# Phase 2 results — cover display indicator spike

**Device:** Galaxy Z Flip5 (SM-F731, serial ending …MZ9X) · Android 16 · One UI 8.0
**Date:** 2026-08-29 · **Build:** 0.1-phase2 (Gradle 9.1, AGP 8.13, Kotlin 2.2)
**Method:** driven over adb with the phone **closed and locked** the whole time
(`adb shell am start -n dev.lucas.coverled/.MainActivity --ei autoshow N`), verified with
`adb shell screencap -d <physical id>` on the cover panel and `dumpsys window/activity/power`.

| # | Test | Result | Evidence |
|---|---|---|---|
| 1 | Display enumeration | ✅ Two displays. Cover = logical id **1**, **748×720**, density 340, flags include `FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD`, `FLAG_PRESENTATION`. Physical id `4630947181303254916`. | `dumpsys display` |
| 2/4 | Launch indicator on cover while **closed + locked** | ✅ **Works on stock One UI 8.0, no Good Lock / MultiStar needed.** Activity placed on display 1 (`display-from-option=1`), no `Permission Denial`, no BAL block. | logcat `ActivityTaskManager`, screenshot |
| 5 | Survives cover-screen timeout | ✅ After 2.5 min still visible. `FLAG_KEEP_SCREEN_ON` → system holds `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager/displayId:1'` on our behalf. | `dumpsys power` |
| 6 | Interaction with Samsung cover AOD/clock | ⚠️ **Mixed.** Double-tap on the cover: dots stay (input swallowed). Side-key press: `screen_off`, indicator `onStop`; on wake Samsung's lockscreen/AOD (`AOD_SubUI_Charging` while charging) sits **above** our still-alive activity and it never resumes on its own. → Phase 5 must re-show after screen-off while pending; battery/charging info must be drawn by us (can't overlay Samsung's AOD). | manual test, `dumpsys window` |
| 8 | Three dots | ✅ Blue/green/purple rendered centered. | screenshot |
| — | Hide | ✅ Broadcast → `finishAndRemoveTask()`; window, task and wake lock gone; SubHome returns. | logcat, `dumpsys` |
| 9 | Overnight battery | ⏳ Not yet measured. Expect measurable drain: the panel is fully ON (not AOD/doze) at `screenBrightness=0.05`. | — |

## Gotchas discovered

1. **`DisplayManager.getDisplays()` omitted the cover display** the first time (returned only id 0 while the
   phone was closed and display 1 was OFF). Fixed by also querying `DISPLAY_CATEGORY_PRESENTATION` and
   `getDisplay(1)` directly (`CoverDisplays.kt`).
2. **5 s cover timeout race.** The first launch got `onStop` almost immediately: the cover's 5 s screen
   timeout fired (`dumpsys power` history: `OFF timeout dev.lucas.coverled`) before the window was
   attached. Subsequent launches held the screen. Worth watching; may need `setTurnScreenOn` +
   an explicit `acquire` on first frame if it recurs.
3. **`setTurnScreenOn` wakes the whole power group**, i.e. the (closed) main display also went ON
   briefly. In this test that was partly the debug path (MainActivity on display 0). Verify again in
   Phase 5 when the listener launches the indicator directly, without MainActivity.
4. `adb shell screencap -d` needs the **physical** display id (`uniqueId` number), not `1`.
5. `am start` cannot target the indicator directly (`exported=false`, by design); go through
   `MainActivity --ei autoshow N` (0 = hide).

## Decision

**Go.** The gating question (spec §20 Q3) is answered yes. Proceed to Phase 3 (NotificationListenerService).
Open items carried forward: re-show after screen-off (Phase 5), replicate charging/battery info in the indicator (user wants to keep it), Q6 battery drain (plan a duty cycle).

---

# Phases 3–7 results — listener → state → indicator (same day)

Built and tested 2026-08-29 evening, phone closed + locked throughout, driven over adb.

| Spec phase | Result | Evidence |
|---|---|---|
| 3 Notification listener | ✅ `LedNotificationListener` connects after `cmd notification allow_listener …`; seeds from `activeNotifications`; ongoing / FGS / group-summary notifications filtered out. | logcat `listener connected`, `posted <pkg>` |
| 4 State manager | ✅ `NotificationState` (pkg → keys, SharedPreferences, no content stored). | logcat `state: com.android.shell=1` |
| 5 Wire to indicator | ✅ `IndicatorCoordinator`: `pending ∧ closed → show`, else hide. **Blocked at first by Android BAL** (`callingUidProcState: BOUND_FOREGROUND_SERVICE → BAL_BLOCK`). **Fixed by declaring + granting `SYSTEM_ALERT_WINDOW`** → `BAL_ALLOW_VISIBLE_WINDOW`. | logcat `ActivityTaskManager` |
| 5 Fold state | ✅ `TYPE_HINGE_ANGLE` sensor (wake-up), closed = angle < 10°. `DeviceStateManager` is still a system API on SDK 36; Jetpack WindowManager needs a UI context. | logcat `hinge sensor registered` |
| 6 Color mapping | ✅ `AppColors` defaults (WhatsApp blue, Gmail green, Calendar purple, dialer red, other orange, own test white); persisted, editable later. | screenshot: orange dot for `com.android.shell`, white for own |
| 7 Clearing | ✅ `onNotificationRemoved` → state → HIDE → `finishAndRemoveTask`. Tested via listener `cancelNotification` (reason 10/16) and app `cancel()` (reason 8). | logcat |
| 8 Multiple apps | ✅ Up to 4 distinct colors, sorted by priority list. (Rendered in Phase 2 test; logic untested with 2+ real apps.) | — |
| extra | ✅ Battery line while charging: “⚡ 79 % · 36 min to full” drawn under the dot (replaces Samsung’s charging AOD info, which cannot be overlaid). | screenshot |

## Setup a real user must do (both are one-tap buttons in the app)
1. Settings → Notifications → **Notification access** → CoverLED → allow.
2. Settings → Apps → CoverLED → **Display over other apps** → allow. Without it the LED launch is silently blocked (Android background-activity-launch rule).

## UX iteration (user test, same evening)
User feedback: after a side-key wake the LED sat on top of the keyguard and blocked reading notifications;
double-tap did nothing; open→close showed Samsung's AOD for a while before the dot.
Fix — "LED yields to the user" model in `IndicatorCoordinator`: tap on the dot → snooze + reveal cover UI;
`SCREEN_ON` not caused by our own launch (> 2.5 s after it) → snooze; `SCREEN_OFF` → un-snooze, re-show after 2 s;
new notification → un-snooze. Hinge sensor at UI rate, closed threshold 15°. Verified over adb (see README table).

## Still open
- Overnight battery measurement still pending. Blink duty cycle (0.8 s on / 3 s off default) and adjustable
  brightness implemented in `Settings.kt` / `CoverIndicatorActivity` — measure with blink on vs off.
- `setTurnScreenOn` waking the main display — re-check when the launch comes from the listener (no MainActivity involved). Not observed in the adb-driven e2e runs (`display 0 … OFF` in logs), so likely a debug-path artifact.
- Per-app color editing UI; ignore-list for apps.


---

# Galaxy Z Flip7 (SM-F766Q) · Android 16 · One UI 8.5 — 2026-08-30

Same APK as built for the Flip5, no code changes needed for the core path.

| Check | Result |
|---|---|
| Displays | main 1080×2520 (id 0); cover **948×1048** (id 1), density 420, `FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD` present |
| Fold detection | hinge sensor: 180° → open, 0° → closed ✅ |
| Listener + permissions | `allow_listener` + `SYSTEM_ALERT_WINDOW` appop as on Flip5 ✅ |
| Launch from listener, phone closed + locked | ✅ BAL reason now reported as `BAL_ALLOW_SAW_PERMISSION` (8.5 wording; Flip5/8.0 said `BAL_ALLOW_VISIBLE_WINDOW`) |
| Dot + charging line rendered | ✅ |
| Camera cutout | Cameras are **inside** the cover panel: cutout bounds `(428,828)–(948,1048)`, bottom inset 220 px. The charging line overlapped it → indicator now pads by the display-cutout insets (also helps Flip5, whose cutout is 66 px bottom-right). |

---

# Re-check on Flip5 after the Flip7 work — 2026-08-30
Latest build (One UI interface, automatic charging-line default, translations) installed over the
existing one: grants persisted, listener rebound by itself, dot + charging line rendered on the closed
cover. Charging line default landed at y≈605 of 720, above the 66 px cutout (starts at 654). Spanish
strings shown ("carga completa").
