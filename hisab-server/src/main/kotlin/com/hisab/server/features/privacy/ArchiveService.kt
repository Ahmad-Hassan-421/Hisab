package com.hisab.server.features.privacy

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.groups.lockGroupForMember
import com.hisab.server.features.media.snapshotMediaReference
import com.hisab.server.features.media.setMediaReference
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.RequestFailure
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.reactivestreams.client.ClientSession
import io.ktor.http.HttpStatusCode
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

internal fun archiveExpiration(now: Long): Date = Date.from(
    Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).plusMonths(2).toInstant())

internal suspend fun lockAccount(session: ClientSession, actor: String): UserDocument {
    return DB.users.findOneAndUpdate(session, and(eq("_id", actor), or(eq("accountStatus", null), eq("accountStatus", "active"))),
        Updates.inc("privacyMutation", 1L)) ?: throw RequestFailure(HttpStatusCode.Unauthorized, "Sign-in required")
}

// Small normalized documents avoid MongoDB's 16 MB document limit for large histories.
internal suspend fun copyInPages(session: ClientSession, source: CoroutineCollection<Document>, filter: org.bson.conversions.Bson,
    target: CoroutineCollection<Document>, transform: (Document) -> Document): Long {
    var last: String? = null
    var count = 0L
    while (true) {
        val rows = source.find(session, and(filter, last?.let { gt("_id", it) } ?: Document()))
            .sort(Sorts.ascending("_id")).limit(256).toList()
        if (rows.isEmpty()) break
        target.insertMany(session, rows.map(transform))
        count += rows.size
        last = rows.last().getString("_id")
    }
    return count
}

internal suspend fun archiveGroup(actor: String, groupId: String) {
    DB.transaction { session ->
        val owner = lockAccount(session, actor)
        val existing = DB.groups.findOne(session, eq("_id", groupId))
        // Repeating a successful deletion is harmless. A live outsider still receives 403.
        if (existing == null || actor !in existing.members) {
            if (DB.archives.findOne(session, and(eq("ownerId", actor), eq("originalGroupId", groupId))) != null) return@transaction
            throw RequestFailure(if (existing == null) HttpStatusCode.NotFound else HttpStatusCode.Forbidden, "Hisab membership required")
        }
        val group = lockGroupForMember(session, groupId, actor)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expires = archiveExpiration(now)
        val base = { kind: String, original: Document ->
            Document(original).append("_id", UUID.randomUUID().toString())
                .append("originalId", original.getString("_id")).append("snapshotId", id)
                .append("ownerId", actor).append("kind", kind).append("expiresAt", expires)
        }
        val entries = copyInPages(session, DB.entryDirectory, eq("groupId", groupId), DB.archiveRecords) { base("entry", it) }
        // Include departed identities as well as active members; only public historical fields are copied.
        val memberIds = (group.members + group.exMembers).distinct()
        var memberCount = 0L
        memberIds.chunked(256).forEach { ids ->
            val profiles = DB.accountDirectory.find(session, `in`("_id", ids))
                .projection(Document("_id", 1).append("name", 1).append("profilePicUrl", 1)).limit(256).toList()
            val byId = profiles.associateBy { it.getString("_id") }
            val rows = ids.map { member -> base("member", byId[member] ?: Document("_id", member).append("name", "Former member")) }
            profiles.forEach { snapshotMediaReference(session, id, actor, it.getString("profilePicUrl").orEmpty(), expires) }
            if (rows.isNotEmpty()) DB.archiveRecords.insertMany(session, rows)
            memberCount += rows.size
        }
        DB.archives.insertOne(session, Document("_id", id).append("ownerId", actor).append("ownerEmail", owner.email)
            .append("originalGroupId", groupId).append("name", group.name).append("createdAt", now)
            .append("deletedAt", now).append("expiresAt", expires).append("recordCount", entries)
            .append("memberCount", memberCount).append("status", "available").append("pictureUrl", group.pictureUrl))
        snapshotMediaReference(session, id, actor, group.pictureUrl, expires)
        if (group.members.size == 1) {
            DB.entries.deleteMany(session, eq("groupId", groupId))
            DB.requests.deleteMany(session, eq("groupId", groupId))
            DB.groups.deleteOne(session, eq("_id", groupId))
            setMediaReference(session, "group", groupId, "")
        } else {
            DB.groups.updateOne(session, eq("_id", groupId), Updates.combine(
                Updates.pull("members", actor), Updates.addToSet("exMembers", actor)))
            DB.requests.deleteMany(session, and(eq("groupId", groupId), or(eq("senderId", actor), eq("receiverId", actor))))
        }
    }
}

