# Initial device and API test report

## Environment

- Physical Android C51, Android 14/API 34, 720 × 1600 at density 320. ADB reported `device` and found Hisab 1.0 (`com.example.hisab`) installed, with Internet and network state permissions granted.
- The test APK uses a separate package, `com.example.hisab.qa`, and connects through ADB reverse to a loopback-only server. The baseline copy differs in test identity, connection address, isolated database configuration and disabled welcome email delivery. The installed personal app and its database were excluded from tests.
- JDK 17, Gradle 8.5, Android SDK API 34, Ktor Netty and a disposable MongoDB Community 8.0 single-node replica set. API fixture emails use the reserved `.invalid` domain. Uploads were isolated from the original upload directory.
- Initial server build succeeded with zero test sources. Android offline dependency resolution first failed on uncached Hilt/ASM artifacts. An online build downloaded dependencies. CanHub publishes cropper 4.5.0 on Maven Central, and the QA copy was adjusted to use the published coordinates. [CanHub release notes](https://github.com/CanHub/Android-Image-Cropper/releases/tag/4.3.3)

## Test matrix

API results apply to the running server and MongoDB, not Android UI. Physical phone workflow results are recorded separately below.

| Scenario | Expected | Baseline API actual | First repair |
|---|---|---|---|
| Register, duplicate email, missing fields, valid and invalid sign-in | 201/409/400/200/401 respectively | PASS | Added bounded server-side registration validation. |
| Authentication required | Invalid and absent bearer token get 401 | PASS | Signing and verification now use a required shared secret and an expiry claim. |
| Credential protection | Responses exclude password hashes | FAIL. Register, login and profile responses included the hash. | Public response model replaces serialized database documents. |
| Own profile and edits | Own lookup/edit succeeds, cross-user edit forbidden | PASS | Added profile field bounds and omitted private fields for other viewers. |
| User discovery and default profile | Lookup by 12-digit ID and portable default image | Lookup PASS. Image FAIL. Old LAN URL returned. | Same-origin image paths and legacy URL normalization. |
| Group creation/listing | User can create/list own groups only | Own operations PASS. Outsider listing returned 200. | Require caller match and validate initial membership. |
| Invitations and decisions | Members can invite and only the recipient can list/decide; statuses validated | Happy path PASS. Cross-user reads/decisions and outsider invitation FAIL. Unsupported state returned 200. | Added membership, recipient and status checks. |
| Entry and repayment batch | Members can create valid debits, reverse entries and read history | Valid calls and amount round trip PASS. Negative and unrelated participant inputs accepted, outsider read/write FAIL. | Added group, participant, amount, description and batch bounds. |
| Clear/delete ledger | Only authorized participants or group members can delete/clear | Authorized clear and empty reload PASS. Unauthorized deletes and clear returned 200. | Added role-independent membership and participant checks. |
| Leave and delete group | Leaving hides the ledger and outsiders cannot delete | Leave and basic delete PASS. Former member ledger access and outsider deletion FAIL. | Added group membership checks. |
| Upload image twice | Distinct, retrievable images with portable paths | Both uploads returned 200 with the same literal path. FAIL. | Bounded PNG/JPEG upload, random filename and packaged default asset. |
| WebSocket events, background jobs, welcome email | Authenticated events and optional configured welcome email | NOT TESTED in initial API run. No system notifications or job scheduler in source. | Welcome email credentials moved to environment. |

The baseline API run passed 30 assertions and failed 21. The repaired server passed all 52 assertions, including one additional check that downloaded and byte-compared an uploaded image. Both runs exercised only disposable records. Some baseline checks intentionally attempted unauthorized mutations on those records.

## Physical phone workflow coverage

| Area | Intended assertions | Initial status |
|---|---|---|
| Install and launch | Launch existing Hisab and install a separate debug APK | PASS for cold launch of the installed package and installation/launch of the separate QA APK. ADB reported 2,010 ms for one original cold launch. |
| Registration/login/session | Empty or invalid inputs, sign-in, keyboard/back, logout and relaunch | PASS for empty email validation, password visibility, signup navigation and QA registration through MongoDB. |
| Group navigation | Empty dashboard, create group, enter detail, cancel/confirm leave or delete | PASS for creation and detail navigation with one controlled member. Leave and delete were exercised through the API. |
| Ledger and repayment | Specific and split entry, history, repayment and clear history | PASS for the split-expense option and repayment empty-state message on the phone. Server-side mutations and retrieval were exercised through the API. |
| Notifications/invitations | Empty state, invite by ID, accept/decline and refreshed group | PASS for opening notifications, returning to the dashboard, and opening and cancelling the invite dialog. Creation and acceptance were exercised through the API. |
| Profile and settings | Display name, email and ID, theme, crop/picker/cancel and upload | PASS for opening profile and opening/cancelling logout confirmation. Profile update and uploads passed through the API. Theme and picker remain unverified on the phone. Name editing and ID copying have no UI control. |
| Lifecycle, network and permissions | Back/rotation, background/relaunch, offline error/recovery and permission denial | PASS for warm resume and restoration of the signed-in route on cold start. FAIL for immediate cold-start group display. Polling read the user before asynchronous session restoration and then waited up to 15 seconds. Dashboard and notifications now start polling as soon as the session emits the restored user. Rotation, loss of connectivity, permission denial and the Android retest remain open for final QA. |
| Admin and legacy screens | Existing admin role and gated access, legacy Add Hisab reachability | NOT TESTED in Android UI yet. Legacy Add Hisab has no registered route. |

The original Android build reached `assembleDebug` and `testDebugUnitTest NO-SOURCE`. Initial lint reported 3 errors and 95 warnings. All three errors came from using `pref` where Android expects `sharedpref`; both backup rule files were corrected and the encrypted session preference was explicitly excluded.

The final C51 pass verified immediate group display on cold relaunch and read the earlier disposable account and group after the API update. [Final test evidence](FINAL_TEST_REPORT.md)
