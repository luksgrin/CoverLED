# Specification: Notification LED-Style Indicator for the Samsung Galaxy Z Flip Cover Screen

> **Revision note (2026-08-29):** Originally scoped as a Flex Window *widget*. Research (see §0) showed that Flex Window widgets live on a swipeable panel and cannot deliver a glance-without-interaction indicator, and that existing AOD-based LED apps (aodNotify, LED Blinker) only draw on the main display. The architecture was therefore changed to a **cover-display overlay activity** driven by a notification listener. Sections marked *(revised)* reflect this.

## 0. Prior-art research summary *(new)*

Before implementation, existing solutions were surveyed (2026-08-29):

| Option | Verdict |
|---|---|
| Stock One UI cover-screen dot | Single red/orange dot, no per-app color. Not sufficient. |
| Samsung Flex Window widget (official codelab) | Widgets appear only on the swipeable widget panel, not on the idle clock/AOD face. Fails the "glance without swiping" goal. |
| aodNotify / LED Blinker / Notification Light for Samsung | Hook Samsung's AOD on the **main** display. **Tested on device: only works with the phone open.** Not viable. |
| CoverScreen OS | Does per-app colored LED/edge indication on the closed cover screen (Flip 3–7), but is a full cover-screen replacement OS with a subscription. **Rejected as too heavy.** |
| flipbase (GitHub) | Bare widget template, no notification logic. Not relevant after the architecture change. |

Conclusion: no lightweight existing app covers the use case. A custom build is justified, using the cover-display activity approach that CoverScreen OS demonstrates is technically possible on stock firmware.

---

## 1. Overview

### 1.1 Project goal

Recreate the useful behavior of the notification LED found on older Samsung Galaxy devices, such as the Galaxy S4, by using the external/cover display of a Samsung Galaxy Z Flip.

The desired experience is:

> **Phone closed + locked → a small, unobtrusive colored indicator tells the user that something is waiting, without requiring the phone to be opened.**

The system should identify which application has an unread notification and represent that state visually on the Flex Window.

### 1.2 Motivation

Older Samsung phones included a physical notification LED. A user could glance at the phone and immediately know that an unread notification existed, and often infer which application generated it from the LED color.

The proposed project brings that same glanceable concept to a modern Samsung Z Flip, using the external display instead of a dedicated LED.

---

# 2. Target Platform

## 2.1 Device

**Confirmed target (2026-08-29): Galaxy Z Flip5, Android 16, One UI 8.0.** Other generations are out of scope until the Flip5 works.

**Important:** The exact Z Flip generation must be established before implementation because Flex Window capabilities and APIs differ between generations.

Examples include:

- Galaxy Z Flip 3
- Galaxy Z Flip 4
- Galaxy Z Flip 5
- Galaxy Z Flip 6
- Galaxy Z Flip 7
- Later supported models

## 2.2 Development environment

Recommended development stack:

- Android Studio
- Kotlin
- Android SDK
- Physical Samsung Galaxy Z Flip for testing
- USB debugging enabled

Additional libraries:

- Jetpack WindowManager (`androidx.window`) for fold-state detection (`FoldingFeature`)
- Optional: Samsung Good Lock → MultiStar as a fallback to allow the app on the cover screen if One UI blocks direct launch

Samsung's Flex Window widget Code Lab is **no longer the reference implementation** (see §0); it remains useful only for cover-display dimensions and `launchDisplayId` behavior.

---

# 3. Functional Requirements

## 3.1 Notification detection

The application shall monitor Android notifications from other applications.

The recommended Android mechanism is:

`NotificationListenerService`

The service should be capable of determining:

- Which application generated a notification
- Whether a notification is currently relevant/unread
- Whether multiple applications have notifications
- Whether notifications have subsequently been dismissed/cleared

Example internal state:

```text
WhatsApp    → unread
Gmail       → unread
Slack       → unread
Calendar    → unread
```

## 3.2 Application identification

Notifications should be associated with their originating application package.

The application should maintain a mapping such as:

