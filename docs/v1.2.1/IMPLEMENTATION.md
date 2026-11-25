# Hisab 1.2.1 implementation

Source requirement: user attachment `e798426a-9333-4c69-a633-86230a825714/Pasted text.txt`, reviewed on 24 November 2026. Existing production baseline: `d510f13`, application package `com.example.hisab`, Android version 1.2.0/code 3. The compatible signing certificate ends in `84bce285`.

Work proceeds on `codex/hisab-v1.2.1`. Do not publish the stable tag or apply production database changes until every required feature and release check is complete. Preserve existing user data. Use disposable databases and the separate QA application for destructive tests. No Gravitas skills are used.

## Ordered phases

| Phase | Scope | Status |
|---|---|---|
| 0 | Architecture, compatibility and requirements audit | Complete |
| 1 | Cursor pagination, virtual lists, authoritative summaries, bounded member loading | Complete |
| 2 | Private archives, expiry, durable recovery, recovered read-only views and permanent account deletion | Complete |
| 3 | Shared feedback and confirmation components | Complete |
| 4 | Reusable skeletons, loading, empty/error/retry behavior | Complete |
| 5 | Startup/session/consent navigation coordination | Complete |
| 6 | Settings redesign and visible version presentation | Complete |
| 7 | Home and Hisab detail presentation | Complete |
| 8 | Shared/member pictures, durable storage and account-aware image caching | In progress |
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

No physical device is currently connected. Device/visual and full cross-feature checks remain release gates in phase 12. Durable group/media assets and cache invalidation are scheduled for phase 8; groups do not have pictures in the current baseline. A low-memory extra Android invocation initially had a PowerShell argument-parsing error; the completed full Android build passed independently (39 tests, lint, both QA APKs). Run future builds sequentially with quoted Gradle properties on this 8 GB machine. No production accounts or data were modified.

## Phase 3 implementation and local evidence

All form/history/confirmation dialogs use the common HisabFormDialog frame; confirmations preserve destructive styling and busy dismissal guards. Shared feedback provides themed success/error/warning/information labels, icons, screen-reader announcements and an accessible dismiss action. Feedback clears the keyboard/navigation bars and keeps the dismiss icon legible against both theme variants. Added missing creation, invitation, recovery and permanent-deletion success messages, always after server confirmation. Password form errors remain beside the form.

Fixed invitation double submission by acquiring the pending state synchronously before launching work. Decisions are canceled and scoped by account generation; cancellation cannot produce a false failure or stale success after an account switch. Added two meaningful unit tests for coalescing, failure preservation, server-confirmed success and account-switch cleanup. Added two device tests for busy destructive confirmation/cancel semantics and announced, dismissible error feedback.

Local validation: all 41 Android unit tests, lint and both QA APK builds passed. A final contrast-only adjustment passed targeted Kotlin compilation and lint. GitHub at phase-2 commit 8e36814 passed Android tests/lint/APK/signature/launcher checks and backend HTTP integration. Its emulator job stopped before tests because Maven Central returned HTTP 429 for a test-platform dependency. Added a bounded instrumentation retry only for dependency HTTP 429/5xx; actual app-test failures still fail immediately. GitHub device verification is pending; no production deployment was made.

Phase 3 GitHub verification: commit 2387903 passed all three jobs: backend tests/container/dependency audit, Android tests/lint/APK/signature/launcher checks, and API 34 emulator instrumentation (10 passed, two explicit process-restart fixture skips). The previous HTTP 429 download failure is superseded by this successful full run.

## Phase 4 implementation and verification in progress

Added shared contextual skeletons for Hisabs, balances, transaction histories, invitations, members, archives, recovered copies and profiles. Skeletons respect system animator settings, including the API 24/25 fallback, and update drawing opacity without recomposing the containing lists. Existing rows remain during refresh; first-load errors have retry controls rather than misleading empty states. Fixed an empty filtered member page hiding the next-page control. Archived list refresh uses server time for expiry filtering so a wrong phone clock cannot hide valid archives. Background errors on an initially empty private directory persist until explicit retry.

The first local pass compiled the skeletons and passed 42 unit tests; lint caught an unguarded API-26 animation call while Hisab supports API 24. Added the legacy fallback before publication. Final source adds server-clock and error-retention unit cases plus device coverage for recovery loading/failure/retry and empty member-page continuation. Verification of the final source is delegated to the project's existing GitHub CI because repeated local builds exhaust this 8 GB computer's memory; do not mark the phase complete until those checks pass.

Phase 11 scalability audit: splitting an expense correctly uses the full authoritative membership list, independent of loaded profiles. Review support for groups whose split exceeds the bounded 1,000-entry request limit; do not chunk financial writes into non-atomic partial batches.

Phase 4 final verification: commit 7e4f869 passed all three GitHub jobs: backend tests/integration/container/dependency audit, Android tests/lint/APK checks, and API 34 emulator tests. The API-24 compatibility fallback, server-clock expiry and retry/empty-page cases are included in this verified source.

## Phase 5 implementation; verification pending

Startup now creates session and privacy view models while the existing 3D animation runs. The splash remains until restoration and consent resolution finish, then mounts the correct navigation destination once. It does not restart for later consent checks or rotation. A slow initialization displays a neutral account-ready status; checking/unavailable routes no longer mount a Privacy Policy review or its spinner. Actual required policy review and intentional policy access remain intact.

