# Hisab implementation 03 handover

Hisab 1.1.0 is available as Android build 2. The signed APK keeps application ID `com.example.hisab` and matches the signing certificate from the owner’s sideloaded 1.0 APK. The release uses source commit `2f99a49288419ca5ceee08026856659d8c85cdf3` on `hisab-update`. The public APK and both Render update endpoints agree on its version, download URL, size, and checksum. [GitHub release](https://github.com/Ahmad-Hassan-421/Hisab/releases/tag/v1.1.0) · [Live update metadata](https://hisab-onlo.onrender.com/api/releases/latest)

| Phase | Change and verification |
| --- | --- |
| 0. Baseline | Audited the Android app, Ktor, MongoDB, GitHub and release configuration. The starting build passed 16 JVM and 89 API checks. |
| 1. Error states | Added typed Android errors, session recovery, retry and safe ledger writes. A Room migration preserves cached data. |
| 2. Loading | Added reusable skeletons and action progress to app flows. Verified the loading state and submission behavior. |
| 3. Exceptions | Centralized Android diagnostics and the Ktor JSON error envelope. API regression verified error status and request ID correlation. |
| 4. Alerts | Added shared confirmations, including the settled-history deletion safeguard. |
| 5. Feedback | Added shared snackbar feedback for confirmed results and errors. |
| 6. Refresh | Added Home and detail refresh, retained usable contents on failure, and prevented overlapping refreshes and account data bleed. |
| 7. Home | Improved group navigation and the Create Hisab action. The C51 covered empty, populated, failed and recovered states and compact dark-mode layout with enlarged text. |
| 8. Detail | Added clear balances, history and a member picker. The C51 verified exact thousandths and recorded a full repayment with history retained. |
| 9. Avatars | Unified circular avatars, local defaults and failed-image fallback. Device check covered custom, missing and broken images. The native crop picker was outside that check. |
| 10. Update UI | Added progress, cancel and retry, ZIP/APK validation and Android installer handoff. The emulator covered missing and corrupt assets and permission handoff. |
| 11. CI/CD | Added Android and backend checks and an environment-gated release workflow. Patched Ktor and Guava after the first audit. |
| 12. Delivery | Replaced inferred build numbers with signed APK metadata and SHA-256 validation. C51 QA rejected a wrong checksum. An emulator accepted a matching in-place 1.0 to 1.1.0 upgrade. |
| 13. Regression | Fixed unsupported ledger transaction types and the repeated settled-history deletion prompt. Passed 34 Android JVM, 3 backend JVM, 93 API, 1 Room instrumentation and 6 final C51 ledger walkthrough checks. |
| 14. GitHub verification | Both required CI workflows passed for the release source commit. GitHub retained the QA APK and test reports. |
| 15. Release | Published the versioned non-debug APK with `SHA256SUMS` and `hisab-update.json`. Independently verified the public download and live metadata. |
| 16. Handover | Recorded source, release, test evidence and the remaining phone confirmation. |

The calculation review covered the payer’s share, remainder allocation at PKR 0.001 precision, balances between individual members, legacy rounding, exact settlement, concurrent repayments, and rejection of invalid batches. The C51 displayed and repaid PKR 1,234.567 exactly. After a cold restart, it reopened the settled ledger with the session and history intact. Deliberate deletion required confirmation and the API confirmed the QA history was cleared. [Regression report](03-testing-report.md)

Android CI passed the API 34 Room migration, JVM tests, lint, and APK build. Backend CI passed database-backed regression, its production container build and OSV scanning. The local lint run reported zero errors and 100 warnings. The local Gradle instrumentation harness had an emulator process failure. Running the rebuilt APK and test directly through AndroidJUnitRunner passed the migration. [Android CI](https://github.com/Ahmad-Hassan-421/Hisab/actions/runs/43594994272) · [Backend CI](https://github.com/Ahmad-Hassan-421/Hisab/actions/runs/43594994323)

The first release used the matching key on the local machine. The tag also triggered the original release workflow. It passed the required checks and built the APK, then stopped at signing with `Missing signing keystore`. Future automated signing needs the `android-release` environment and its signing settings. The release workflow now checks for an already published stable release before attempting another publication for the same tag. The initial asset upload timed out, and a longer retry completed with verified hashes. The C51’s owner still needs to complete the live in-app upgrade and confirm the old Hisabs are available afterward. [Automated release run](https://github.com/Ahmad-Hassan-421/Hisab/actions/runs/43621569679) · [CI and release procedure](03-ci-cd.md) · [Update delivery and compatibility](03-update-delivery.md)