```text
com.whatsapp        → WhatsApp
com.google.android.gm → Gmail
com.slack            → Slack
```

The exact application/package mappings should be configurable rather than hard-coded wherever practical.

## 3.3 Color association

Each application should be assignable a notification indicator color.

Example:

| Application | Indicator |
|---|---|
| WhatsApp | Blue |
| Gmail | Green |
| Calendar | Purple |
| Missed call | Red |
| Other | White/orange |

The initial prototype should support a simple configurable mapping.

*Implemented (2026-08-30):* user override → notification `ledARGB` (what the Galaxy LED honored) → notification accent color → dominant icon color (Palette) → default orange. `NotificationChannel.lightColor` is a system-only API for listeners and cannot be used.

## 3.4 External-screen indicator

The application shall display notification status on the cover display via an indicator activity (see §5).

The initial prototype should be deliberately minimal.

Example:

```text
        12:38

          ●
```

The dot should indicate that one or more relevant notifications are waiting.

## 3.5 Multiple notifications

The design should support multiple applications having unread notifications.

Possible representations include:

### Multiple dots

```text
        12:38

        ● ● ●
```

For example:

- Blue = WhatsApp
- Green = Gmail
- Purple = Calendar

### Highest-priority indicator

Alternatively, show only one indicator corresponding to the highest-priority notification.

This can be implemented later; the first prototype only needs to establish reliable notification detection and display.

---

# 4. User Experience

## 4.1 Locked-device experience

The core use case is:

1. User closes the Z Flip.
2. Phone remains locked.
3. A notification arrives.
4. The notification listener detects it.
5. The application updates its notification state.
6. The Flex Window displays a small colored indicator.
7. User can glance at the external screen without opening the phone.
8. The indicator remains until the notification is considered cleared/read according to the application's defined behavior.

## 4.2 Desired visual design

The indicator should be:

- Small
- Unobtrusive
- High contrast
- Easy to identify at a glance
- Suitable for the dimensions and shape of the specific Z Flip Flex Window
- Low power where possible

The first version should prioritize functionality over visual polish.

---

# 5. Cover Display Integration *(revised)*

## 5.1 Why not a Flex Window widget

Samsung's widget mechanism (`samsung-appwidget-provider display="sub_screen"`, `widgetCategory="keyguard"`) places widgets on a panel the user must swipe to. The idle face of the closed phone is Samsung's clock/AOD surface, which is not open to third-party drawing. A widget therefore cannot replicate a notification LED.

## 5.2 Chosen mechanism: activity launched on the cover display

The cover screen is a second Android display (display id `1` on current Z Flip models). A third-party app can present an `Activity` on it:

```kotlin
val options = ActivityOptions.makeBasic().apply {
    launchDisplayId = coverDisplayId   // resolve via DisplayManager, do not hard-code 1
}
startActivity(indicatorIntent, options.toBundle())
```

The indicator activity:

- calls `setShowWhenLocked(true)` and `setTurnScreenOn(true)` so it is visible while the device is locked
- uses a fully black background with only the dot(s) drawn (OLED: black pixels ≈ off)
- is `excludeFromRecents`, `noHistory`, single-instance
- is launched by the state manager only when **(a)** the device is folded closed and **(b)** at least one pending notification exists
- finishes itself when the pending set becomes empty or the device is opened

## 5.3 Fold-state detection *(revised after Phase 5)*

Implemented with the public hinge-angle sensor (`Sensor.TYPE_HINGE_ANGLE`, wake-up): closed = angle < 10°. `DeviceStateManager` is still a system API in SDK 36 and Jetpack WindowManager's `FoldingFeature` requires a UI context, so neither works from the listener service. Jetpack WindowManager is still used in the debug UI.

## 5.4 Known risks to validate first (Phase 2) — **validated 2026-08-29, see `PHASE2_RESULTS.md`**

Outcome on Flip5 / One UI 8.0: risks 1, 2 and 4 are **cleared** (works on stock firmware, survives timeout via keep-screen-on, launch from app context allowed). Risk 3 (Samsung AOD pre-empting after a manual wake) still needs a hands-on test. Original list kept for reference:

