# Hisab final project readiness report

Testing took place on November 9, 2026. This report was finalized on November 10, 2026.

The Android app and Ktor API now use feature packages, maintain the existing application and datastore identities, and build with JDK 17. The work removed embedded credentials, closed cross-account access paths, corrected uploads and ledger behavior, and prepared GitHub CI and a Render Blueprint. Physical tests used the separate QA app on a C51 and disposable records. The existing phone installation and its data were preserved.

## Resulting structure

```text
Hisab/
  Hisab/app/
    src/main/java/com/example/hisab/
      features/{auth,groups,ledger,invitations,profile,admin}
      data/{db,remote}
      ui/  di/  util/
    src/test/
  hisab-server/
    src/main/kotlin/com/hisab/server/
      features/{accounts,groups,ledger,invitations,media,realtime}
      database/  models/  plugins/
    Dockerfile
  scripts/qa/
  .github/workflows/
  docs/
  render.yaml
```

Android features own their screens, ViewModels and relevant repositories. Hilt supplies the single Room database and shared transport. Ktor features register the existing routes with shared authentication, validation and error handling. The MongoDB document mappings and Room schema version were retained. [Architecture](ARCHITECTURE.md), [Modules](MODULES.md), [Data flow](DATA_FLOW.md)

## Feature verification

| Feature | Initial finding | Change | Final evidence |
|---|---|---|---|
| Login and registration | Password hashes returned in API responses. Embedded JWT and administrator enrollment secrets. | Safe response DTOs, required JWT secret and expiry, regular role on registration, bounded validation and unique indexes. | API and physical QA registration/sign-in PASS. Concurrent registration and invalid/expired tokens passed. |
| Session, splash and logout | Cold relaunch initially showed the signed-in screen before the group list loaded. | Polling follows restored user state. An authenticated 401 invalidates the matching token and returns to login. | C51 PASS for in-place update, reauthentication, earlier group, logout, warm resume and cold relaunch. |
| Dashboard and group creation | Group list accepted another user's ID. | Query identity matches the bearer token and group creation is scoped to the requester. | API and C51 group creation PASS. Cross-user listing rejected. |
| Group leave and deletion | Outsider could delete groups. Whole-document leave updates could lose concurrent changes. | Membership checks, atomic array updates and transactional group/ledger/invitation deletion. | API PASS for leave, former-member denial and group deletion. Group deletion has no wired Android control. |
| Ledger and history | Outsiders could read or mutate records. Room kept entries after a server-side clear. | Membership and participant checks, atomic cache replacement and past-member history. | API PASS for ownership and ledger reload. C51 loaded the group and displayed a persisted split. |
| Specific and split expenses | Invalid amounts and unrelated participants accepted. Floating-point splits could lose precision. | Validated entry writes, decimal arithmetic and splits that conserve thousandths. | Five JVM tests and API checks PASS. On the C51, PKR 12.345 split plus PKR 0.655 specific expense displayed PKR 13.000. |
| Repayment and settlement | Several repayments were sent independently. A failed write could be silent. | Batched repayments, explicit failure state and server check that only settled history is cleared. | API PASS for repayment, unsettled-clear rejection, settlement clear and empty reload. Android completion remains unverified after the phone switched apps. |
| Entry deletion | An outsider could delete an entry by ID. | Require the actor to be an entry participant and a current group member. | API denial PASS. Individual entry deletion has no Android UI action. |
| Invitations and notifications | Outsider could send, list or decide invitations. Duplicate requests could overwrite unrelated pending records. | Scope requests, make replay stable, and transact accepted membership and request state. | API and C51 invite/send/accept PASS. The C51 reloaded the accepted membership. |
| Realtime | Socket transport existed alongside REST polling. | Bounded frames and thread-safe connections. | WebSocket upgrade and invitation event PASS in the API suite. Android currently polls. |
| Profile, theme and images | A hardcoded LAN origin and repeated literal filename broke independent uploads. | Unique image paths, upload limits, same-origin URLs and preserved legacy path compatibility. | API PASS for profile updates, uploads and byte retrieval. Unsafe URIs rejected. C51 profile and logout dialog PASS. Theme sync and picker/crop remain unverified on the phone. |
| Admin | Role-gated view of device-local entries. | Removed registration backdoor and restricted session restoration. | Verified through source review. The admin screen's data source is the device cache. Physical admin access remains unverified. |
| Offline, lifecycle and permissions | Network-backed groups with a Room read cache. | Explicit failure handling and retained local data. | C51 warm resume and cold relaunch passed. Android network restoration, rotation and permission denial remain unverified. No offline mutation queue. |
| Legacy and background functionality | Dormant Add Hisab composable and Firebase rule files. No OS notification or job scheduler. | Existing assets retained. | Source inventory. These items have no active device workflow. |

