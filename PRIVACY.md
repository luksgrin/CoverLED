# CoverLED — Privacy Policy

_Last updated: 2026-08-30_

CoverLED shows a colored indicator on the cover screen of a Samsung Galaxy Z Flip when notifications are waiting. This policy explains what the app can see and what it does with it.

## What the app accesses and why

**Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`).** Required to know that a notification exists and which app posted it. CoverLED reads only:
- the package name of the app that posted the notification,
- the system's notification identifier (key),
- the notification's declared LED / accent color, if any.

CoverLED **does not read, display, store or transmit notification titles, text, images, sender names or any other content.**

**Display over other apps (`SYSTEM_ALERT_WINDOW`).** Android requires this permission for an app in the background to show the indicator on the cover screen. CoverLED draws nothing over other apps; it only shows its own full-screen indicator on the cover display while the phone is closed.

**Hinge angle sensor.** Used to know whether the phone is open or closed.

## What is stored on the device

Only in the app's private storage, never leaving the phone:
- package names of apps that have posted notifications (to list them in the color editor),
- your settings: colors per app, priority / ignore choices, blink and position preferences,
- the custom shape image you may import.

Nothing is stored about notification content. All data can be removed by clearing the app's storage or uninstalling.

## What is sent over the network

Nothing. CoverLED has no network permission and contains no analytics, advertising or crash-reporting SDKs.

## Third parties

None. No data is shared with anyone.

## Children

CoverLED does not collect personal data from anyone, including children.

## Changes

Updates to this policy are published in the app's source repository alongside the code.

## Contact

Open an issue in the project repository.