1. Whether One UI permits a `showWhenLocked` third-party activity on the cover display without Good Lock/MultiStar. If not, document the MultiStar "I ♥ Galaxy Foldable → allow app on cover screen" step as a setup requirement.
2. Whether the system dismisses the activity when the cover screen times out, and whether `setTurnScreenOn` re-wakes the cover panel.
3. Whether Samsung's own cover AOD pre-empts the activity after screen-off.
4. Background-activity-launch restrictions (Android 10+): the launch is triggered from a `NotificationListenerService`, which may need a foreground service or `SYSTEM_ALERT_WINDOW` grant to be allowed to start activities. Test both paths.

## 5.5 Fallback mechanism

If a `showWhenLocked` activity is blocked, the alternative is a `TYPE_APPLICATION_OVERLAY` window added via a `WindowManager` obtained from `createDisplayContext(coverDisplay)`, requiring the `SYSTEM_ALERT_WINDOW` permission. Overlays generally cannot draw above the keyguard, so this fallback may only work with the cover screen unlocked; treat it as best-effort.

---

# 6. Proposed Architecture

```text
                  Android Notification System
                              |
                              v
                 NotificationListenerService
                              |
                              v
                   Notification State Manager
                              |
                +-------------+-------------+
                |                           |
                v                           v
          Application State            Persistence
                |                           |
                +-------------+-------------+
                              |
                              v
              Fold-state monitor  +  Cover Indicator Activity
                              |
                              v
                 Samsung Cover Display (display id 1)
                              |
                              v
                     User Interaction
```

## 6.1 Components

### NotificationListenerService

Responsible for receiving notification events from Android.

Responsibilities:

- Detect posted notifications
- Detect removed notifications
- Identify source application
- Extract relevant metadata
- Update notification state

### Notification State Manager

Responsible for converting raw notification events into application-level state.

Example:

```text
WhatsApp → 3 notifications
Gmail    → 2 notifications
Calendar → 1 notification
```

Responsibilities:

- Add notification
- Remove notification
- Track counts
- Determine whether an application has something waiting
- Determine indicator color
- Notify/update the cover indicator activity

### Persistence layer

A persistent data store should be used so that state can survive appropriate application/process lifecycle events.

The exact storage mechanism can be selected during implementation.

Potential options:

- DataStore
- Room
- SharedPreferences for a very small prototype

The prototype can start with the simplest reliable mechanism and evolve later.

### Fold-state monitor *(revised)*

Responsible for knowing whether the phone is closed.

Responsibilities:

- Observe `FoldingFeature` / device state
- Expose `isClosed` to the state manager
- Trigger show/hide of the indicator on transitions

### Cover Indicator Activity *(revised)*

Responsible for rendering the current notification state on the cover display.

Responsibilities:

- Launch on the cover display with `showWhenLocked` when closed + pending
- Draw the dot(s) on a black background
- Refresh in place when state changes (single instance, receive updates via broadcast/flow)
- Finish when nothing is pending or the phone is opened
- Fit the target cover-screen dimensions

---

# 7. Interaction Model

## 7.1 Version 1

The first prototype should implement only:

```text
Notification arrives
        ↓
Colored dot appears
        ↓
Notification remains pending
        ↓
Notification is removed/cleared
        ↓
Dot disappears
```

## 7.2 Future interaction

A later version could allow the user to tap the indicator.

Example:

```text
          ●
          ↓
Notifications

🔵 WhatsApp     3
🟢 Gmail        2
🟣 Calendar     1
```

The user could then select an application or notification.

## 7.3 Launching the full application

The indicator activity already runs on the cover display, so a tap can start another activity on the same display with `launchDisplayId`, or request unlock via `KeyguardManager.requestDismissKeyguard`.

For example:

```text
Cover indicator
     |
     +--> Tap WhatsApp
              |
              v
       Launch application
```

Launching third-party apps on the cover display may require the MultiStar allowlist; validate on the target device.

