package com.hisab.server.features.media

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.accounts.allowAttempt
import com.hisab.server.features.groups.lockGroupForMember
import com.hisab.server.features.privacy.lockAccount
import com.hisab.server.features.realtime.WebSocketManager
import com.hisab.server.plugins.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.Binary
import java.io.File
import java.util.UUID

@Serializable data class UploadResponse(val url: String)
@Serializable private data class MediaAccessInput(val ids: List<String>)
@Serializable private data class MediaAccessResponse(val allowed: Set<String>)
@Serializable private data class GroupPictures(val pictureUrl: String, val pictureUpdatedAt: Long, val members: Map<String, String>)
internal const val DEFAULT_PROFILE_URL = "https://ssl.gstatic.com/images/branding/product/1x/avatar_circle_blue_512dp.png"
internal const val DEFAULT_PROFILE_PATH = "/static/default_profile.png"

private suspend fun ApplicationCall.readImage(): ByteArray {
    var bytes: ByteArray? = null
    var invalid = false
    receiveMultipart().forEachPart { part ->
        try {
            if (part is PartData.FileItem && part.name == "image" && bytes == null) {
                bytes = withContext(Dispatchers.IO) { part.streamProvider().use { it.readNBytes(MAX_IMAGE_INPUT + 1) } }
                if (bytes!!.size > MAX_IMAGE_INPUT) throw RequestFailure(HttpStatusCode.PayloadTooLarge, "Choose an image smaller than 5 MB")
            } else invalid = true
        } finally { part.dispose() }
    }
    if (invalid || bytes == null) throw RequestFailure(HttpStatusCode.BadRequest, "Provide one image")
    return withContext(Dispatchers.IO) { normalizedImage(bytes!!) }
}

private suspend fun savePicture(actor: String, groupId: String?, image: ByteArray?): String {
    val id = UUID.randomUUID().toString()
    val path = if (image == null) "" else mediaPath(id)
    DB.transaction { session ->
        lockAccount(session, actor)
        if (groupId != null) lockGroupForMember(session, groupId, actor)
        if (image != null) DB.mediaAssets.insertOne(session, Document("_id", id).append("bytes", Binary(image))
            .append("kind", if (groupId == null) "profile" else "group").append("subjectId", groupId ?: actor)
            .append("createdAt", System.currentTimeMillis()))
        if (groupId == null) {
            DB.users.updateOne(session, eq("_id", actor), Updates.combine(Updates.set("profilePicUrl", path),
                Updates.set("updatedAt", System.currentTimeMillis())))
        } else DB.groups.updateOne(session, eq("_id", groupId), Updates.combine(Updates.set("pictureUrl", path),
            Updates.set("pictureUpdatedAt", System.currentTimeMillis())))
        setMediaReference(session, if (groupId == null) "profile" else "group", groupId ?: actor, path)
    }
    if (groupId != null) DB.groups.findOneById(groupId)?.members?.forEach { WebSocketManager.notifyUser(it, "GROUP_UPDATED") }
    else WebSocketManager.notifyUser(actor, "PROFILE_UPDATED")
    return path
}

@Suppress("UNUSED_PARAMETER")
fun Route.storageRoutes(uploadDirectory: File) {
    authenticate("auth-jwt") {
        get("/media/{id}") {
            val id = call.parameters["id"].orEmpty()
            if (!mediaIdPattern.matches(id) || id !in allowedMedia(call.authenticatedUserId, listOf(id))) {
                throw RequestFailure(HttpStatusCode.NotFound, "Picture unavailable")
            }
            val asset = DB.mediaAssets.findOne(eq("_id", id)) ?: throw RequestFailure(HttpStatusCode.NotFound, "Picture unavailable")
            call.response.header(HttpHeaders.CacheControl, "private, max-age=0, must-revalidate")
            call.response.header(HttpHeaders.Vary, "Authorization")
            call.response.header(HttpHeaders.ETag, "\"$id\"")
            // Authorize before evaluating a validator, including requests for obsolete versions.
            if (call.request.headers[HttpHeaders.IfNoneMatch] == "\"$id\"") call.respond(HttpStatusCode.NotModified)
            else call.respondBytes(asset.get("bytes", Binary::class.java).data, ContentType.Image.JPEG)
        }
        post("/media/access") {
            val input = call.receive<MediaAccessInput>()
            if (input.ids.size > 50 || input.ids.any { !mediaIdPattern.matches(it) }) throw RequestFailure(HttpStatusCode.BadRequest, "Invalid picture request")
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(MediaAccessResponse(allowedMedia(call.authenticatedUserId, input.ids.distinct())))
        }
        get("/groups/{groupId}/pictures") {
            val actor = call.authenticatedUserId
            val group = DB.groups.findOne(and(eq("_id", call.parameters["groupId"].orEmpty()), eq("members", actor)))
                ?: throw RequestFailure(HttpStatusCode.Forbidden, "Active membership required")
            val ids = call.request.queryParameters["members"].orEmpty().split(',').filter { it.isNotBlank() }.distinct()
            if (ids.size > 100) throw RequestFailure(HttpStatusCode.BadRequest, "Request at most 100 members")
            val permitted = ids.filter { it in group.members || it in group.exMembers }
            val rows = if (permitted.isEmpty()) emptyList() else DB.accountDirectory.find(`in`("_id", permitted))
                .projection(Document("_id", 1).append("profilePicUrl", 1)).limit(100).toList()
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(GroupPictures(group.pictureUrl, group.pictureUpdatedAt,
                permitted.associateWith { "" } + rows.associate { it.getString("_id") to ProfileImages.publicUrl(it.getString("profilePicUrl").orEmpty()) }))
        }
        route("/upload") {
            install(PrivacyAccess)
            post("/profile-pic") {
                val actor = call.authenticatedUserId
                if (!allowAttempt("picture-upload", actor, 30)) throw RequestFailure(HttpStatusCode.TooManyRequests, "Try again later")
                call.respond(UploadResponse(savePicture(actor, null, call.readImage())))
            }
            delete("/profile-pic") { call.respond(UploadResponse(savePicture(call.authenticatedUserId, null, null))) }
        }
        route("/groups/{groupId}/picture") {
            install(PrivacyAccess)
            post {
                val actor = call.authenticatedUserId
                val group = call.parameters["groupId"].orEmpty()
                if (DB.groups.findOne(and(eq("_id", group), eq("members", actor))) == null) throw RequestFailure(HttpStatusCode.Forbidden, "Active membership required")
                if (!allowAttempt("picture-upload", actor, 30)) throw RequestFailure(HttpStatusCode.TooManyRequests, "Try again later")
                call.respond(UploadResponse(savePicture(actor, group, call.readImage())))
            }
            delete { call.respond(UploadResponse(savePicture(call.authenticatedUserId, call.parameters["groupId"].orEmpty(), null))) }
        }
    }
}
