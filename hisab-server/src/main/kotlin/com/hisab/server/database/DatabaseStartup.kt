package com.hisab.server.database

import kotlinx.coroutines.withTimeout

internal fun databaseStartupTimeout(value: String?): Long {
    if (value.isNullOrBlank()) return 120_000
    return requireNotNull(value.toLongOrNull()?.takeIf { it in 5_000..300_000 }) {
        "MONGO_STARTUP_TIMEOUT_MS must be between 5000 and 300000"
    }
}

internal suspend fun initializeDatabase(
    timeoutMillis: Long,
    initialize: suspend () -> Unit,
    seed: suspend () -> Unit
) {
    withTimeout(timeoutMillis) {
        initialize()
        seed()
    }
}
