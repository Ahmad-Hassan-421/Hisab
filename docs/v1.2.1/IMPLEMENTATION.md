# Hisab 1.2.1 implementation

Source requirement: user attachment `e798426a-9333-4c69-a633-86230a825714/Pasted text.txt`, reviewed on 24 November 2026. Existing production baseline: `d510f13`, application package `com.example.hisab`, Android version 1.2.0/code 3. The compatible signing certificate ends in `84bce285`.

Work proceeds on `codex/hisab-v1.2.1`. Do not publish the stable tag or apply production database changes until every required feature and release check is complete. Preserve existing user data. Use disposable databases and the separate QA application for destructive tests. No Gravitas skills are used.

## Ordered phases

| Phase | Scope | Status |
|---|---|---|
| 0 | Architecture, compatibility and requirements audit | Complete |
| 1 | Cursor pagination, virtual lists, authoritative summaries, bounded member loading | Complete |
| 2 | Private archives, expiry, durable recovery, recovered read-only views and permanent account deletion | Complete |
| 3 | Shared feedback and confirmation components | In progress |
| 4 | Reusable skeletons, loading, empty/error/retry behavior | Pending |
| 5 | Startup/session/consent navigation coordination | Pending |
| 6 | Settings redesign and visible version presentation | Pending |
| 7 | Home and Hisab detail presentation | Pending |
| 8 | Shared/member pictures, durable storage and account-aware image caching | Pending |
| 9 | Multi-device push registration, durable event delivery, preferences and deep links | Pending implementation; configuration located, delivery unverified |
| 10 | Shared automatic/manual/push update flow and release metadata | Pending |
| 11 | Security, safe migrations and cross-feature consistency audit | Pending |
| 12 | Functional, scalability, upgrade and device verification | Pending |
| 13 | Signed stable 1.2.1 release, backend rollout, metadata and notification verification | Pending |

## Audit findings and implementation constraints

- Kotlin/Compose already uses keyed `LazyColumn` lists. The bottleneck is unbounded API responses, polling whole lists every 15 seconds, syncing full ledger history, and fetching member profiles one at a time. Keep virtualization and introduce bounded cursor pages and batched profile reads.
- Financial balances currently depend on every ledger entry being downloaded into Room. Paging that list without changing calculations would display incorrect debts. Introduce authoritative backend aggregates separately from paginated display history, preserving three-decimal precision and legacy rounding behavior.
- Existing deletion removes a shared group and all its entries for any member. New personal deletion must isolate the actor's archive/view and preserve other active members' access and records. Last-member deletion can move the remaining source out of active storage atomically.
- The recovery source must be `hard_copy_of_user_data`, private to authenticated owner IDs, with BSON-date TTL expiry at two calendar months. Durable database jobs perform the delayed recovery. Delivered read-only copies must have independent lifetime and cannot be written through active ledger endpoints.
- Privacy data requests currently create a receipt; they do not perform recovery or account deletion. Replace the correction option, implement genuine workflows and require password reauthentication for account destruction. Retain shared financial records safely with anonymized departing identities.
- Privacy checking is mounted after the timed splash and renders the review screen while the status is still unknown. Coordinate session/consent resolution with startup; render the policy only for intentional access or a confirmed consent requirement.
- Profile photos are currently device-local in the profile flow, and server upload files are ephemeral on the existing Render Free instance. Use the existing MongoDB infrastructure for durable media rather than silently provisioning paid services. Backend metadata is authoritative; local caches must be scoped and invalidated.
- Feedback, refresh/error components, policy assets and a secure signed-APK update mechanism exist. Extend them instead of creating parallel systems.
- No Firebase Android configuration or Firebase server credentials were found. Ask for the existing Firebase project identifier early; never place private keys in source or report push delivery as verified without real credentials and devices.
- Existing production Android signing identity is available locally. Keep it, increment the internal version code, preserve Room/session migrations, and do not publish untested release metadata.

Each phase requires implementation evidence, relevant tests, and a concise user-facing completion report before proceeding. Archive/recovered list pagination will use the same pagination contract when those collections are introduced in phase 2. Phase status is evidence-based; pending work is not a completed feature.

## Phase 1 evidence

Implemented authenticated, signed, scope-bound cursor APIs and keyed paged Compose lists for groups, invitations, current and suggested members, and all/debt/payment/pair histories. Authoritative MongoDB aggregates retain three-decimal and legacy-fraction balance behavior independently of loaded pages. Batch input is bounded at 1,000 with idempotent replay. Older API paths remain available.

