# Development

The Android and server Gradle wrappers run independently. Edit a feature in its package and validate the affected app path and HTTP contract together. Hilt creates the Android repositories and Room database. The server maps MongoDB document IDs to the existing HTTP IDs.

## Troubleshooting

| Symptom | Check |
|---|---|
| Gradle cannot find an artifact in offline mode | Run once without `--offline` from a machine with Maven Central, Google Maven and Gradle distribution access. |
| Android cannot reach the API | Check `adb reverse --list`, the configured debug origin and `GET /healthz` on the host. |
| ADB shows `unauthorized` | Unlock the phone and approve the USB debugging prompt. |
| APK update fails with a signature mismatch | Use the original signing key for an in-place update, or use the separate QA package for disposable tests. |
| Server exits during initialization | Confirm JDK 17, a writable upload directory, a replica set URI, application database permissions and existing unique index compatibility. |
| Sign-in was invalidated after a server update | A rotated JWT signing key invalidates existing tokens. Sign in again against the intended backend. |

Use the [migration plan](MIGRATION_PLAN.md) before pointing a new backend at existing data.