---

# 8. Notification State Semantics

A key design decision is defining what “unread” means.

Android notifications do not necessarily provide a universal concept equivalent to an application-specific unread message count.

Therefore, the first implementation should define:

> **Pending indicator = notification currently observed by the notification listener and not subsequently removed/cleared.**

This is a practical approximation of the old notification LED behavior.

Later versions can potentially incorporate application-specific behavior.

## 8.1 Example

WhatsApp notification arrives:

```text
Notification posted
        ↓
WhatsApp state = pending
        ↓
Blue indicator displayed
```

Notification is dismissed:

```text
Notification removed
        ↓
WhatsApp state updated
        ↓
Blue indicator removed
```

---

# 9. Indicator Designs

## 9.1 Single indicator

Simplest implementation:

```text
          ●
```

Advantages:

- Extremely simple
- Minimal display usage
- Closest to a physical notification LED

Disadvantage:

- Does not identify which application is waiting when multiple applications have notifications.

## 9.2 Color-coded indicator

Example:

```text
          ●
```

with the dot's color representing the application.

Advantages:

- Very similar to the original Samsung LED concept
- Extremely glanceable
- Minimal UI

Disadvantage:

- Only one application can be clearly represented at a time unless additional logic is added.

## 9.3 Multiple indicators

Example:

```text
        ● ● ●
```

Each dot represents an application.

Advantages:

- Multiple notification sources visible simultaneously
- Still very compact

Disadvantages:

- More complicated state management
- Limited by the physical display dimensions
- Colors may become ambiguous for some users

## 9.4 Expanded notification summary

Example:

```text
Notifications

🔵 WhatsApp     3
🟢 Gmail        2
🟣 Calendar     1
```

This should be considered a later version rather than the initial prototype.

---

# 10. Accessibility

The application should not rely solely on color to communicate notification state.

Potential future improvements:

- Different shapes
- Different dot counts
- Application icons
- Short text labels
- Optional haptic/audio integration where appropriate
- User-configurable colors

Color choices should consider color-vision deficiencies.

However, the initial prototype can remain color-based because reproducing the original LED experience is the primary goal.

---

# 11. Power Considerations *(revised)*

Unlike a widget, the indicator keeps the cover OLED panel lit while a notification is pending. This is the main cost of the approach and must be managed:

- Pure black background; only the dot pixels emit light
- Lowest practical brightness for the indicator window (`WindowManager.LayoutParams.screenBrightness`)
- Optional blink/duty cycle (e.g. 1 s on / 4 s off) to approximate the original LED and cut power — configurable
- Optional auto-hide after N minutes, re-shown on the next notification
- Avoid polling: react to listener and fold-state events only
- No wake locks beyond what `setTurnScreenOn` implies; let the activity be dismissed and re-launched rather than holding the screen indefinitely if the duty cycle is off
- Measure battery drain over a night in Phase 2 before continuing

---

# 12. Privacy and Permissions

Because the application needs to observe notifications from other applications, Android will require the appropriate notification-listener access.

The user should explicitly grant this access.

The application should:

- Request only necessary access
- Avoid storing notification contents unless required
- Prefer storing minimal metadata
- Clearly explain why notification access is needed
- Never transmit notification contents externally unless explicitly required and authorized

For the notification LED use case, the application generally only needs:

- Source application
- Notification identifier
- Notification presence/state
- Optional timestamp
- Optional count

It does not necessarily need to store message contents.

---

# 13. Security Considerations

Notification data can contain sensitive information.

The implementation should therefore avoid:

- Logging notification text unnecessarily
- Persisting message contents
- Sending notification data over the network
- Exposing notification data through debug interfaces

Debug builds may log package names and state transitions, but production builds should minimize diagnostic data.

---

# 14. Development Phases

## Phase 1 — Basic Android project

Create:

- Android Studio project
- Kotlin application
- Basic application activity
- Basic indicator activity

Goal:

> Confirm the development environment works on the physical Z Flip.

