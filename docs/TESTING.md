# Testing

Use JDK 17. On Windows use `gradlew.bat` in place of `./gradlew`.

| Scope | Command |
|---|---|
| Android debug build, unit tests and lint | In `Hisab/`, `./gradlew testDebugUnitTest lintDebug assembleDebug` |
| Android unsigned release build | In `Hisab/`, `./gradlew assembleRelease -PHISAB_RELEASE_API_URL=https://api.example.invalid/` for a compile-only check |
| Server tests and runtime distribution | In `hisab-server/`, `./gradlew test installDist` |
| API regression against a disposable local server | From the workspace root, `python scripts/qa/check_api.py --extended --port 8081` |
| Physical Android QA navigation | From the workspace root, `python scripts/qa/device_smoke.py --serial YOUR_DEVICE_SERIAL` after installing the QA APK |

The API suite creates, changes and deletes fixture records and writes fixture uploads. Run it only against a separate local database and upload directory. Pass the same disposable `JWT_SECRET` to the suite and server to exercise expiry and missing-claim checks.

For physical testing, use the separate QA APK and ADB reverse from [setup](SETUP.md). Exercise registration and sign-in, group creation, invitation decisions, expenses and repayments, theme and profile pictures, leaving a group, network interruption and relaunch. Compare the ledger between the app and the local API. [Initial device evidence](QA/INITIAL_DEVICE_TEST_REPORT.md)
