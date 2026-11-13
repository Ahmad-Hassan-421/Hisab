package com.hisab.server.features.invitations

import com.hisab.server.features.groups.lockGroupForMember
import com.hisab.server.features.media.ProfileImages

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.models.HisabRequestDocument
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.features.realtime.WebSocketManager
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.litote.kmongo.and
import org.litote.kmongo.eq

@Serializable data class SendRequestRequest(val receiverUniqueId: String, val groupId: String)
@Serializable data class UpdateRequestRequest(val status: String)

@Serializable
data class InviteNotificationPayload(
    val requestId: String,
    val senderId: String,
    val senderName: String,
    val senderEmail: String,
    val profilePictureUrl: String?,
    val groupId: String,
    val groupName: String
)

private fun HisabRequestDocument.toResponse(): HisabRequestDocument {
    val pic = ProfileImages.publicUrl(profilePictureUrl ?: senderProfilePicUrl)
    return copy(
        senderProfilePicUrl = pic,
        profilePictureUrl = pic
    )
}

fun Route.requestRoutes() {
    authenticate("auth-jwt") {
        route("/requests") {
            get {
                val receiverId = call.request.queryParameters["receiverId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                if (receiverId != call.authenticatedUserId) return@get call.respond(HttpStatusCode.Forbidden)
                val status = call.request.queryParameters["status"] ?: "pending"
                val requests = MongoClientProvider.requests.find(
                    and(HisabRequestDocument::receiverId eq receiverId, HisabRequestDocument::status eq status)
                ).toList()
                call.respond(requests.map { it.toResponse() })
            }
            
            post {
                val req = call.receive<SendRequestRequest>()
                val senderId = call.authenticatedUserId
                if (req.receiverUniqueId.length != 12 || req.receiverUniqueId.any { it !in '0'..'9' }) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid recipient ID")
                }
                
                val users = MongoClientProvider.users
                val sender = users.findOne(UserDocument::id eq senderId) ?: return@post call.respond(HttpStatusCode.NotFound)
                
                if (req.receiverUniqueId == sender.uniqueId) {
                    call.respond(HttpStatusCode.BadRequest, "Cannot invite yourself")
                    return@post
                }
                
                val receiver = users.findOne(UserDocument::uniqueId eq req.receiverUniqueId) 
                    ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")
                
                val requests = MongoClientProvider.requests
                val (request, created) = MongoClientProvider.transaction { session ->
                    val group = lockGroupForMember(session, req.groupId, senderId)
                    if (receiver.id in group.members) {
                        throw RequestFailure(HttpStatusCode.BadRequest, "User is already in the Hisab")
                    }
                    val pending = requests.findOne(session, and(
                        HisabRequestDocument::senderId eq sender.id,
                        HisabRequestDocument::receiverId eq receiver.id,
                        HisabRequestDocument::groupId eq group.id,
                        HisabRequestDocument::status eq "pending"
                    ))
                    if (pending != null) return@transaction pending to false
                    val storedPic = ProfileImages.storedPath(sender.profilePicUrl)
                    val invitation = HisabRequestDocument(
                        senderId = sender.id,
                        senderName = sender.name,
                        senderEmail = sender.email,
                        senderProfilePicUrl = storedPic,
                        profilePictureUrl = storedPic,
                        receiverId = receiver.id,
                        groupId = group.id,
                        groupName = group.name
                    )
                    requests.insertOne(session, invitation)
                    invitation to true
                }
                if (created) {
                    val payload = Json.encodeToString(
                        InviteNotificationPayload(
                            requestId = request.id,
                            senderId = request.senderId,
                            senderName = request.senderName,
                            senderEmail = request.senderEmail,
                            profilePictureUrl = ProfileImages.publicUrl(request.profilePictureUrl ?: request.senderProfilePicUrl),
                            groupId = request.groupId,
                            groupName = request.groupName
                        )
                    )
                    WebSocketManager.notifyUser(receiver.id, "NEW_REQUEST", payload)
                }
                
                call.respond(HttpStatusCode.Created, request.toResponse())
            }
            
            patch("/{id}") {
                val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<UpdateRequestRequest>()
                if (req.status !in setOf("accepted", "declined")) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Invalid invitation state")
                }
                val actor = call.authenticatedUserId
                val requests = MongoClientProvider.requests
                val (updated, joined) = MongoClientProvider.transaction { session ->
                    val request = requests.findOne(session, HisabRequestDocument::id eq id)
                        ?: throw RequestFailure(HttpStatusCode.NotFound, "Invitation not found")
                    if (request.receiverId != actor) {
                        throw RequestFailure(HttpStatusCode.Forbidden, "Invitation recipient required")
                    }
                    if (request.status == req.status) return@transaction request to false
                    if (request.status != "pending") {
                        throw RequestFailure(HttpStatusCode.Conflict, "Invitation already decided")
                    }

                    val joining = req.status == "accepted"
                    if (joining) {
                        lockGroupForMember(session, request.groupId, request.senderId)
                        MongoClientProvider.groups.updateOne(session, HisabGroupDocument::id eq request.groupId,
                            Updates.combine(Updates.addToSet("members", actor), Updates.pull("exMembers", actor))
                        )
                    }
                    requests.updateOne(session, HisabRequestDocument::id eq id, Updates.set("status", req.status))
                    request.copy(status = req.status) to joining
                }
                if (joined) {
                    WebSocketManager.notifyUser(updated.senderId, "GROUP_UPDATED")
                    WebSocketManager.notifyUser(updated.receiverId, "GROUP_UPDATED")
                }
                
                call.respond(updated.toResponse())
            }
        }
    }
}