## Verification

- Android clean debug and unsigned release builds passed. Five JVM tests passed. Debug lint reported 0 errors and 95 warnings, and release vital lint passed.
- The final server clean build passed. It has no backend unit-test source set. The separate API suite passed 73 checks against disposable MongoDB, including concurrency, failure responses and stored-record compatibility.
- The final C51 log recorded 29 passing checks with the isolated APK. The phone switched foreground apps during repayment. The report identifies the remaining device workflows separately.
- The database readiness endpoint, startup failure, listener shutdown and upload persistence across startup passed locally. Android release validation accepted HTTPS and rejected missing or cleartext origins.
- Updated Netty to 4.1.140.Final, Logback to 1.5.38 and Jackson to 2.23.0 after finding matches for the previously resolved versions. The targeted OSV checks returned no matches for the updated components checked. Kotlin server compilation moved to 2.1.21 to support the Jackson Kotlin module. [OSV query API](https://google.github.io/osv.dev/post-v1-querybatch/)
- A single aggregation pass replaces repeated entry scans for balance calculations. Timing and memory samples, exact commands and device evidence are in the [final QA report](QA/FINAL_TEST_REPORT.md).

## GitHub and Render

The workspace began without Git metadata. A local `main` branch was initialized. Both projects built from a disposable clean checkout. The candidate scan found no credential matches. CI covers wrapper validation, Android tests and packaging, backend packaging, repository scanning and API integration against a disposable replica set. The workflow YAML and its Bash script passed local parsing. A GitHub repository and first hosted run remain to be configured. [Security and rotation](../SECURITY.md)

The Render Blueprint defines one Docker Ktor service, `GET /healthz`, an external MongoDB replica set or sharded cluster, and a 1 GB persistent disk for profile uploads. It selects the paid 0.5 CPU/512 MB compute plan and disables automatic deploys. It passed the published Render schema. The JRE distribution passed production-mode local tests. A Docker engine and Render deployment were unavailable for this run. The database URI, region, approval of resources and hosted verification remain with the owner. [Deployment](RENDER_DEPLOYMENT.md), [Environment](ENVIRONMENT_VARIABLES.md), [Migration and rollback](MIGRATION_PLAN.md)

## Readiness

| Area | Verdict | Basis |
|---|---|---|
| Android application | READY WITH WARNINGS | Final debug APK and core C51 flows passed. Production origin and signing remain. |
| Features and regression tests | READY WITH WARNINGS | Core device and API flows passed. Broader hardware, network and media-picker coverage remains. |
| Architecture and maintainability | READY | Feature packages, shared infrastructure, clean builds and documented boundaries. |
| GitHub push | READY WITH WARNINGS | Local checks passed. Rotate exposed credentials and review the first hosted CI run. |
| Render configuration | READY WITH WARNINGS | Schema and local runtime passed. Container execution remains unverified. |
| Live Render deployment | NOT READY | Requires approval, credentials, backup rehearsal, container startup and hosted acceptance. |

Rotate the previously embedded MongoDB and Brevo credentials and audit legacy administrators before publishing. Use a new JWT secret and expect affected sessions to sign in again. Keep the original upload files during migration. Bytes overwritten by the old fixed filename require a separate backup. Use the [production checklist](PRODUCTION_CHECKLIST.md) to rehearse cutover and rollback with controlled records.