## Phase 2 — Cover display indicator spike *(revised, gating)*

Build a throwaway activity that draws a static dot, and launch it on the cover display with `showWhenLocked` from a debug button, then close the phone.

Goal:

> Confirm a third-party activity can be shown on the closed, locked cover screen on the target model/One UI, with and without MultiStar; record screen-timeout behavior and overnight battery cost.

**This phase gates the project.** If no configuration works, stop and reassess (see §5.5).

## Phases 3–7 — **done 2026-08-29**, see `PHASE2_RESULTS.md` (second half)

## Phase 3 — Notification listener

Implement:

`NotificationListenerService`

Goal:

> Detect when a notification is posted and identify the originating application.

## Phase 4 — Notification state

Create the state manager.

Goal:

```text
WhatsApp → pending
Gmail    → pending
```

## Phase 5 — Connect notification state to the cover indicator

When notification state changes:

```text
Notification
     ↓
State manager
     ↓
Fold state = closed?
     ↓
Launch / update / finish indicator activity
     ↓
Cover display
```

Goal:

> A notification causes the external-screen indicator to appear.

## Phase 6 — Color mapping

Implement configurable application colors.

Example:

```text
WhatsApp → Blue
Gmail    → Green
Calendar → Purple
```

Goal:

> The indicator color identifies the source application.

## Phase 7 — Clearing behavior

Implement handling for notification removal/dismissal.

Goal:

> The indicator disappears when no relevant notification remains.

## Phase 8 — Multiple applications

Support multiple simultaneous pending applications.

Possible UI:

```text
        ● ● ●
```

Goal:

> The user can see that several applications have something waiting.

## Phase 9 — Interaction

Add tapping behavior.

Goal:

> Tapping the indicator opens a useful notification summary or application.

## Phase 10 — Polish

Improve:

- Visual design
- Animations, if appropriate
- Accessibility
- Configuration
- Battery behavior
- Error handling
- Different Z Flip screen sizes/orientations

---

# 15. Initial Prototype Scope

The first working prototype should deliberately be small.

### Required

- Kotlin
- Android Studio
- NotificationListenerService
- Cover-display indicator activity (`launchDisplayId` + `showWhenLocked`)
- Fold-state detection
- Works while the phone is closed and locked
- One indicator
- At least two configurable notification colors
- Notification posted/removed handling

### Not required initially

- Full notification browser
- Message previews
- Complex animations
- Cloud synchronization
- User accounts
- Cross-device synchronization
- Network communication
- Complex database
- Support for every Z Flip generation

---

# 16. Proposed First Demonstration

The first successful demonstration should work as follows:

### Starting state

Phone is:

```text
Closed
+
Locked
+
No pending notifications
```

External screen:

```text
(no indicator)
```

### Trigger

Send a test notification from a configured application.

### Expected result

External screen:

```text
          ●
```

with the configured application color.

### Second trigger

Send a notification from another configured application.

Expected result could initially be:

```text
          ●
```

using a defined priority rule.

A later version can change this to:

```text
        ● ●
```

### Clear

Dismiss/remove the notification.

Expected result:

```text
(no indicator)
```

---

# 17. Potential Future Features

Once the basic system works, the project could evolve into a full notification dashboard for the Flex Window.

Possible features:

- Per-application colors
- Multiple notification dots
- Application icons
- Notification counts
- Notification priority
- Custom indicator patterns
- User-defined color rules
- Schedule-based behavior
- Do Not Disturb integration
- Different behavior while charging
- Different behavior during sleep hours
- Tap-to-open application
- Tap-and-hold for notification details
- Optional clock
- Optional battery status
- Optional weather/status information

A particularly attractive final design would preserve the simplicity of the original LED while taking advantage of the Z Flip's display.

---

# 18. Reference Architecture

