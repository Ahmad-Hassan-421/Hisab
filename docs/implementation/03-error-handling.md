# Android error handling and recovery

Android classifies offline connections, timeouts, unavailable services, unreadable responses, expired sessions, missing records, permission failures, validation errors, conflicts, local storage failures, and rate limiting. Screens display fixed user-facing messages. Diagnostic entries contain the operation, failure category, and exception class.

Home, Hisab detail, invitations, profile, and the local ledger view show a retry action after a failed load. Previously loaded content remains visible when it is still usable. The sign-in screen explains when a session expires. Profile saves report success after the server confirms the update.

Ledger forms retain inputs after a failed save. An uncertain outcome or a conflict requires a successful ledger reload before another write is enabled. The app never automatically retries that write. The existing Room migration adds the ledger type while retaining existing rows, and unsupported migrations no longer fall back to deleting the database.

The Phase 1 debug build and lint succeeded. All 22 JVM tests passed, including coverage of HTTP and transport classification, malformed responses, storage failures, cancellation, dashboard retry coalescing, and ledger recovery after a timed-out write. Lint reported zero errors and 97 warnings. An in-memory SQLite check confirmed that the migration SQL preserves an existing row and assigns the expected default. Device-level screen and Room upgrade checks remain outstanding.

## Exception boundaries

Android now records profile, account restoration, realtime, notification, and update-download failures through the same diagnostics. Rejected profile writes do not enter the Room cache as if the server accepted them. Background profile reads can still use cached data, and session expiration propagates to the sign-in flow. The unused Android welcome-email stub that logged personal information was removed. The backend's existing welcome-email integration remains in use.

Ktor responds to route errors, rejected authentication, and handled exceptions with a JSON object containing `code`, `error`, and `requestId`. Each request receives a server-generated `X-Request-ID`. Database and unexpected failure logs include the request ID and exception class. The health probe retains its minimal response. GitHub release lookup errors return fixed messages.

The extended integration suite verifies matching correlation IDs and safe JSON bodies for every expected error response. It also checks that failed ledger writes leave persisted records intact.

Phase 3 verification passed with 23 Android JVM tests, a successful debug build and lint, and 90 API integration checks on disposable MongoDB. The backend packaged successfully. The API test suite observed malformed input, authorization failures, conflicts, unsupported uploads, and missing routes. The server's unexpected exception branch was reviewed and compiled. It was not fault-injected in the production configuration.
