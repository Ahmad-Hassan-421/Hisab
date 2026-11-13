# Android loading states

Home, Hisab detail, invitations, the local ledger, and profile show static skeletons during their first load. The placeholders follow the shape of the content and use the active theme. Existing content stays visible while a refresh runs.

Sign-in, account registration, Hisab creation, invitations, ledger forms, profile saves, picture uploads, and leaving a group show action-specific progress. Pending writes disable repeated submission. Ledger forms close after the repository confirms the write, and failed saves retain their input.

The dashboard regression suite simulates a slow read, an offline failure, a retry, and cancellation when the account changes. The ledger suite covers duplicate taps and control recovery after an uncertain save.

The Android debug build and lint succeeded. All 23 JVM tests passed. Device-level layout checks remain part of final regression testing.