Session restoration and privacy checks are bounded at 12 seconds; policy fetch has a five-second fallback. Offline continuation requires an acknowledgment cached for the same account and matching bundled revision, and is prohibited when a required review is pending. A stale policy document cannot satisfy a newly required revision. Incomplete/rejected saved sessions return to sign-in; late profile responses cannot overwrite a switched account. Required acknowledgment writes remain explicit and retryable, never automatic.

Added six startup-flow unit tests covering accepted-session sequencing, stalled I/O, offline consent, stale policy revisions, incomplete credentials, rejected sessions and account-switch races. Added a device test for neutral checking/error/retry UI without policy-review controls. Existing encrypted TokenManager and PrivacyRepository implement small startup interfaces so the real state machines can be tested without substituting production behavior. Final GitHub checks are pending before completing this phase.

Phase 5 final verification: commit 4da9393 passed all three GitHub jobs, including the API 34 emulator. Eight startup unit scenarios are included in the final source, along with the neutral startup status device test. The retained startup destination prevents later account/consent updates from recreating the graph; a fresh process initializes again.

## Phase 6 implementation; verification pending

Reorganized Settings into account/profile, preferences, and lower application/support sections. Added current account information from the account-scoped local profile stream, profile and account-management navigation, consistent icons/cards/indicators, Android notification-settings navigation, and the shared feedback host for preference success/failure. Updates retain the existing verified download/installer flow and sit above About, Privacy and data, and FAQs. Both Settings and About display Version 1.2.1 without a build label. Internal versionCode is now 4, preserving package and signing identity; no release has been published.

The first frame now uses the existing persisted theme mirror rather than briefly drawing Light before DataStore emits. Preference read failures fall back safely, theme writes are coalesced and reported, and the profile's theme write now sits inside its existing error handler. Updated recovery/deletion FAQ answers and the existing About device test to require the public version label and reject the build label. Detailed in-app push preferences are added in phase 9; the current notification row opens actual Android settings rather than a placeholder.

Final GitHub validation is pending before completing this phase.

Phase 6 final verification: commit e49516c passed all three GitHub jobs, including the About version/hidden-build device assertion. No stable release or production deployment has occurred.

## Phase 7 implementation; verification pending

Added an authenticated, no-store Home summary that streams reduced actor/counterparty balances across all active Hisabs. It uses the same exact/legacy rounding helper as the group ledger. Groups remain separate when totaling debts and receivables, and former-membership ledgers are excluded. Added debtor/creditor lookup indexes; neither history pages nor loaded group-card counts determine money totals. Summary refresh/error state is independent of list paging, retains last-known values on failure, and clears on account changes.

Refined Home cards, hierarchy, summary presentation and primary actions. Details now have a clearer group header, authoritative record count and personal position card; member debt/receivable metrics share the same design. Kept keyed virtualization and paged history. Member selection is disabled during financial submissions. Missing legacy timestamps display Date unavailable instead of a fabricated January 1970 date; missing deleted identities are labeled as such.

Added summary/paging/failure/account-scope unit coverage, shared monetary rounding/non-finite aggregate tests, currency/date display tests, and independent HTTP Decimal checks for complete Home totals, opposing obligations in different ledgers, former-member exclusion and authentication. Final GitHub checks are pending before completion. Group pictures are wired to this header and the Home cards in phase 8.

Phase 7 final verification: commit 0ba7eeb passed all three GitHub jobs: backend tests/integration/container/dependency audit, Android tests/lint/APK checks, and API 34 emulator tests. All 21 backend unit tests also passed locally. The first push encountered a network connection failure; the retry succeeded. No production deployment or data changes occurred.

## Phase 8 implementation; verification pending

New profile and shared Hisab images are normalized into bounded square JPEGs and stored as immutable versions in MongoDB, replacing ephemeral filesystem writes. Uploads inspect format, dimensions and byte limits before decoding, subsample oversized images, crop and strip source metadata. Profile upload/removal updates the authoritative account field atomically; active group members can update/remove their shared picture atomically. Field-specific profile patches prevent name/theme edits from overwriting newer photos. Existing device-only crops migrate after confirming the account's remote profile, with an already-synchronized remote version taking precedence.

Authenticated image reads and batched authorization enforce current membership, invitation and private archive/recovery access before processing validators. Picture references follow private snapshots through independent recovery and expire with archives. Permanently deleting an account removes its personal image bytes/references while preserving shared group pictures for remaining members. A bounded background sweep removes old unreferenced versions after a grace period.

Home and group headers show shared pictures; active details expose gallery/crop/upload/remove controls and bounded current-member picture metadata refresh while visible. Read-only recovered views show historical pictures without editing controls. The app stores authorized immutable versions in a private, account-scoped 32 MB cache, coalesces concurrent reads, rechecks access in bounded batches while visible, removes revoked versions and clears previous-account images on logout/switch/deletion. Offline use retains only that account's previously authorized cached files.

Backend source compiled locally and the initial Android compilation passed. Added image-decoding unit tests, disposable HTTP coverage for permissions/version replacement/retention/deletion, a real server-process restart check, and a native cache restart/revocation/account-isolation test. Final tests, lint and GitHub verification are pending; phase 8 is not complete yet. No production deployment or data changes occurred.
