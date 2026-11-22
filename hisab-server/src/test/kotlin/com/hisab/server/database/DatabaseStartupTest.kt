package com.hisab.server.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseStartupTest {
    @Test
    fun slowAtlasStartupCanFinishBeforeTheBoundedDeadline() = runTest {
        var seeded = false
        initializeDatabase(databaseStartupTimeout(null),
            initialize = { delay(25_000) }, seed = { delay(5_000); seeded = true })
        assertTrue(seeded)
    }

    @Test
    fun unresponsiveDatabaseTimesOutWithoutSeeding() = runTest {
        var seeded = false
        try {
            initializeDatabase(5_000, initialize = { delay(10_000) }, seed = { seeded = true })
            error("Expected a startup timeout")
        } catch (_: TimeoutCancellationException) {
            assertFalse(seeded)
        }
    }

    @Test
    fun timeoutIncludesSeeding() = runTest {
        try {
            initializeDatabase(5_000, initialize = { delay(3_000) }, seed = { delay(3_000) })
            error("Expected a startup timeout")
        } catch (_: TimeoutCancellationException) {
            assertEquals(5_000, testScheduler.currentTime)
        }
    }

    @Test
    fun configurationAcceptsOnlyBoundedTimeouts() {
        assertEquals(120_000, databaseStartupTimeout(null))
        assertEquals(180_000, databaseStartupTimeout("180000"))
        for (invalid in listOf("false", "0", "4999", "300001")) {
            assertTrue(runCatching { databaseStartupTimeout(invalid) }.isFailure)
        }
    }
}
