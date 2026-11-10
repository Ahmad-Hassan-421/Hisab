# Final test evidence

## Environment

Physical C51 with Android 14/API 34, connected over authorized USB ADB. The final debug APK used `com.example.hisab.qa` and ADB reverse to a loopback API. The original `com.example.hisab` installation and its data were outside the test runs. The server ran on Windows with JDK 17 and a disposable MongoDB Community 8.0 replica set. The final run used the same disposable database as the baseline to verify access to earlier records.

## Commands and results

| Check | Command | Result |
|---|---|---|
| Android clean build, unit tests and lint | In `Hisab/`, `gradlew.bat clean assembleDebug testDebugUnitTest assembleRelease lintDebug -PHISAB_QA=true -PHISAB_DEBUG_API_URL=http://127.0.0.1:18089/ -PHISAB_RELEASE_API_URL=https://hisab.example.invalid/` | PASS. Debug and unsigned release APKs built. Five JVM tests passed. Debug lint reported zero errors and 95 warnings. Release lint vital passed. |
| Release config | `gradlew.bat :app:requireReleaseOrigin` with valid, missing and HTTP origins. Inspect the release manifest with `aapt dump xmltree`. | PASS. HTTPS accepted. Missing and HTTP origins rejected. The APK uses the original package and has cleartext disabled. |
| Backend clean build | In `hisab-server/`, `gradlew.bat clean test installDist` | PASS. Gradle's backend unit test task reported `NO-SOURCE`. |
| API integration | `python scripts/qa/check_api.py --extended --port 18089` | 73 passed, zero failed. Checked identity, authorization, expiry, concurrent registration and replays, socket delivery, invalid input, batch writes, profile media, leave and delete. |
| Physical Android smoke | `python scripts/qa/device_smoke.py --serial YOUR_DEVICE_SERIAL` with the final QA APK installed | PASS for login, empty-form validation and signup navigation. |
| Production runtime | Start `installDist` with production environment values, exercise health and upload retrieval, start a second JVM using the same upload directory, and run a separate startup with an unavailable MongoDB endpoint. | PASS. HTTP readiness, retained upload bytes, listener shutdown and failed startup with unavailable MongoDB. |
| Repository scan | `python scripts/check_secrets.py` | No matches in the audited repository candidates. A separate check of the final QA server log found no fixture password or JWT header prefix. |

The five ledger unit tests cover exact decimal settlement, split remainder allocation, legacy fractional values, range and precision, and distinct counterparty balances.

## Physical C51 workflow

- Updating the QA package in place preserved its session storage. The earlier signing key caused a return to login. The original disposable account signed in against the final server and displayed the earlier group.
- Registered another test account through Android, opened notifications, created a group, opened its ledger and invitation dialogs, and exercised profile navigation and logout confirmation.
- Warm resume and cold relaunch passed. The group appeared after cold relaunch, which had failed at the baseline checkpoint.
- Sent an invitation and accepted it on the C51 using a second disposable account. The updated membership appeared in MongoDB and Android.
- Recorded a split of PKR 12.345 and a specific expense of PKR 0.655. The Android balance displayed PKR 13.000. The test used a visually confirmed tap for the member dropdown because the phone's accessibility dump omitted the popup.
- The phone switched to another foreground app during repayment. The QA crash buffer contained no app crash for the attempt. Repayment and settled-record clearing passed through the API. Their Android actions, theme sync, leaving via Android, Android network recovery, picker/crop, admin panel, orientation and permission-denial coverage remain unverified.

The final phone log contains 29 passing checks. It includes the repeated navigation checks and the targeted continuation for the specific expense.

## Measurements and scope

ADB reported 4,026 ms for one final QA launch after installation and 2,798 ms for one cold relaunch. A sample of 20 local health requests had a median of 15.1 ms and a maximum of 41.3 ms. The Windows JVM working set was 214.1 MiB at the sample. These are local observations without a controlled before-and-after benchmark.

Render Docker execution, hosted traffic and GitHub's hosted workflow remain to be verified. The release APK uses an HTTPS placeholder for packaging verification and has no signing identity.