```text
┌───────────────────────────────────────────┐
│            Android Notification System    │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│        NotificationListenerService        │
│                                           │
│  • notification posted                    │
│  • notification removed                   │
│  • identify application                   │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│          Notification State Manager       │
│                                           │
│  WhatsApp → pending                       │
│  Gmail    → pending                       │
│  Calendar → none                           │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│   Fold monitor → Cover Indicator Activity │
│                                           │
│             ●                             │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│       Samsung Z Flip External Display     │
│                                           │
│                 LOCKED                    │
└───────────────────────────────────────────┘
```

---

# 19. Reference Material *(revised)*

Android:

- Multi-display activities / `ActivityOptions.setLaunchDisplayId`: https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)
- `Activity.setShowWhenLocked` / `setTurnScreenOn`
- Jetpack WindowManager `FoldingFeature`: https://developer.android.com/jetpack/androidx/releases/window
- Background activity start restrictions: https://developer.android.com/guide/components/activities/background-starts
- `NotificationListenerService`: https://developer.android.com/reference/android/service/notification/NotificationListenerService

Samsung (secondary; for display ids, dimensions and MultiStar behavior):

- Flex Window documentation: https://developer.samsung.com/galaxy-z/flex_window.html
- Flex Window widget Code Lab (superseded for this project, kept for `launchDisplayId` notes): https://developer.samsung.com/codelab/galaxy-z/widget-flex-window.html
- Good Lock / MultiStar cover-screen app allowlist (community write-up): https://www.xda-developers.com/samsung-good-lock-run-any-app-galaxy-z-flip-5-cover-screen/

Prior art examined: CoverScreen OS (proves the approach works on stock firmware), aodNotify (main-display only).

---

# 20. Key Technical Questions to Resolve Before Implementation *(revised)*

1. Exact Galaxy Z Flip model/generation and One UI version.
2. ~~Cover display id and dimensions~~ **Logical id 1, 748×720, density 340 (Phase 2).** Note `getDisplays()` may omit it while OFF; query `getDisplay(1)` / presentation category too.
3. ~~Can a `showWhenLocked` third-party activity be shown on the cover display while closed — without MultiStar?~~ **Yes, without MultiStar (Phase 2).**
4. ~~Does the activity survive cover-screen timeout?~~ **Yes with `FLAG_KEEP_SCREEN_ON` (Phase 2).** `setTurnScreenOn` also wakes the main display briefly — re-check in Phase 5.
5. ~~Is an activity start from the `NotificationListenerService` allowed?~~ **Blocked by BAL unless the app holds `SYSTEM_ALERT_WINDOW`; with it, allowed (`BAL_ALLOW_VISIBLE_WINDOW`). (Phase 5)**
6. Overnight battery cost of a black activity with one lit dot vs. stock AOD; is a duty-cycle needed? *(pending)*
7. How notification dismissal should map to "read".
8. Single dot vs. multiple simultaneous indicators.
9. Tap behavior: summary, originating app, or nothing.
10. Continuous indicator vs. auto-hide after a timeout.

---

# 21. Recommended Implementation Strategy

The project should **not** start as a full notification-management application.

The recommended strategy is:

```text
1. Identify exact Z Flip model
        ↓
2. Create a basic Android/Kotlin project
        ↓
3. Spike: static dot activity launched on the cover display
        ↓
4. Confirm it shows while closed + locked (with/without MultiStar); measure battery
        ↓
5. Add NotificationListenerService
        ↓
6. Detect one test notification
        ↓
7. Make one dot appear
        ↓
8. Add application/color mapping
        ↓
9. Handle notification removal
        ↓
10. Add multiple notifications
        ↓
11. Add interaction
        ↓
12. Polish
```

This approach front-loads the single biggest unknown (step 4) so Samsung/One UI restrictions are discovered before any notification logic is written.

---

# 22. Product Vision

The final product should feel less like an app and more like a **software replacement for the classic Samsung notification LED**.

The ideal experience is:

> **Look at the closed phone → immediately know whether something is waiting → optionally know what kind of notification it is → open the phone only when necessary.**

The design principle should be:

**Maximum information at a glance, minimum interaction required.**

The first prototype should therefore prioritize a tiny, persistent, color-coded indicator over a conventional widget interface.
