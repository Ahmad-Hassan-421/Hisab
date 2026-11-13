package com.hisab.server.features.groups

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.realtime.WebSocketManager
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.models.HisabRequestDocument
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.authenticatedUserId
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.reactivestreams.client.ClientSession
import com.hisab.server.plugins.respondFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.and
import org.litote.kmongo.contains
import org.litote.kmongo.eq

@Serializable data class CreateGroupRequest(val name: String, val members: List<String>)

internal suspend fun ApplicationCall.requireGroupMember(groupId: String): HisabGroupDocument? {
    val group = MongoClientProvider.groups.findOne(HisabGroupDocument::id eq groupId)
    if (group == null) {
        respondFailure(HttpStatusCode.NotFound)
        return null
    }
    if (authenticatedUserId !in group.members) {
        respondFailure(HttpStatusCode.Forbidden)
        return null
    }
    return group
}

internal suspend fun lockGroupForMember(session: ClientSession, groupId: String, actor: String): HisabGroupDocument {
    val groups = MongoClientProvider.groups
    // Serialize membership and ledger changes through the same group document.
    return groups.findOneAndUpdate(session,
        and(HisabGroupDocument::id eq groupId, HisabGroupDocument::members contains actor),
        Updates.inc("ledgerRevision", 1L)
    ) ?: run {
        if (groups.findOne(session, HisabGroupDocument::id eq groupId) == null) {
            throw RequestFailure(HttpStatusCode.NotFound, "Group not found")
        }
        throw RequestFailure(HttpStatusCode.Forbidden, "Group membership required")
    }
}

fun Route.hisabGroupRoutes() {
    authenticate("auth-jwt") {
        route("/groups") {
            get {
                val userId = call.request.queryParameters["userId"] ?: return@get call.respondFailure(HttpStatusCode.BadRequest)
                if (userId != call.authenticatedUserId) return@get call.respondFailure(HttpStatusCode.Forbidden)
                val groups = MongoClientProvider.groups.find(HisabGroupDocument::members contains userId).toList()
                call.respond(groups)
            }
            post {
                val req = call.receive<CreateGroupRequest>()
                val userId = call.authenticatedUserId
                if (req.members != listOf(userId) || req.name.isBlank() || req.name.length > 120) {
                    return@post call.respondFailure(HttpStatusCode.BadRequest, "Invalid group")
                }
                val newGroup = HisabGroupDocument(name = req.name.trim(), members = listOf(userId))
                MongoClientProvider.groups.insertOne(newGroup)
                call.respond(HttpStatusCode.Created, newGroup)
            }
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respondFailure(HttpStatusCode.BadRequest)
                val actor = call.authenticatedUserId
                MongoClientProvider.transaction { session ->
                    lockGroupForMember(session, id, actor)
                    MongoClientProvider.entries.deleteMany(session, HisabEntryDocument::groupId eq id)
                    MongoClientProvider.requests.deleteMany(session, HisabRequestDocument::groupId eq id)
                    MongoClientProvider.groups.deleteOne(session, HisabGroupDocument::id eq id)
                }
                call.respond(HttpStatusCode.OK)
            }
            post("/{id}/leave") {
                val id = call.parameters["id"] ?: return@post call.respondFailure(HttpStatusCode.BadRequest)
                val userId = call.authenticatedUserId
                
                val groups = MongoClientProvider.groups
                val updated = groups.findOneAndUpdate(
                    and(HisabGroupDocument::id eq id, HisabGroupDocument::members contains userId),
                    Updates.combine(Updates.pull("members", userId), Updates.addToSet("exMembers", userId)),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                )
                if (updated == null && groups.findOneById(id) == null) {
                    return@post call.respondFailure(HttpStatusCode.NotFound)
                }
                updated?.members?.forEach { WebSocketManager.notifyUser(it, "GROUP_UPDATED") }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
