package com.hisab.server.features.privacy

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.accounts.PasswordUtil
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.RequestFailure
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.http.HttpStatusCode
import org.bson.Document
import java.util.UUID

internal suspend fun permanentlyDeleteAccount(actor: String, password: String, confirmedId: String) {
    if (confirmedId != actor || !PasswordUtil.acceptsInput(password)) {
        throw RequestFailure(HttpStatusCode.BadRequest, "Select your account and confirm with its password")
    }
    val user = DB.users.findOne(eq("_id", actor)) ?: throw RequestFailure(HttpStatusCode.Unauthorized, "Sign-in required")
    if (!PasswordUtil.verify(password, user.passwordHash)) throw RequestFailure(HttpStatusCode.BadRequest, "The password is incorrect")
    DB.transaction { session ->
        val locked = lockAccount(session, actor)
        if (locked.passwordHash != user.passwordHash || locked.sessionVersion != user.sessionVersion) {
            throw RequestFailure(HttpStatusCode.Conflict, "Your account changed. Sign in again before deleting it")
        }
        val anonymous = "deleted-${UUID.randomUUID()}"
        var last: String? = null
        while (true) {
            val groups = DB.groupDirectory.find(session, and(or(eq("members", actor), eq("exMembers", actor)),
                last?.let { gt("_id", it) } ?: Document())).sort(Sorts.ascending("_id")).limit(100).toList()
            if (groups.isEmpty()) break
            for (group in groups) {
                val id = group.getString("_id")
                val others = group.getList("members", String::class.java).orEmpty().filterNot { it == actor }
                if (others.isEmpty()) {
                    DB.entries.deleteMany(session, eq("groupId", id))
                    DB.requests.deleteMany(session, eq("groupId", id))
                    DB.groups.deleteOne(session, eq("_id", id))
                    DB.mediaReferences.deleteOne(session, eq("_id", "group:$id"))
                } else {
                    val former = (group.getList("exMembers", String::class.java).orEmpty().filterNot { it == actor } + anonymous).distinct()
                    DB.groups.updateOne(session, eq("_id", id), Updates.combine(Updates.set("members", others),
                        Updates.set("exMembers", former), Updates.inc("ledgerRevision", 1L)))
                    DB.entries.updateMany(session, and(eq("groupId", id), eq("debtorId", actor)), Updates.set("debtorId", anonymous))
                    DB.entries.updateMany(session, and(eq("groupId", id), eq("creditorId", actor)), Updates.set("creditorId", anonymous))
                }
            }
            last = groups.last().getString("_id")
        }
        // Remove the account's private copies and anonymize its identity in other owners' historical copies.
        for (collection in listOf(DB.archives, DB.archiveRecords, DB.recovered, DB.recoveredRecords, DB.recoveryJobs,
            DB.privacyRequests, DB.devices, DB.notificationOutbox)) collection.deleteMany(session, eq("ownerId", actor))
        for (collection in listOf(DB.archiveRecords, DB.recoveredRecords)) {
            collection.updateMany(session, and(eq("kind", "member"), eq("originalId", actor)), Updates.combine(
                Updates.set("originalId", anonymous), Updates.set("name", "Deleted account"), Updates.unset("profilePicUrl")))
            collection.updateMany(session, eq("debtorId", actor), Updates.set("debtorId", anonymous))
            collection.updateMany(session, eq("creditorId", actor), Updates.set("creditorId", anonymous))
        }
        DB.requests.deleteMany(session, or(eq("senderId", actor), eq("receiverId", actor)))
        DB.adminAudit.deleteMany(session, or(eq("actorId", actor), eq("targetId", actor)))
        var fileCursor: String? = null
        while (true) {
            val files = DB.personalFiles.find(session, and(eq("ownerId", actor), fileCursor?.let { gt("_id", it) } ?: Document()))
                .sort(Sorts.ascending("_id")).limit(100).toList()
            if (files.isEmpty()) break
            DB.fileCleanup.insertMany(session, files.map { Document("_id", UUID.randomUUID().toString()).append("fileName", it.getString("fileName")) })
            fileCursor = files.last().getString("_id")
        }
        // Older uploads did not have ownership metadata. Queue only the account's validated legacy path.
        locked.profilePicUrl.substringAfter("/static/uploads/", "").takeIf { legacyFileName.matches(it) }?.let {
            DB.fileCleanup.insertOne(session, Document("_id", UUID.randomUUID().toString()).append("fileName", it))
        }
        DB.personalFiles.deleteMany(session, eq("ownerId", actor))
        while (true) {
            val images = DB.mediaAssets.find(session, and(eq("kind", "profile"), eq("subjectId", actor)))
                .projection(Document("_id", 1)).limit(100).toList().map { it.getString("_id") }
            if (images.isEmpty()) break
            DB.mediaReferences.deleteMany(session, `in`("assetId", images))
            DB.mediaAssets.deleteMany(session, `in`("_id", images))
        }
        DB.mediaReferences.deleteMany(session, or(eq("ownerId", actor), eq("_id", "profile:$actor")))
        DB.users.deleteOne(session, eq("_id", actor))
    }
}

internal val legacyFileName = Regex("^[a-fA-F0-9-]{36}\\.(png|jpg)$")
