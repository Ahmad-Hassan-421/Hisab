package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.media.ProfileImages
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.respondFailure
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
        if (ownProfile) themePreference else "system", if (ownProfile) role else "user", createdAt, updatedAt)
}

fun Route.userRoutes() {
    authenticate("auth-jwt") {
        route("/users") {
            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respondFailure(HttpStatusCode.BadRequest)
                val user = MongoClientProvider.users.findOne(UserDocument::id eq id)
                if (user != null) call.respond(user.toResponse(call.authenticatedUserId)) else call.respondFailure(HttpStatusCode.NotFound)
            }

            get("/by-unique-id/{uid}") {
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
                val existing = users.findOne(UserDocument::id eq id) ?: return@put call.respondFailure(HttpStatusCode.NotFound)
                
                val updated = existing.copy(
                    name = req.name,
                    profilePicUrl = image,
                    themePreference = theme,
                    updatedAt = System.currentTimeMillis()
                )
                users.updateOneById(id, updated)
                call.respond(updated.toResponse())
            }
        }
    }
}
