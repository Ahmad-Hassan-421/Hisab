# Contributing

Use JDK 17 and the project Gradle wrappers. Put new code with the feature it serves and use shared HTTP and persistence infrastructure for common work.

Run the checks in [Testing](docs/TESTING.md) before opening a pull request. For data or API changes, include the old-client and old-data cases you verified. Exercise write or delete flows with disposable records. Keep APKs, credentials, signing files, device logs and local databases outside the commit.
