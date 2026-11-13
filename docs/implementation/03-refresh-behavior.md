# Refresh and synchronization

Pull down on the Home list or Hisab member list to reload current server data. Home coalesces the gesture with any running poll. Hisab detail reloads membership and ledger data, replaces the Room snapshot, and recalculates balances from it. Existing content and expanded rows remain visible while the request runs. A failed refresh retains usable data and shows retry feedback. Changing accounts cancels the previous read and clears the visible state.

Phase 6 passed the debug build, lint, and 26 JVM tests. A controlled ledger test exercised a delayed response, repeated refresh, an offline failure, and a changed repository snapshot that updated the balance. Physical swipe behavior and two-device synchronization remain unverified.
