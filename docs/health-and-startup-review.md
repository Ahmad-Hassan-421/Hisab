# Health and startup review

`GET /health` already existed in `Routing.kt`. It now lives in `features/health/HealthRoutes.kt` alongside the existing readiness routes. Every request checks MongoDB with a two-second deadline. A successful check returns HTTP 200 and `{"status":"ok"}`. A database error or timeout returns HTTP 503 with `{"status":"unavailable"}`. The endpoint is public, returns no database or account details, and disables response caching.

The Android starting theme uses a transparent icon on API 31 and later and a plain background on earlier versions. The Compose animation remains the visible introduction. The old static logo themes and drawable wrappers were removed. The animated splash now remembers completion across activity recreation and respects the active dark theme.

The review found and removed a startup migration that recorded privacy acceptance for existing users without their acknowledgment. The previous backend CI container build failed on Maven Central HTTP 429 responses. Docker builds now cache Gradle downloads and retry only transient HTTP dependency failures, up to three attempts.

Backend verification passed nine JVM tests, including five health HTTP tests, and all 96 existing API and calculation checks against disposable MongoDB. The account reset was also tested on disposable MongoDB. It erased account-related records, invalidated old credentials and sessions, preserved invitation templates, and permitted fresh signup. CI additionally checks an actual database outage and privacy state after a server restart.

The account reset command is `python scripts/reset_accounts.py --database hisab_db`. It previews counts with the operator-supplied `MONGO_URI` and matching `MONGO_DATABASE`. Adding `--apply` deletes accounts, groups, transactions, invitations, admin audit records, privacy requests, and recovery counters in a MongoDB transaction. It rejects an unrecognized collection so its contents can be reviewed first. Quiesce application writes during the reset and verify the zero counts afterward. Uploaded photos on the Render volume require separate removal. Existing device caches and provider backups are outside this database operation.

On 22 November 2026, read-only checks of the existing deployment returned HTTP 200 and `ok` at `/healthz` and HTTP 404 at `/health`. This review changes repository code without initiating a deployment. Live database deletion requires the authorized Atlas connection, and removal of uploaded profile files requires access to the hosting volume.
