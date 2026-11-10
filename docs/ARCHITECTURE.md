# Architecture

```text
Hisab/
  Hisab/                          Android Gradle project
    app/src/main/java/com/example/hisab/
      features/
        auth/
        groups/
        ledger/
        invitations/
        profile/
        admin/
      ui/                         Activity, navigation, theme and shared UI
      data/db/                    Room schema, migrations and DAOs
      data/remote/                Shared HTTP and socket transport
      di/                         Hilt bindings
      util/                       Session, theme and endpoint configuration
  hisab-server/                   Ktor Gradle project
    src/main/kotlin/com/hisab/server/
      features/
        accounts/
        groups/
        ledger/
        invitations/
        media/
        realtime/
      plugins/                    HTTP, auth, routing and serialization
      database/                   MongoDB connection
      models/                     Persisted MongoDB document contracts
  docs/
```

Both Gradle projects retain their existing names and Gradle wrappers. Android remains on Kotlin 1.9.22. The server uses Kotlin 2.1.21 for compatibility with the updated Jackson Kotlin module. The Android app remains a single APK with compile-time feature packages. Compose screens and their ViewModels live together. Feature repositories bind through Hilt, and shared Retrofit and Room infrastructure stays in the app module. Ktor installs its routes from feature packages at the original URL paths.

The Android application ID, Room filename and version, MongoDB document classes and collection mapping were preserved. Room migrations remain registered in the single Hilt provider. An unsupported schema now surfaces a migration error instead of erasing the database.

[Module responsibilities](MODULES.md) · [Data flow](DATA_FLOW.md) · [Refactoring report](REFACTORING_REPORT.md)
