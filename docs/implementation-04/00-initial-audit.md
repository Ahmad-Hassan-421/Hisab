# Hisab implementation 04 baseline

The starting revision is `195d911c40655a9c4b5981db8bc75e0036421677` on `hisab-update`. The working tree was clean at the start. The baseline did not modify application code, database records, or the installed phone package.

| Area | Observed baseline and implementation plan |
| --- | --- |
| Android architecture | Kotlin, Compose, Material 3, Hilt, Room, Retrofit and OkHttp. API 24 minimum and API 34 target. Navigation and session restoration are in `MainActivity`, `AuthViewModel` and `SessionViewModel`. Room holds profile and financial cache data. |
| Identity and update | `com.example.hisab`, version 1.1.0, build 2. The C51 reports that package once, a single launcher activity, and the same November 13 first-install timestamp recorded before upgrade. Its installed APK byte hash matches the public `v1.1.0` asset (`ef034d9895df980f3372658d1e61c59750a7f2213ca64e008c88a3f3ad110132`). `apksigner` confirmed the prior signing certificate (`d2dc29853f3cc5ce5ed1456e613da764baf20a408d5148c913ea48d784bce285`). Earlier QA used a separately installed `.qa` identity. That variant shared the launcher label and could create two visually identical entries while installed. The current phone cannot establish which entries were visible when the duplicate was reported. Phase 1 will distinguish QA branding and verify artifact and update identity. |
| Authentication | Email and password login and registration, BCrypt hashes, JWT bearer tokens and encrypted local session storage. No reset endpoints or recovery screen exist. Signup masks password confirmation without a separate toggle. Phases 3 and 4 will cover recovery and visibility. |
| Profile | The cropper produces a capped JPEG in a temporary path. Android uploads the file and stores the server URL in the profile. The server stores a portable path on a configured upload volume. Coil cache can evict images and the app has no managed per-user offline avatar file. Phase 2 will verify local and remote persistence. |
| MongoDB and roles | Ktor uses KMongo and requires a replica set or sharded cluster. Data models retain user IDs, email, password hash, Hisab ID and role; group member and former-member IDs; and ledger and invitation relationships. The Atlas connector listed one accessible project. Phase 5 will inspect the relevant collection metadata, use server-side authorization, and exercise changes in an isolated database. The existing Android admin view reads cached ledger entries and is not a user-management backend. |
| Ledger and Home | Groups, expenses, shared splits, debts, repayments, and settled history use API repositories. Existing calculations retain PKR thousandths and tests cover rounding, input precision, batch atomicity, and concurrent repayments. Phase 8 focuses on labels and workflow clarity while retaining these rules. |
| Invitations | The API persists requests and statuses with sender, recipient, group and avatar fields. Android shows in-app notifications and receives WebSocket events. There is no stored library of invitation messages. Phase 9 adds it with compatibility for existing requests. |
| Help, legal and UI | Navigation has no About, FAQ, privacy policy, or agreement version flow. The splash uses AndroidX SplashScreen with themed assets. Login and signup share a Compose form. Phases 6, 7, 10 and 11 address these areas. |
| Backend and publication | Kotlin/Ktor on Netty with MongoDB, static uploads, JWT, and structured errors. Render declares a manual Docker deployment and persistent storage, with `/healthz` as a database-aware probe. Phase 12 adds the monitor endpoint and tests degraded behavior. Android, backend and tagged release workflows are present. Production signing for 1.1.0 was done locally, and automated signing requires a configured GitHub environment. |

Android Gradle unit, lint, debug and release compilation passed. The current reports record 34 Android JVM tests with zero failures, lint with zero errors and 100 existing warnings, and 3 backend JVM tests with zero failures. The extended baseline against disposable MongoDB passed 93 of 93 API checks. The APK compatibility evidence was read-only. A fresh live metadata read timed out, but the public GitHub asset metadata and installed APK hashes matched.

## Progress tracker

| Phase | State |
| --- | --- |
| 0. Initial inspection | Completed |
| 1. Duplicate-install prevention | Completed for package identity and upgrade. Live candidate update and account-data check deferred to release validation. |
| 2. Profile image persistence | Completed for the restart, account isolation and compatible update gate on the C51. Interactive checks are listed for Phase 13. |
| 3. Forgot password | Completed through the Android and disposable backend flow. Live mailbox delivery remains a release check. |
| 4. Confirm password visibility | Completed and verified by instrumentation on the C51. |
| 5. MongoDB administration | Completed with read-only Atlas inspection and a disposable C51 admin flow. |
| 6. About and FAQ | Completed and verified on the C51. The Privacy Policy link is part of Phase 7. |
| 7. Privacy policy and agreement | Completed |
| 8. Hisab actions | Completed |
| 9. Invitation messages | Completed |
| 10. Splash | Completed |
| 11. Login and signup design | Completed with warnings |
| 12. Render health check | Completed |
| 13. Final regression | Completed for available automated checks |
| 14. GitHub and release readiness | Ready with warnings |
| 15. Handover | Completed |