internal suspend fun requestRecovery(actor: String, id: String): Document = DB.transaction { session ->
    lockAccount(session, actor)
    val source = DB.archives.findOneAndUpdate(session,
        and(eq("_id", id), eq("ownerId", actor), gt("expiresAt", Date())), Updates.inc("mutation", 1L))
        ?: throw RequestFailure(HttpStatusCode.NotFound, "This archive is unavailable or has expired")
    DB.recoveryJobs.findOne(session, eq("_id", id))?.let {
        if (it.getString("status") != "failed") return@transaction it
        DB.recoveryJobs.deleteOne(session, eq("_id", id))
    }
    val job = Document("_id", id).append("ownerId", actor).append("name", source.getString("name"))
        .append("status", "pending").append("createdAt", System.currentTimeMillis())
        .append("dueAt", System.currentTimeMillis() + 180_000L).append("attempts", 0).append("expiresAt", source.getDate("expiresAt"))
    DB.recoveryJobs.insertOne(session, job)
    DB.archives.updateOne(session, eq("_id", id), Updates.set("status", "pending"))
    job
}

// The persisted due time, transaction and deterministic copy IDs survive server/app restarts.
internal suspend fun deliverRecovery(id: String) = DB.transaction { session ->
    val now = System.currentTimeMillis()
    val job = DB.recoveryJobs.findOneAndUpdate(session,
        and(eq("_id", id), eq("status", "pending"), lte("dueAt", now)), Updates.inc("attempts", 1)) ?: return@transaction
    val actor = job.getString("ownerId")
    lockAccount(session, actor)
    val source = DB.archives.findOneAndUpdate(session,
        and(eq("_id", id), eq("ownerId", actor), gt("expiresAt", Date(now))), Updates.inc("mutation", 1L))
    if (source == null) {
        DB.recoveryJobs.updateOne(session, eq("_id", id), Updates.combine(Updates.set("status", "expired"), Updates.set("completedAt", now)))
        return@transaction
    }
    val copied = copyInPages(session, DB.archiveRecords, eq("snapshotId", id), DB.recoveredRecords) {
        Document(it).apply { remove("expiresAt") }
    }
    check(copied == (source["recordCount"] as Number).toLong() + (source["memberCount"] as Number).toLong()) {
        "The recovery source is incomplete"
    }
    if (source.getDate("expiresAt").time <= System.currentTimeMillis()) {
        throw RequestFailure(HttpStatusCode.Conflict, "The recovery source expired during processing")
    }
    DB.recovered.insertOne(session, Document(source).apply {
        remove("expiresAt"); remove("ownerEmail"); put("status", "ready"); put("recoveredAt", now); put("createdAt", now)
    })
    copyInPages(session, DB.mediaReferences, and(eq("kind", "archive"), eq("subjectId", id)), DB.mediaReferences) {
        Document(it).apply {
            put("_id", "recovered:$id:${it.getString("assetId")}"); put("kind", "recovered"); remove("expiresAt")
        }
    }
    DB.recoveryJobs.updateOne(session, eq("_id", id), Updates.combine(Updates.set("status", "ready"), Updates.set("completedAt", now)))
    DB.archives.updateOne(session, eq("_id", id), Updates.combine(Updates.set("status", "ready"), Updates.set("recoveredAt", now)))
    com.hisab.server.features.notifications.queueNotification(session, actor, "RECOVERY", id, "Your Hisab is recovered",
        "${source.getString("name")} is ready as a private read-only copy.")
}

internal suspend fun deleteRecovered(actor: String, id: String) = DB.transaction { session ->
    lockAccount(session, actor)
    val own = and(eq("_id", id), eq("ownerId", actor))
    if (DB.recovered.findOne(session, own) == null) return@transaction
    DB.recovered.deleteOne(session, own)
    DB.archives.deleteOne(session, own)
    DB.recoveryJobs.deleteOne(session, own)
    val rows = and(eq("snapshotId", id), eq("ownerId", actor))
    DB.recoveredRecords.deleteMany(session, rows)
    DB.archiveRecords.deleteMany(session, rows)
    DB.mediaReferences.deleteMany(session, and(eq("subjectId", id), eq("ownerId", actor)))
}
