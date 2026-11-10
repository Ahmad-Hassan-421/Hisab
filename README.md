# Hisab

Hisab is an Android debt and shared expense tracker. Create a group, invite members by their Hisab ID, split an expense or assign it to one member, record repayments, and review balances and history in PKR. The app also provides profile pictures, a theme preference and in-app invitation notifications.

| Project | Stack |
|---|---|
| `Hisab/` | Kotlin, Jetpack Compose, Hilt, Room, Retrofit and OkHttp. Minimum Android API 24. Targets API 34. |
| `hisab-server/` | Kotlin, Ktor/Netty, MongoDB via KMongo, BCrypt and JWT. |

Both projects have a Gradle 8.5 wrapper and use JDK 17 for builds. Install the Android SDK with platform 34, build tools 34.0.0 and platform tools. Run a MongoDB replica set locally or use a dedicated staging cluster. See [setup](docs/SETUP.md) for database, environment and ADB commands.

## Build and test

From `hisab-server/`, load the settings in [.env.example](.env.example) into the shell, then start the local API with `./gradlew run` or `./gradlew.bat run` on Windows. From `Hisab/`, use `./gradlew assembleDebug` or `./gradlew.bat assembleDebug`. For a physical phone, set up ADB reverse and install the separate QA variant described in [testing](docs/TESTING.md).

For Android verification run `./gradlew testDebugUnitTest lintDebug assembleDebug`. For backend verification run `./gradlew test installDist` in `hisab-server/` and use the API suite in a disposable environment. Release APK builds require an explicit HTTPS `HISAB_RELEASE_API_URL`. Credentials and Android signing material belong in the shell, CI secrets or Android Studio signing setup.

## Project guides

- [Feature inventory](docs/FEATURE_INVENTORY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development and troubleshooting](docs/DEVELOPMENT.md)
- [Testing](docs/TESTING.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Security](SECURITY.md)
- [Readiness report](docs/FINAL_PROJECT_READINESS_REPORT.md)
