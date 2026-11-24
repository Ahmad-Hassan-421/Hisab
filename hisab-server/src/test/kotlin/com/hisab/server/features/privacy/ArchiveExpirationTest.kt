package com.hisab.server.features.privacy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArchiveExpirationTest {
    @Test fun expirationUsesTwoCalendarMonthsInUtc() {
        for ((deleted, expired) in listOf(
            "2026-12-31T23:59:59Z" to "2027-02-28T23:59:59Z",
            "2027-12-31T23:59:59Z" to "2028-02-29T23:59:59Z",
            "2026-01-31T12:34:56Z" to "2026-03-31T12:34:56Z")) {
            assertEquals(Instant.parse(expired), archiveExpiration(Instant.parse(deleted).toEpochMilli()).toInstant())
        }
    }
}
