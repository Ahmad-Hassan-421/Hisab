# Android interface improvements

## Confirmations

The shared alert supports informational, warning, success, error, confirmation, and destructive styles. Material theme colors provide the appropriate contrast in each theme. Scrollable messages fit smaller screens. Pending actions disable repeat taps, back dismissal, and outside dismissal.

Hisab uses the component for settled-history deletion, leaving a group, logging out, an empty repayment state, and update download confirmation. The package installer still provides Android's own confirmation. Update download completion exposes an explicit install action.

The Android debug build and lint succeeded. All 24 JVM tests passed, including a settled-history test for dismissal, duplicate taps, and a failed deletion. Dialog layout and accessibility checks require a running Android device.

## Non-blocking feedback

A shared snackbar host keeps confirmed results visible across navigation. Notices use the Material theme and announce their message to accessibility services. The host coalesces duplicate messages and replaces the current notice with new feedback. Warnings and errors remain visible longer than success and information messages.

The app announces confirmed Hisab creation, ledger writes, settlement clearing, invitation sending, and profile saves. Login and ledger forms keep validation messages in the form. Page loading continues to use the shared error states.

The debug build and lint passed with 25 JVM tests. The new test verifies duplicate suppression, replacement, and duration selection. Visual and screen-reader timing remain unverified on a device.

## Home

Home now gives the list a clear heading, shows the number of groups actually loaded, and uses cards that wrap longer names and identify member counts. The empty state explains what a Hisab is and opens the existing create form. Accounts with groups have a labeled create button in the list header. The compact landscape test found that the initial floating button was unreachable, so the button now scrolls with the list. The notifications control retains its destination and no longer shows a placeholder count.

The screen keeps the existing loading, recovery, and refresh states. It refreshes a retained dashboard when returning from another screen, so recent changes can appear without waiting for the next polling cycle. The app also keeps its initial route stable within the activity. Previously a theme change after login could recalculate the route and discard navigation history. The redesign does not change the group API or ledger calculations.

The updated QA APK built successfully, 27 JVM tests passed, and lint reported zero errors and 97 warnings. A controlled delayed-response test verified that an empty account appears only after the server returns an empty list. The device snapshots did not capture the transient skeleton. On the connected C51, the isolated QA app opened the create form from an empty account and returned to Home. A refresh displayed 10 fixture groups including a long title. When the fixture group endpoint failed, Home recovered from a failed initial fetch and retained the loaded list through a later failure and retry.

The C51 also kept the group count and create action reachable in compact landscape with dark mode and 145% text. On the final APK the QA run selected dark mode in Settings, returned to Home using the app's Back button, opened the populated account's create form, returned to Home, and restored the phone's original text size.
