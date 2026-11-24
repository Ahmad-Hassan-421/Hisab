package com.hisab.server.features.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorPagingTest {
    private val codec = CursorCodec("disposable-paging-test-secret")

    @Test
    fun authenticatedCursorsRoundTripAndAreBoundToTheAccountAndFilter() {
        val position = PagePosition(100L, "same-timestamp-tie", 200L, 9L)
        val encoded = codec.encode("entries:alice:group:payments", position)
        assertEquals(position, codec.decode("entries:alice:group:payments", encoded))
        assertNull(codec.decode("entries:bob:group:payments", encoded))
        assertNull(codec.decode("entries:alice:group:debts", encoded))
        val altered = encoded.dropLast(2) + "xx"
        assertNull(codec.decode("entries:alice:group:payments", altered))
    }

    @Test
    fun missingLegacyDatesRemainPageableAndMalformedCursorsAreRejected() {
        val position = PagePosition(null, "legacy", 200L, null)
        assertEquals(position, codec.decode("groups:alice", codec.encode("groups:alice", position)))
        for (value in listOf("", "not-base64", "a.b.c", "x".repeat(769))) {
            assertNull(codec.decode("groups:alice", value))
        }
        val invalid = PagePosition(201L, "future", 200L, -1L)
        assertNull(codec.decode("groups:alice", codec.encode("groups:alice", invalid)))
    }
}
