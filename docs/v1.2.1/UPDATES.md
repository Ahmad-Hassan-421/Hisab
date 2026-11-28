# APK release metadata

Publish stable releases only after the implementation and release gates in IMPLEMENTATION.md pass. The backend discovers the repository's latest stable GitHub release and validates its APK and `hisab-update.json` assets. Drafts and prereleases are rejected. Production APKs must retain `com.example.hisab`, the original signing identity, and a greater Android version code.

The JSON manifest contains:

```json
{
  "versionName": "1.2.1",
  "versionCode": 4,
  "applicationId": "com.example.hisab",
  "sizeBytes": 12345678,
  "sha256": "replace-with-the-actual-64-character-lowercase-apk-sha256",
  "minimumSupportedVersionCode": 1,
  "required": false
}
```

The example size and checksum are placeholders, not publishable metadata. `minimumSupportedVersionCode` and `required` are optional; omitted values preserve optional-update behavior for old manifests. A minimum code must be positive and no greater than the release code. Set `required` only for an intentional mandatory release; an installation below the declared minimum also receives mandatory presentation.

The APK name is `hisab-vVERSION.apk` under tag `vVERSION`. GitHub's asset size and SHA-256 digest must match the manifest. The backend derives the release date and bounded notes from GitHub's published release, and publishes normalized UTC dates through `/api/releases/latest` and `/releases/latest`. These public responses contain release information only and use `Cache-Control: no-store`.

Startup, manual checks and update push taps share one coordinator and modal. Automatic checks do not block startup, remain quiet on network errors and do not repeatedly prompt during the same process session. Explicit checks can reopen an optional postponed update. Release metadata never grants access to account data.

The existing DownloadManager/FileProvider installer flow validates the canonical download URL, exact size, checksum, application ID, version and installed signing certificate. It stores download state privately, reattaches downloads after a process restart and revalidates staged files before offering them again. The user must approve installation through Android. Obsolete state is removed after upgrade; cancelled and invalid downloads are cleaned up.

Keep `HISAB_RELEASE_NOTIFICATIONS_ENABLED` disabled until phase 13 confirms backend compatibility and all stable assets. Older installations without Firebase Messaging cannot receive their first upgrade by push; use their existing manual updater or the compatible shared APK.
