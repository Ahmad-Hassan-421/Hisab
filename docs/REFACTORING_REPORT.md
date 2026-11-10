# Refactoring report

The Android code originally separated screens, ViewModels, API DTOs, repository contracts and implementations by layer. Ktor kept every route in one package. Both projects now put these files with the features they serve and retain shared infrastructure for work used by multiple features.

- Android screens and ViewModels moved together. DTOs and repositories moved with groups, ledger, invitations, profile and authentication.
- The invitation model moved out of presentation. Its API mapping is shared by list, send, accept and decline.
- Profile upload now goes through the repository contract. The repository handles temporary file cleanup and performs file I/O off the main dispatcher.
- Authentication uses the shared Hilt Room provider. The extra database builder and destructive fallback were removed.
- Server routes now live with their account, group, ledger, invitation, media or socket helpers. Ktor still registers the same HTTP methods and paths.
- Profile theme updates accept existing uppercase values from older Android builds and store the canonical lowercase value.

The Android debug APK assembled after the move. Lint reported zero errors and 95 warnings, and the Android unit task had no test sources at this stage. The Ktor `test installDist` build succeeded, and all 52 API assertions passed after the reorganized distribution started against disposable MongoDB. A source search found zero references to the former route, ViewModel, repository and DTO package paths. The final device pass verifies the reorganized APK with a configurable loopback endpoint.
