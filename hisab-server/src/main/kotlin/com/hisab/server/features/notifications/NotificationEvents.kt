package com.hisab.server.features.notifications

import com.hisab.server.database.MongoClientProvider as DB
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.reactivestreams.client.ClientSession
import org.bson.Document
import java.util.Date
import java.util.UUID

internal suspend fun queueNotification(session: ClientSession, owner: String, kind: String, sourceId: String,
    title: String, body: String, groupId: String = "", actor: String = "", recordId: String = "") {
    val account = DB.users.findOne(session, eq("_id", owner)) ?: return
    if (account.accountStatus != null && account.accountStatus != "active") return
    val event = notificationDocument(owner, kind, sourceId, title, body, groupId, actor, recordId)
    DB.notificationOutbox.updateOne(session, eq("_id", event.getString("_id")), Document("\$setOnInsert", event), UpdateOptions().upsert(true))
}

internal fun notificationDocument(owner: String, kind: String, sourceId: String, title: String, body: String,
    groupId: String = "", actor: String = "", recordId: String = ""): Document {
    val now = System.currentTimeMillis()
    val id = UUID.nameUUIDFromBytes("$kind:$owner:$sourceId".toByteArray(Charsets.UTF_8)).toString()
    return Document("_id", id).append("ownerId", owner).append("kind", kind).append("title", title.take(120)).append("body", body.take(1500))
        .append("groupId", groupId).append("actorId", actor).append("recordId", recordId).append("sourceId", sourceId)
        .append("createdAt", now).append("dueAt", now).append("status", "pending").append("leaseUntil", 0L).append("attempts", 0)
        .append("expiresAt", Date(now + 7L * 86_400_000))
}

internal fun NotificationPreferences.accepts(kind: String): Boolean = when (kind) {
    "DEBT" -> debts; "PAYMENT" -> payments; "INVITATION" -> invitations; "RECOVERY" -> recovery; "UPDATE" -> updates
    else -> false
}

internal fun notificationData(event: Document, preferences: NotificationPreferences): Map<String, String> {
    val generic = if (event.getString("kind") == "UPDATE") "A new Hisab version is available. Open the app to review it."
        else "You have an update in Hisab. Open the app to view it."
    return mapOf("eventId" to event.getString("_id"), "ownerId" to event.getString("ownerId"), "kind" to event.getString("kind"),
        "title" to if (preferences.previews) event.getString("title") else "Hisab update",
        "body" to if (preferences.previews) event.getString("body") else generic,
        "groupId" to event.getString("groupId").orEmpty(), "recordId" to event.getString("recordId").orEmpty(),
        "sourceId" to event.getString("sourceId"), "createdAt" to (event["createdAt"] as Number).toLong().toString(),
        "expiresAt" to event.getDate("expiresAt").time.toString())
}
