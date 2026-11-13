# Android interface improvements

## Confirmations

The shared alert supports informational, warning, success, error, confirmation, and destructive styles. Material theme colors provide the appropriate contrast in each theme. Scrollable messages fit smaller screens. Pending actions disable repeat taps, back dismissal, and outside dismissal.

Hisab uses the component for settled-history deletion, leaving a group, logging out, an empty repayment state, and update download confirmation. The package installer still provides Android's own confirmation. Update download completion exposes an explicit install action.

The Android debug build and lint succeeded. All 24 JVM tests passed, including a settled-history test for dismissal, duplicate taps, and a failed deletion. Dialog layout and accessibility checks require a running Android device.

## Non-blocking feedback

A shared snackbar host keeps confirmed results visible across navigation. Notices use the Material theme and announce their message to accessibility services. The host coalesces duplicate messages and replaces the current notice with new feedback. Warnings and errors remain visible longer than success and information messages.

The app announces confirmed Hisab creation, ledger writes, settlement clearing, invitation sending, and profile saves. Login and ledger forms keep validation messages in the form. Page loading continues to use the shared error states.

The debug build and lint passed with 25 JVM tests. The new test verifies duplicate suppression, replacement, and duration selection. Visual and screen-reader timing remain unverified on a device.
