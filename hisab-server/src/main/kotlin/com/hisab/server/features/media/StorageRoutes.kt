package com.hisab.server.features.media

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable data class UploadResponse(val url: String)

internal const val DEFAULT_PROFILE_PATH = "/static/default_profile.png"
private const val MAX_PROFILE_BYTES = 5 * 1024 * 1024

fun Route.storageRoutes(uploadDirectory: File) {
    authenticate("auth-jwt") {
        post("/upload/profile-pic") {
            var bytes: ByteArray? = null
            var invalid = false
            call.receiveMultipart().forEachPart { part ->
                try {
                    if (part is PartData.FileItem) {
                        if (part.name != "image" || bytes != null) invalid = true
                        else bytes = part.streamProvider().use { it.readNBytes(MAX_PROFILE_BYTES + 1) }
                    }
                } finally {
                    part.dispose()
                }
            }
            val image = bytes
            if (invalid || image == null) return@post call.respond(HttpStatusCode.BadRequest, "Provide one image")
            if (image.size > MAX_PROFILE_BYTES) return@post call.respond(HttpStatusCode.PayloadTooLarge)
            val extension = when {
                image.size >= 8 && image.copyOf(8).contentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                ) -> "png"
                image.size >= 3 && image[0] == 0xff.toByte() && image[1] == 0xd8.toByte() && image[2] == 0xff.toByte() -> "jpg"
                else -> return@post call.respond(HttpStatusCode.UnsupportedMediaType, "Use a PNG or JPEG image")
            }
            val fileName = "${UUID.randomUUID()}.$extension"
            withContext(Dispatchers.IO) { File(uploadDirectory, fileName).writeBytes(image) }
            call.respond(UploadResponse(ProfileImages.publicUrl("/static/uploads/$fileName")))
        }
    }
}
