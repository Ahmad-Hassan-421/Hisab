package com.hisab.server.features.notifications

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.models.UserDocument
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.UUID

class NotificationDeliveryIntegrationTest {
    private suspend fun fixture(test: suspend (String, String, String) -> Unit) {
        check(System.getenv("MONGO_URI").startsWith("mongodb://127.0.0.1:"))
        check(System.getenv("MONGO_DATABASE").startsWith("hisab_phase"))
        DB.initialize()
        val owner = UUID.randomUUID().toString(); val other = UUID.randomUUID().toString(); val group = UUID.randomUUID().toString()
        for (id in listOf(owner, other)) DB.users.insertOne(UserDocument(id, "Disposable push fixture", "$id@example.invalid", "unusable", id, ""))
        DB.groups.insertOne(HisabGroupDocument(group, "Disposable push ledger", listOf(owner, other)))
        try { test(owner, other, group) }
        finally {
            for (collection in listOf(DB.devices, DB.notificationOutbox, DB.notificationDeliveries, DB.notificationPreferences)) {
                collection.deleteMany(`in`("ownerId", listOf(owner, other)))
            }
            DB.users.deleteMany(`in`("_id", listOf(owner, other))); DB.groups.deleteOne(eq("_id", group))
        }
    }
    private suspend fun device(owner: String, token: String): String {
        val id = UUID.randomUUID().toString()
        DB.devices.insertOne(Document("_id", id).append("ownerId", owner).append("token", token).append("tokenDigest", digest(token))
            .append("packageName", "com.example.hisab.qa").append("enabled", true).append("sessionVersion", 0L)
            .append("expiresAt", Date(System.currentTimeMillis() + 86_400_000)))
        return id
    }
    private suspend fun event(owner: String, group: String): String {
        val value = notificationDocument(owner, "DEBT", UUID.randomUUID().toString(), "Debt", "Private PKR 100", group)
        DB.notificationOutbox.insertOne(value)
        return value.getString("_id")
    }
    @Test fun successfulDevicesAreNotSentAgainWhenAnotherDeviceNeedsRetry() = runBlocking {
        fixture { owner, _, group ->
            device(owner, "success-token-${UUID.randomUUID()}"); device(owner, "retry-token-${UUID.randomUUID()}")
            val id = event(owner, group)
            val calls = mutableMapOf<String, Int>()
            val transport = object : PushTransport {
                override suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult {
                    calls[token] = calls.getOrDefault(token, 0) + 1
                    assertEquals(owner, data["ownerId"]); assertFalse(data.getValue("body").contains("PKR"))
                    return if (token.startsWith("retry-") && calls[token] == 1) PushResult.RETRY else PushResult.ACCEPTED
                }
            }
            deliverNotification(id, transport)
            assertEquals("pending", DB.notificationOutbox.findOne(eq("_id", id))!!.getString("status"))
            DB.notificationOutbox.updateOne(eq("_id", id), Updates.set("dueAt", 0L))
            deliverNotification(id, transport)
            assertEquals("delivered", DB.notificationOutbox.findOne(eq("_id", id))!!.getString("status"))
            assertEquals(1, calls.entries.single { it.key.startsWith("success-") }.value)
            assertEquals(2, calls.entries.single { it.key.startsWith("retry-") }.value)
            assertEquals(2L, DB.notificationDeliveries.countDocuments(and(eq("ownerId", owner), eq("status", "delivered"))))
        }
    }
    @Test fun revokedMembershipDisabledPreferencesAndInvalidTokensSuppressDelivery() = runBlocking {
        fixture { owner, _, group ->
            val device = device(owner, "invalid-token-${UUID.randomUUID()}")
            var calls = 0
            val transport = object : PushTransport {
                override suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult { calls++; return PushResult.INVALID_TOKEN }
            }
            deliverNotification(event(owner, group), transport)
            assertFalse(DB.devices.findOne(eq("_id", device))!!.getBoolean("enabled"))
            assertEquals(1, calls)
            DB.devices.updateOne(eq("_id", device), Updates.set("enabled", true))
            DB.notificationPreferences.insertOne(Document("_id", owner).append("ownerId", owner).append("preferences", NotificationPreferences(debts = false).document()))
            deliverNotification(event(owner, group), transport); assertEquals(1, calls)
            DB.notificationPreferences.deleteOne(eq("_id", owner))
            DB.groups.updateOne(eq("_id", group), Updates.pull("members", owner))
            deliverNotification(event(owner, group), transport); assertEquals(1, calls)
        }
    }
    @Test fun deletingAnAccountDuringAnInFlightSendNeverRecreatesDeliveryRecords() = runBlocking {
        fixture { owner, _, group ->
            device(owner, "inflight-token-${UUID.randomUUID()}")
            val id = event(owner, group)
            val transport = object : PushTransport {
                override suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult {
                    DB.transaction { session ->
                        DB.users.deleteOne(session, eq("_id", owner)); DB.devices.deleteMany(session, eq("ownerId", owner))
                        DB.notificationOutbox.deleteMany(session, eq("ownerId", owner))
                    }
                    return PushResult.ACCEPTED
                }
            }
            deliverNotification(id, transport)
            assertEquals(0L, DB.notificationDeliveries.countDocuments(eq("ownerId", owner)))
            assertEquals(0L, DB.notificationOutbox.countDocuments(eq("ownerId", owner)))
        }
    }
    @Test fun releaseFanOutIsIdempotentAndTargetsOnlyDevicesRunningOlderVersions() = runBlocking {
        fixture { owner, _, _ ->
            val old = device(owner, "old-version-token-${UUID.randomUUID()}")
            val current = device(owner, "current-version-token-${UUID.randomUUID()}")
            DB.devices.updateOne(eq("_id", old), Updates.set("versionCode", 4))
            DB.devices.updateOne(eq("_id", current), Updates.set("versionCode", 5))
            val tag = "v999.9.9"
            try {
                announceVerifiedRelease("999.9.9", 5)
                advanceReleaseAudience(tag)
                announceVerifiedRelease("999.9.9", 5)
                advanceReleaseAudience(tag)
                val events = DB.notificationOutbox.find(and(eq("ownerId", owner), eq("kind", "UPDATE"))).toList()
                assertEquals(1, events.size)
                val tokens = mutableListOf<String>()
                deliverNotification(events.single().getString("_id"), object : PushTransport {
                    override suspend fun send(token: String, packageName: String, data: Map<String, String>): PushResult {
                        tokens.add(token); return PushResult.ACCEPTED
                    }
                })
                assertEquals(1, tokens.size)
                assertTrue(tokens.single().startsWith("old-version-token-"))
            } finally { DB.releaseNotificationJobs.deleteOne(eq("_id", tag)) }
        }
    }
}
