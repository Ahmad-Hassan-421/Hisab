package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.media.ProfileImages
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.respondFailure
import com.hisab.server.plugins.privacyAcknowledged
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
                val updated = users.findOneAndUpdate(
                    UserDocument::id eq id,
                    Updates.combine(
                        Updates.set("name", req.name), Updates.set("profilePicUrl", image),
                        Updates.set("themePreference", theme), Updates.set("updatedAt", System.currentTimeMillis())
                    ),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                ) ?: return@put call.respondFailure(HttpStatusCode.NotFound)
                call.respond(updated.toResponse())
            }
        }
    }
}
