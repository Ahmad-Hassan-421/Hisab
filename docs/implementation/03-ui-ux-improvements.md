# Android interface improvements

## Confirmations

The shared alert supports informational, warning, success, error, confirmation, and destructive styles. Material theme colors provide the appropriate contrast in each theme. Scrollable messages fit smaller screens. Pending actions disable repeat taps, back dismissal, and outside dismissal.

Hisab uses the component for settled-history deletion, leaving a group, logging out, an empty repayment state, and update download confirmation. The package installer still provides Android's own confirmation. Update download completion exposes an explicit install action.

The Android debug build and lint succeeded. All 24 JVM tests passed, including a settled-history test for dismissal, duplicate taps, and a failed deletion. Dialog layout and accessibility checks require a running Android device.
