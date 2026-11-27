package com.hisab.server.features.notifications

import com.hisab.server.database.MongoClientProvider as DB
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.bson.Document
import java.util.Date
import java.util.UUID

internal fun notificationBackoff(attempts: Int): Long = (60_000L shl attempts.coerceIn(0, 9)).coerceAtMost(6 * 3_600_000L)

internal suspend fun deliverNotification(id: String, transport: PushTransport) {
    val now = System.currentTimeMillis()
    val lease = UUID.randomUUID().toString()
    val event = DB.notificationOutbox.findOneAndUpdate(and(eq("_id", id), eq("status", "pending"),
        lte("dueAt", now), lte("leaseUntil", now), gt("expiresAt", Date(now))),
        Updates.combine(Updates.set("leaseUntil", now + 120_000), Updates.set("lease", lease)),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)) ?: return
    val owner = event.getString("ownerId")
    val kind = event.getString("kind")
    val group = event.getString("groupId").orEmpty()
    val account = DB.accountDirectory.findOne(eq("_id", owner))
    val allowed = account != null && account.getString("accountStatus") in listOf(null, "active") &&
        (kind !in setOf("DEBT", "PAYMENT") || DB.groups.countDocuments(and(eq("_id", group), eq("members", owner))) > 0)
    val prefs = preferences(owner)
    var retry = false
    var sent = false
    if (allowed && prefs.accepts(kind)) {
        DB.devices.find(and(eq("ownerId", owner), eq("enabled", true), gt("expiresAt", Date())))
            .limit(20).toFlow().collect { device ->
                val deviceId = device.getString("_id")
                val deliveryId = "$id:$deviceId"
                val prior = DB.notificationDeliveries.findOne(eq("_id", deliveryId))
                if (prior?.getString("status") == "delivered") { sent = true; return@collect }
                if (prior?.getString("status") in setOf("rejected", "invalid")) return@collect
                // A lease spans the bounded fan-out; a restart can retry, and clients deduplicate event IDs.
                val renewed = DB.notificationOutbox.updateOne(and(eq("_id", id), eq("lease", lease)), Updates.set("leaseUntil", System.currentTimeMillis() + 120_000))
                if (renewed.matchedCount != 1L) return@collect
                val current = DB.devices.findOne(and(eq("_id", deviceId), eq("ownerId", owner), eq("enabled", true),
                    eq("tokenDigest", device.getString("tokenDigest")))) ?: return@collect
                val currentAccount = DB.accountDirectory.findOne(eq("_id", owner)) ?: return@collect
                if (currentAccount.getString("accountStatus") !in listOf(null, "active") ||
                    ((currentAccount["sessionVersion"] as? Number)?.toLong() ?: 0) != ((current["sessionVersion"] as? Number)?.toLong() ?: 0)) return@collect
                if (kind == "UPDATE" && ((current["versionCode"] as? Number)?.toInt() ?: Int.MAX_VALUE) >= ((event["targetVersionCode"] as? Number)?.toInt() ?: 0)) return@collect
                if (kind in setOf("DEBT", "PAYMENT") && DB.groups.countDocuments(and(eq("_id", group), eq("members", owner))) == 0L) return@collect
                val result = try { transport.send(current.getString("token"), current.getString("packageName"), notificationData(event, prefs)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        org.slf4j.LoggerFactory.getLogger("NotificationWorker").warn("Push transport will retry ({})", error.javaClass.simpleName)
                        PushResult.RETRY
                    }
                val status = when (result) {
                    PushResult.ACCEPTED -> { sent = true; "delivered" }
                    PushResult.INVALID_TOKEN -> {
                        DB.devices.updateOne(and(eq("_id", deviceId), eq("tokenDigest", current.getString("tokenDigest"))), Updates.set("enabled", false))
                        "invalid"
                    }
                    PushResult.RETRY -> { retry = true; "pending" }
                    PushResult.REJECTED -> "rejected"
                }
                // Never recreate deleted-account data if deletion commits while an FCM request is in flight.
                DB.transaction { session ->
                    if (DB.users.findOne(session, eq("_id", owner)) != null && DB.notificationOutbox.findOne(session, eq("_id", id)) != null) {
                        com.hisab.server.features.privacy.lockAccount(session, owner)
                        DB.notificationDeliveries.updateOne(session, eq("_id", deliveryId), Document("\$set", Document("ownerId", owner)
                            .append("eventId", id).append("deviceId", deviceId).append("status", status)
                            .append("updatedAt", System.currentTimeMillis()).append("expiresAt", event.getDate("expiresAt"))), UpdateOptions().upsert(true))
                    }
                }
            }
    }
    val attempts = (event["attempts"] as Number).toInt() + 1
    DB.notificationOutbox.updateOne(and(eq("_id", id), eq("lease", lease)), Updates.combine(
        Updates.set("status", if (retry && attempts < 12) "pending" else if (retry) "failed" else if (sent) "delivered" else "skipped"),
        Updates.set("attempts", attempts), Updates.set("leaseUntil", 0L), Updates.set("dueAt", System.currentTimeMillis() + notificationBackoff(attempts - 1))))
}

fun Application.startNotificationWorker() {
    val transport = PushGateway.transport
    if (transport == null) { log.warn("Push delivery disabled: Firebase service-account configuration is missing or invalid"); return }
    val worker = launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val events = DB.notificationOutbox.find(and(eq("status", "pending"), lte("dueAt", now), lte("leaseUntil", now), gt("expiresAt", Date(now))))
                    .sort(Sorts.ascending("dueAt")).limit(20).toList()
                for (event in events) deliverNotification(event.getString("_id"), transport)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { log.warn("Push delivery will retry ({})", error.javaClass.simpleName) }
            delay(5_000)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) { worker.cancel() }
}
