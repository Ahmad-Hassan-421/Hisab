package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.media.ProfileImages
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.respondFailure
import com.hisab.server.plugins.privacyAcknowledged
import com.hisab.server.features.privacy.lockAccount
import com.hisab.server.features.media.mediaId
import com.hisab.server.features.media.setMediaReference
import com.hisab.server.plugins.RequestFailure
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq

@Serializable data class UpdateUserRequest(val name: String, val profilePicUrl: String, val themePreference: String)
@Serializable data class PatchUserRequest(val name: String? = null, val themePreference: String? = null)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val uniqueId: String,
    val profilePicUrl: String,
    val themePreference: String,
    val role: String,
    val createdAt: Long,
    val updatedAt: Long
)

internal fun UserDocument.toResponse(viewerId: String = id): UserResponse {
    val ownProfile = id == viewerId
    val image = ProfileImages.publicUrl(profilePicUrl)
    return UserResponse(id, name, if (ownProfile) email else "", uniqueId, image,
        if (ownProfile) themePreference else "system", if (ownProfile) accountRole(role) else USER_ROLE, createdAt, updatedAt)
}

fun Route.userRoutes() {
    authenticate("auth-jwt") {
        route("/users") {
            patch("/me") {
                val actor = call.authenticatedUserId
                val req = call.receive<PatchUserRequest>()
                val theme = req.themePreference?.lowercase()
                if (req.name != null && (req.name.isBlank() || req.name.length > 120) ||
                    theme != null && theme !in setOf("system", "light", "dark")) {
                    return@patch call.respondFailure(HttpStatusCode.BadRequest, "Invalid profile")
                }
                val updated = MongoClientProvider.transaction { session ->
                    lockAccount(session, actor)
                    val changes = mutableListOf<org.bson.conversions.Bson>(Updates.set("updatedAt", System.currentTimeMillis()))
                    req.name?.let { changes.add(Updates.set("name", it.trim())) }
                    theme?.let { changes.add(Updates.set("themePreference", it)) }
                    MongoClientProvider.users.findOneAndUpdate(session, UserDocument::id eq actor, Updates.combine(changes),
                        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER))!!
                }
                call.respond(updated.toResponse())
            }
            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respondFailure(HttpStatusCode.BadRequest)
                if (id != call.authenticatedUserId && !call.privacyAcknowledged) {
                    return@get call.respondFailure(privacyAcknowledgmentRequiredStatus, "Acknowledge the current Privacy Policy to continue")
                }
                val user = MongoClientProvider.users.findOne(UserDocument::id eq id)
                if (user != null) call.respond(user.toResponse(call.authenticatedUserId)) else call.respondFailure(HttpStatusCode.NotFound)
            }

            get("/by-unique-id/{uid}") {
                if (!call.privacyAcknowledged) {
                    return@get call.respondFailure(privacyAcknowledgmentRequiredStatus, "Acknowledge the current Privacy Policy to continue")
                }
                val uid = call.parameters["uid"] ?: return@get call.respondFailure(HttpStatusCode.BadRequest)
                val user = MongoClientProvider.users.findOne(UserDocument::uniqueId eq uid)
                if (user != null) call.respond(user.toResponse(call.authenticatedUserId)) else call.respondFailure(HttpStatusCode.NotFound)
            }

            put("/{id}") {
                val id = call.parameters["id"] ?: return@put call.respondFailure(HttpStatusCode.BadRequest)
                val userId = call.authenticatedUserId
                
                if (id != userId) return@put call.respondFailure(HttpStatusCode.Forbidden)

                val req = call.receive<UpdateUserRequest>()
                val theme = req.themePreference.lowercase()
                val image = ProfileImages.storedPath(req.profilePicUrl)
                if (req.name.isBlank() || req.name.length > 120 ||
                    theme !in setOf("system", "light", "dark") || req.profilePicUrl.length > 2048 ||
                    !ProfileImages.isSupported(image)
                ) return@put call.respondFailure(HttpStatusCode.BadRequest, "Invalid profile")
                val users = MongoClientProvider.users
                val updated = MongoClientProvider.transaction { session ->
                    lockAccount(session, userId)
                    mediaId(image)?.let { asset ->
                        if (MongoClientProvider.mediaAssets.findOne(session, Filters.and(Filters.eq("_id", asset),
                            Filters.eq("kind", "profile"), Filters.eq("subjectId", userId))) == null) {
                            throw RequestFailure(HttpStatusCode.BadRequest, "Invalid profile picture")
                        }
                    }
                    setMediaReference(session, "profile", userId, image)
                    users.findOneAndUpdate(session,
                    UserDocument::id eq id,
                    Updates.combine(
                        Updates.set("name", req.name), Updates.set("profilePicUrl", image),
                        Updates.set("themePreference", theme), Updates.set("updatedAt", System.currentTimeMillis())
                    ),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                    )!!
                }
                call.respond(updated.toResponse())
            }
        }
    }
}