Validation: 18 backend unit tests, 96 existing HTTP regression checks, 37 Android unit tests, Android lint, debug and instrumentation APK compilation passed. Disposable MongoDB integration covered 1,238 groups, 5,147 entries, 517 invitations, member privacy, exact/legacy balance parity, concurrent insertion, cursor isolation, revision conflicts, 1,000-entry writes and replay. No production data was changed.

Firebase project confirmed by user: hisab-90ff2. User configured FIREBASE_SERVICE_ACCOUNT_JSON in Render; clarified that this requires the complete service-account JSON rather than only its private_key block. Credential validity and actual push delivery are not yet verified.


Firebase follow-up: user confirmed that the full service-account JSON is set in Render. Matching public Android configuration was found at C:/Users/User/Downloads/google-services (4).json (project hisab-90ff2, package com.example.hisab). No private credential content was read or added to the repository.

## Phase 2 evidence

Implemented normalized private snapshots in hard_copy_of_user_data and archived_hisab_records, with authenticated owner IDs, private owner email snapshots, public historical members, complete history, deletion timestamps and BSON-date TTL expiry after two UTC calendar months. Normalized rows avoid the 16 MB document limit. Shared deletion removes only the actor's access; a last-member deletion removes the active source atomically. The dashboard now exposes deletion with confirmation, optimistic removal, safe error restoration and duplicate-request protection.

Persistent MongoDB recovery jobs become eligible after three minutes, retry in the server worker, survive process restarts and publish a complete read-only copy transactionally. Integrity checks reject partial sources. Delivered records have no expiry; deleting a delivered copy permanently deletes its source, rows and job. Archived/recovered directories, historical members and filtered history use authenticated cursor pages. Privacy screens provide selection, recovery confirmation/status, historical views and the account selection/password confirmation flow. Removed correction submission from frontend and backend. The prior access receipt endpoint remains for older clients.

Permanent account deletion rechecks the password and current account, removes private data/jobs/tokens, queues safe owned-file cleanup, deletes exclusively personal groups and anonymizes identity in retained shared ledgers and other owners' copies. Transactions serialize account/membership changes, uploads and invitations to prevent deletion races. Updated the bundled retention/deletion notice and required acknowledgment revision. Existing users are not acknowledged automatically.

Validation: 19 backend unit tests (including calendar-month/leap-year expiry), 96 original HTTP regression checks, 39 Android unit tests, Android lint and QA/instrumentation APK compilation passed. Real disposable MongoDB integration covered simultaneous deletion of 1,205 entries, authorization boundaries, a process restart with a pending job, actual worker delivery, expired-source rejection, independent copy lifetime, immutable copy APIs, permanent deletion replay, incomplete-source rollback and retry, shared-record anonymization and 75-row archive/recovered cursor directories. Private responses were checked for no-store headers. Final logs contain only the intentional incomplete-source warning, with no request error logs. Fixed a response hook that tried to append headers after an authentication challenge and a transaction retry interval too short for large concurrent archives.

No physical device is currently connected. Device/visual and full cross-feature checks remain release gates in phase 12. Durable group/media assets and cache invalidation are implemented in phase 8; groups do not have pictures in the current baseline. A low-memory extra Android invocation initially had a PowerShell argument-parsing error; the completed full Android build passed independently (39 tests, lint, both QA APKs). Run future builds sequentially with quoted Gradle properties on this 8 GB machine. No production accounts or data were modified.

## Phase 3 implementation and local evidence

All form/history/confirmation dialogs use the common HisabFormDialog frame; confirmations preserve destructive styling and busy dismissal guards. Shared feedback provides themed success/error/warning/information labels, icons, screen-reader announcements and an accessible dismiss action. Feedback clears the keyboard/navigation bars and keeps the dismiss icon legible against both theme variants. Added missing creation, invitation, recovery and permanent-deletion success messages, always after server confirmation. Password form errors remain beside the form.

Fixed invitation double submission by acquiring the pending state synchronously before launching work. Decisions are canceled and scoped by account generation; cancellation cannot produce a false failure or stale success after an account switch. Added two meaningful unit tests for coalescing, failure preservation, server-confirmed success and account-switch cleanup. Added two device tests for busy destructive confirmation/cancel semantics and announced, dismissible error feedback.

Local validation: all 41 Android unit tests, lint and both QA APK builds passed. A final contrast-only adjustment passed targeted Kotlin compilation and lint. GitHub at phase-2 commit 8e36814 passed Android tests/lint/APK/signature/launcher checks and backend HTTP integration. Its emulator job stopped before tests because Maven Central returned HTTP 429 for a test-platform dependency. Added a bounded instrumentation retry only for dependency HTTP 429/5xx; actual app-test failures still fail immediately. GitHub device verification is pending; no production deployment was made.
