package com.hisab.server.features.media

import com.hisab.server.database.MongoClientProvider as DB
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.ClientSession
import org.bson.Document
import kotlinx.coroutines.flow.collect
import java.util.Date

internal suspend fun setMediaReference(session: ClientSession, kind: String, subject: String, path: String) {
    val key = "$kind:$subject"
    val asset = mediaId(path)
    if (asset == null) DB.mediaReferences.deleteOne(session, eq("_id", key))
    else DB.mediaReferences.replaceOne(session, eq("_id", key), Document("_id", key)
        .append("kind", kind).append("subjectId", subject).append("assetId", asset), ReplaceOptions().upsert(true))
}

internal suspend fun snapshotMediaReference(session: ClientSession, snapshot: String, owner: String, path: String, expires: Date?) {
    val asset = mediaId(path) ?: return
    val key = "${if (expires == null) "recovered" else "archive"}:$snapshot:$asset"
    val ref = Document("_id", key).append("kind", if (expires == null) "recovered" else "archive")
        .append("subjectId", snapshot).append("ownerId", owner).append("assetId", asset)
    if (expires != null) ref.append("expiresAt", expires)
    DB.mediaReferences.replaceOne(session, eq("_id", key), ref, ReplaceOptions().upsert(true))
}

/** Authorize known immutable versions in bounded batches, including private recovered snapshots. */
internal suspend fun allowedMedia(actor: String, ids: List<String>): Set<String> {
    if (ids.isEmpty()) return emptySet()
    val refs = DB.mediaReferences.find(and(`in`("assetId", ids), `in`("kind", listOf("profile", "group"))))
        .limit(ids.size).maxTime(3, java.util.concurrent.TimeUnit.SECONDS).toList()
    val privateVersions = DB.mediaReferences.aggregate<Document>(listOf(
        Document("\$match", and(`in`("assetId", ids), eq("ownerId", actor),
            or(eq("kind", "recovered"), and(eq("kind", "archive"), gt("expiresAt", Date()))))),
        Document("\$group", Document("_id", "\$assetId"))))
        .maxTime(3, java.util.concurrent.TimeUnit.SECONDS).toList().map { it.getString("_id") }.toSet()
    val groups = refs.filter { it.getString("kind") == "group" }.map { it.getString("subjectId") }
    val accessibleGroups = if (groups.isEmpty()) emptySet() else DB.groupDirectory.find(and(`in`("_id", groups), eq("members", actor)))
        .projection(Document("_id", 1)).limit(ids.size).toList().map { it.getString("_id") }.toSet()
    val profiles = refs.filter { it.getString("kind") == "profile" }.map { it.getString("subjectId") }.toSet()
    val visibleProfiles = mutableSetOf(actor)
    if (profiles.isNotEmpty()) {
        // Project only requested identities, not every member of every shared group.
        DB.groupDirectory.aggregate<Document>(listOf(Document("\$match", and(eq("members", actor), `in`("members", profiles))),
            Document("\$project", Document("members", Document("\$setIntersection", listOf("\$members", profiles.toList()))))))
            .maxTime(3, java.util.concurrent.TimeUnit.SECONDS).toFlow().collect { row ->
                visibleProfiles.addAll(row.getList("members", String::class.java).orEmpty())
            }
        DB.requestDirectory.find(and(eq("receiverId", actor), eq("status", "pending"), `in`("senderId", profiles)))
            .projection(Document("senderId", 1)).maxTime(3, java.util.concurrent.TimeUnit.SECONDS)
            .toFlow().collect { visibleProfiles.add(it.getString("senderId")) }
    }
    return refs.filter { when (it.getString("kind")) {
        "profile" -> it.getString("subjectId") in visibleProfiles
        "group" -> it.getString("subjectId") in accessibleGroups
        else -> false
    } }.map { it.getString("assetId") }.toSet() + privateVersions
}
