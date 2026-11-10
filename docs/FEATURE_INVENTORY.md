# Hisab feature inventory

## Baseline and environment

Discovery on 2026-11-09 found two Gradle 8.5 projects under one workspace. The workspace and both projects lack Git metadata, so there is no branch, commit history, staging area, or remote to inspect. The existing APK, server build and profile upload were present before this work. Application and database identities were recorded before making source changes.

A local audit snapshot contains source and build inputs with embedded credentials redacted. SHA-256 fingerprints record 133 original input files. Build outputs, local settings and uploaded user data were excluded from the snapshot. With JDK 17, the original server ran `gradlew.bat --offline test installDist` successfully with `test NO-SOURCE`. The original Android offline build failed during dependency resolution because Hilt 2.50 and ASM 9.6 artifacts were missing from the effective cache. The online retry and device execution are tracked in the initial QA report.

| Area | Observed implementation |
|---|---|
| Android | Kotlin 1.9.22, AGP 8.2.1, Jetpack Compose Material 3, Navigation Compose, ViewModels and StateFlow, Hilt. Application ID `com.example.hisab`, min API 24, target/compile API 34. Debug APK and unminified release variant. |
| Android persistence | Room `hisab.db` v3 has user and entry tables plus migrations 1→2 and 2→3. EncryptedSharedPreferences holds the token and user ID. Theme uses DataStore and a preference mirror for the splash screen. The group list requires the API. There is no offline mutation queue. |
| Network | Retrofit/Gson and OkHttp for HTTP. JWT bearer interceptor, WebSocket client, and periodic group and invitation polling while corresponding ViewModels are active. No WorkManager jobs or Android system notification integration were found. |
| Backend | Kotlin 1.9.22, Ktor 2.3.9/Netty, kotlinx.serialization, MongoDB through KMongo 4.11.0. Database `hisab_db`, documents for users, groups, entries and invitations. UUID identifiers and epoch-millisecond timestamps. |
| Authentication | Email/password registration and login, BCrypt, signed JWT with user and role claims. The Android session selects login or dashboard and conditionally exposes an admin screen. The backend used source-embedded signing and administrator credentials. |
| Hosting | The Android app pointed to a LocalTunnel HTTPS URL. Netty binds to all interfaces on port 8081 by default. Some server image URLs point to an old LAN host and port. The server used a credential-bearing MongoDB fallback and writes profile uploads to `uploads/`. Firebase rule and project files are present but no runtime Firebase SDK or client calls were found. |
| Other integrations | Coil loads profile images, CanHub crops a selected image, Android FileProvider shares files internally, and the server can send welcome email using Brevo. No payment provider or currency conversion was found. |
| Tooling and prior tests | Gradle wrappers, Android lint, JUnit, coroutine test and Compose instrumentation dependencies. Test source directories contained no tests at baseline. No CI, hosting manifest, or setup documentation was present. |

## Feature map

“Source” means implementation was observed in source. See the [initial QA report](QA/INITIAL_DEVICE_TEST_REPORT.md) for execution evidence. Every feature started with zero checked-in automated tests.

| Feature | Screen or entry point | HTTP or socket contract | Data and integration | Baseline status and test priority |
|---|---|---|---|---|
| Splash, session restoration and sign-out | MainActivity, login, dashboard, profile confirmation | `GET /users/{id}` on session/profile lookup | Token and user ID preferences, theme mirror, in-memory role | Source. High. Expired auth recovery, back-stack clearing, relaunch, and offline restoration. |
| Registration and sign-in | Login, register mode, password visibility | `POST /auth/register`, `POST /auth/login` | User document, bcrypt, JWT, optional Brevo email | Source. Critical. Credential and hash exposure, malformed forms, duplicate registration, authorization. |
| Dashboard and group creation | Dashboard cards, create group form, refresh, empty/error states | `GET /groups?userId=`, `POST /groups` | Group document, current user, polling | Source. Critical. Caller-controlled group owner lookup and membership at baseline. |
| Group deletion and leaving | Group detail overflow and leave confirmation. Group deletion has a server route and a dashboard ViewModel method. | `DELETE /groups/{id}`, `POST /groups/{id}/leave` | Group/member history, entries, websocket on leave | Source. Critical. Deletion authorization and multi-document cleanup. No group-delete UI action is wired. |
| Ledger and history | Group detail, expandable member cards and per-person transaction dialog | `GET /entries?groupId=` | Entry and user documents, Room entry cache, PKR display | Source. Critical. Group authorization, stale entries after remote clear, decimal totals. |
| Add specific expense and split expense | Group detail amount dialog | `POST /entries`, `POST /entries/batch` | Debtor, creditor, amount, description, timestamp | Source. Critical. Input bounds, fractional splits, retries and repeated submits. |
| Repayment | Group detail repayment dialog, individual/equal/different payments | `POST /entries`, `POST /entries/batch` | Opposite-direction ledger entries, balance computation | Source. Critical. Overpayment validation, rounding and partial failure. |
| Clear settled history | Settlement confirmation with keep/delete actions | `DELETE /entries/group/{groupId}` | Server and Room group entries | Source. Critical. Cache and server consistency, authorization and clear failures. |
| Individual entry deletion | API only | `DELETE /entries/{id}` | Entry document | Source. Critical. No reachable Android action was found. |
| Invitations and in-app notifications | Group invite by 12-digit ID, recommendations, dashboard badge, notification list and accept/decline | `GET/POST /requests`, `PATCH /requests/{id}`, `GET /users/by-unique-id/{uid}`, `/ws` | Invitation, group and user documents, polling and socket events | Source. Critical. Invitation ownership, duplicate handling and concurrent group membership changes. |
| Profile and settings | Read-only name, email and user ID display, theme, Save Profile and profile picture | `GET/PUT /users/{id}`, `POST /upload/profile-pic`, `GET /static/uploads/{name}` | User document, local preferences, picker, cropper and server filesystem | Source. High. Upload literal-path bug, stale LAN image links and disk persistence. The ViewModel has a name setter, and the API accepts name changes. No name editor or ID copy action is wired on the screen. |
| Admin view | Role-gated Admin Panel | No global ledger API | Room entry DAO | Source. High. The panel reads the device’s local cache. It does not provide a server-wide audit. |
| Legacy UI and assets | Add Hisab composable and Firebase rules | None from current navigation | Bundled assets and historical Firebase configuration | Source only. The Add Hisab composable has no registered destination. |
| Lifecycle, connectivity and permissions | Android activity, encrypted storage, image picker, polling | Same API contracts as the related feature | INTERNET and ACCESS_NETWORK_STATE, foreground activity lifecycle | Source. High. Validate background/resume, rotation, interrupted requests and recovered connectivity. |
