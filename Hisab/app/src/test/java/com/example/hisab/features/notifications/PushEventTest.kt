package com.example.hisab.features.notifications

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PushEventTest {
    private val owner = UUID.randomUUID().toString()
    private val now = 1_000_000L
    private fun event() = mapOf("eventId" to UUID.randomUUID().toString(), "ownerId" to owner, "kind" to "PAYMENT",
        "groupId" to UUID.randomUUID().toString(), "recordId" to UUID.randomUUID().toString(), "sourceId" to UUID.randomUUID().toString(),
        "title" to "Payment recorded", "body" to "Payment details", "expiresAt" to (now + 1000).toString())
    @Test fun validatesOwnerExpiryKindAndNavigationIdentifiersBeforeDisplayOrNavigation() {
        val value = event()
        assertNotNull(PushEvent.parse(value, owner, now))
        assertNull(PushEvent.parse(value, null, now))
        assertNull(PushEvent.parse(value, "other-account", now))
        assertNull(PushEvent.parse(value, owner, now + 1001))
        assertNull(PushEvent.parse(value + ("kind" to "DELETE_ACCOUNT"), owner, now))
        assertNull(PushEvent.parse(value + ("groupId" to "../../admin"), owner, now))
        assertNull(PushEvent.parse(value + ("recordId" to "arbitrary-route"), owner, now))
        assertNull(PushEvent.parse(value + ("expiresAt" to Long.MAX_VALUE.toString()), owner, now))
    }
    @Test fun categoriesAndFinancialPreviewsAreIndependentAndPrivateByDefault() {
        val preferences = PushPreferences(debts = false, payments = true)
        assertFalse(preferences.previews)
        assertFalse(preferences.accepts("DEBT"))
        assertTrue(preferences.accepts("PAYMENT"))
        assertFalse(preferences.accepts("unknown"))
    }
}
