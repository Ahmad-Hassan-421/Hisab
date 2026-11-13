# Hisab implementation 03 initial audit

## Baseline and repository

The starting revision is `426a1c44d382908a4028e82aa4de8aaabfbfbc67`. GitHub confirmed that `Ahmad-Hassan-421/Hisab` uses `hisab-update` as its default branch and that the branch points to the same revision. The workspace also contains uncommitted ledger, authentication, profile, avatar, and update-check changes. The local baseline includes those changes. The audit commit contains only this document.

## Architecture and findings

| Area | Existing behavior | Findings for the implementation phases |
| --- | --- | --- |
| Android and navigation | A single Kotlin Android module uses Compose Material 3, Hilt, feature ViewModels, StateFlow, and Navigation Compose. Light, dark, and system themes are supported. | Screen states and recovery are implemented independently. Different screens use text, snackbars, alerts, and spinners for similar outcomes. |
| Home | The dashboard lists the authenticated user's groups and polls every 15 seconds. It links to group creation, profiles, settings, and invitations. | A failed refresh can hide usable results. Explicit refresh and polling can overlap. The empty screen has little guidance beyond the floating action button. |
| Hisab details | Group metadata and users come from the API. Room publishes cached ledger entries. Current calculations support direct debt, shared expenses, repayments, and settlement. | Initial load and background failures have no dedicated recovery state. Some forms close before a save finishes. Returning to the screen is the available way to reload server changes. |
| Authentication and session | Retrofit adds bearer tokens. A matching 401 clears the stored session and the navigation host opens sign-in. Room can supply the initial cached user. | Expired sessions need a clear explanation. Auth error handling inspects exception text. Background account restoration suppresses failures. |
| Invitations and notifications | Invitations use API polling and per-item action state. The backend also emits WebSocket invitation events. | A failed list read can appear as an empty list. Some action errors expose exception strings or are never shown. |
| Profile and images | The profile uses Room-backed user records, server uploads, Coil, and a local fallback avatar. | Duplicate profile observers can overwrite edits or loading state. The default avatar can resolve to a remote image. Image fallback and crop behavior need device verification. |
| Cache and synchronization | The ledger repository serializes cache changes and replaces a group's snapshot in a Room transaction. The server uses MongoDB transactions for ledger mutations. | Groups and invitations depend on the network. There is no offline write queue. The UI does not distinguish cached content from a successful refresh. |
| Backend | Ktor exposes feature routes and uses MongoDB, JWT authentication, and StatusPages. The Render Docker Blueprint defines readiness, a persistent upload disk, and a manual deploy trigger. | Error response formats vary by route. Release lookup can expose upstream exception details. Request correlation is missing. No production service or database was changed during this audit. |
| Updates and signing | Settings calls `/api/releases/latest`. Ktor looks up the latest GitHub release and selects its first APK. Android uses DownloadManager and the platform installer. The Gradle project uses application ID `com.example.hisab`, version code 2, and version name 1.1.0. | There is no verified release artifact selection, checksum, package or signer validation, or configured release signing in the project. The backend derives a pseudo build number from tag digits. Existing installation compatibility must be demonstrated with the original signing identity. |
| CI and release process | The repository has Android and backend CI jobs, including a MongoDB-backed API regression. Render deployment configuration is tracked separately. | There is no signed release workflow in the checkout. Remote CI and the final signed upgrade must be verified on the intended release revision. |

## Executed baseline

| Check | Observed result |
| --- | --- |
| Android `testDebugUnitTest lintDebug assembleDebug assembleRelease` on JDK 17, using a placeholder HTTPS origin for release compilation | Gradle succeeded. 16 JVM tests passed. Lint reported 0 errors and 97 warnings. Release output was unsigned. |
| Backend `test installDist` on JDK 17 | Gradle succeeded. The unit-test task reported `NO-SOURCE`. |
| `python scripts/qa/check_api.py --extended` against a disposable loopback Ktor server and MongoDB replica set | 89 checks passed. Coverage included auth, invitations, ledger precision and concurrency, settlement, and profile uploads. |
| `git diff --check` and `python scripts/check_secrets.py` | Passed. The scanner reported 0 potential exposures. |
| Existing Android device test coverage | The repository has an ADB smoke script. The preceding isolated emulator attempt ended during Android system startup. This baseline does not establish a device or production upgrade pass. |

## Phase checklist

| Phase | Status |
| --- | --- |
| 0. Initial inspection and baseline | COMPLETED |
| 1. Global error states | COMPLETED |
| 2. Loading states | COMPLETED |
| 3. Exception handling | COMPLETED |
| 4. Alerts and confirmations | COMPLETED |
| 5. Non-blocking feedback | PENDING |
| 6. Refresh and synchronization | PENDING |
| 7. Home refinement | PENDING |
| 8. Hisab detail refinement | PENDING |
| 9. Profile avatars | PENDING |
| 10. Update UI | PENDING |
| 11. CI/CD | PENDING |
| 12. Update delivery and versioning | PENDING |
| 13. Final regression | PENDING |
| 14. Remote CI verification | PENDING |
| 15. Verified release | PENDING |
| 16. Handover | PENDING |
