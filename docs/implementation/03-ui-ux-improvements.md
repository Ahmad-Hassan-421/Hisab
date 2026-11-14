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

## Hisab details

The detail screen places the full Hisab name, action buttons, and member balances in one scrolling view. Action labels wrap on smaller screens. Each member card labels debts and receivables separately and identifies former members. Transaction history starts with the current pair balance, shows repayments and debts in date order, and displays localized digit grouping with three decimal places. A settled pair now says “No outstanding balance.” Presentation does not round or modify ledger values in storage.

A shared member picker opens inline in expense, debt, and repayment forms. The phone test reproduced a failure opening the former debt dropdown; the inline control gives each member a labeled selection target in the form. Forms can scroll when the screen or keyboard leaves less space.

The disposable API regression passed all 90 checks before and after the presentation changes. It covers creating and reading groups and entries, invitations, exact repayment, duplicate submission, and deleting settled history. The existing ledger routes do not implement editing a posted entry.

The final Android debug build and lint passed, as did all 29 JVM tests. The C51 QA app created and opened an empty Hisab in an earlier pass. With the final member picker, it recorded PKR 1,234.567, showed the matching transaction note and balance, recorded the full repayment, and showed a zero balance with both entries retained after declining deletion. The API regression separately verified deletion of settled history.

## Profile pictures

Profile, member cards, and invitations share a circular avatar. It uses a centered icon with theme colors for the legacy default, missing URLs, and failed loads. The legacy default no longer needs a network request. Custom pictures keep their aspect ratio; the frame crops the edges. When uploading a picture, the existing crop screen lets the user position a square crop, then writes JPEG with a maximum dimension of 512 pixels. Upload no longer writes an unused persistent file. Invite notifications reuse the app image loader and decode at 128 pixels.

Two unit tests cover default, missing, portable upload, and invalid image sources. The C51 QA check compared rendered Profile pixels after loading a default avatar, a non-square test upload, a missing image, and a failed image. The custom image appeared, and both missing and failed images matched the default. The fixture was uploaded through the API; the native picker crop was not exercised in that check.

## Update experience

Settings shows the installed build, available version and release notes, and the download size when present. “Later” dismisses the confirmation without downloading. A progress bar uses measured bytes when the total is available and switches to an indeterminate indicator when it is unknown. Failed or interrupted downloads describe the recovery action. Users can cancel an active transfer or retry one that failed.

The app keeps only the exact download in its app-specific update directory. It checks the file is readable and validates the package ID, signing certificates, and increasing Android version code before showing Install. Android handles source permission and final consent. The app reports the installer handoff without claiming installation succeeded. Release provenance and SHA-256 verification belong to Phase 12.

All 34 Android unit tests, debug build and lint, and all 90 API regression checks passed. An isolated Android emulator showed the check in progress, current and newer versions, a failed check and retry, release notes and size, Later, measured download progress, cancellation, and recovery from unavailable and unreadable packages. A verified, signed QA candidate advanced to Ready, and Android opened its settings for this app’s install-source permission. The permission and installed APK were left unchanged. Waiting for a connection, an interrupted transfer, and insufficient storage were checked by unit tests for the corresponding Android result codes; the device was not deliberately filled. An actual upgrade and data preservation remain release gates.
