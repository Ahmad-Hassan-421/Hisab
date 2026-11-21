# Profile picture persistence

The old flow uploaded a temporary crop, deleted the local file, and kept only the server URL. The app could display its cached copy temporarily. After a restart, the avatar depended on the network and on the server still serving the image.

Hisab now saves a bounded 1:1 JPEG crop in Android's private no-backup directory, keyed to the signed-in account. The existing cropper handles orientation and resizing, and the app validates the result and writes it atomically off the UI thread. The profile loads the local copy before refreshing the account. An explicit local reset restores the default avatar, and existing accounts with a legacy remote avatar can still display that avatar until they save or reset their local photo.

Selecting a picture does not upload it. The invitation implementation in Phase 9 should ask the sender for permission to attach a photo to a specific invitation, send only its bounded crop, and store it separately from user documents and public uploads. The server should allow access only to the sender and recipient after validating the authenticated account against the invitation. The recipient falls back to Hisab's default avatar when there is no accessible attachment.

Android unit tests passed 34 of 34 and lint passed with zero errors. The C51 instrumentation verified reload from private storage, isolation between two account keys, low and maximum supported image dimensions, rejection of a non-square replacement without losing the previous photo, replacement, corruption handling, reset, and successful image decoding in a new process after force-stopping the QA app.

The C51 accepted a same-signature in-place update of Hisab QA from build 3 to build 4 and the updated app decoded the photo written by build 3. This meets the restart and update gate. The real gallery crop and orientation, navigation, backgrounding, device reboot and sign-out/sign-in remain interactive cases for Phase 13. Uninstalling or clearing storage removes this local photo.
