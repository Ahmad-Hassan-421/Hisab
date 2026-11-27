package com.hisab.server.features.notifications

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.privacy.lockAccount
import com.hisab.server.plugins.*
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import org.bson.Document
import java.security.MessageDigest
import java.util.Date

@OptIn(ExperimentalSerializationApi::class)
@Serializable internal data class NotificationPreferences(@EncodeDefault val debts: Boolean = true, @EncodeDefault val payments: Boolean = true,
    @EncodeDefault val invitations: Boolean = true, @EncodeDefault val recovery: Boolean = true, @EncodeDefault val updates: Boolean = true,
    @EncodeDefault val previews: Boolean = false)
@Serializable private data class NotificationSettings(val preferences: NotificationPreferences, val deliveryConfigured: Boolean)
@Serializable private data class RegisterDevice(val installationId: String, val installationSecret: String, val token: String,
    val packageName: String, val versionCode: Int, val permissionGranted: Boolean, val bindingRevision: Long)
@Serializable private data class RevokeDevice(val installationId: String, val installationSecret: String, val bindingRevision: Long)
internal fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }
private val installationPattern = Regex("^[a-f0-9-]{36}$")
internal fun Document.preferences(): NotificationPreferences {
    val value = get("preferences", Document::class.java) ?: Document()
    fun enabled(key: String) = value.getBoolean(key, true)
    return NotificationPreferences(enabled("debts"), enabled("payments"), enabled("invitations"), enabled("recovery"),
        enabled("updates"), value.getBoolean("previews", false))
}
internal fun NotificationPreferences.document() = Document("debts", debts).append("payments", payments)
    .append("invitations", invitations).append("recovery", recovery).append("updates", updates).append("previews", previews)
internal suspend fun preferences(actor: String): NotificationPreferences =
    (DB.notificationPreferences.findOne(eq("_id", actor)) ?: Document()).preferences()

fun Route.notificationRoutes() {
    // Possession of the installation's random proof permits only its own revocation, even after JWT expiry/logout.
    post("/notifications/devices/revoke") {
        val input = call.receive<RevokeDevice>()
        if (!installationPattern.matches(input.installationId) || !Regex("^[a-f0-9]{64}$").matches(input.installationSecret) || input.bindingRevision < 1) {
            throw RequestFailure(HttpStatusCode.BadRequest, "Invalid device registration")
        }
        DB.devices.deleteOne(and(eq("_id", input.installationId), eq("secretDigest", digest(input.installationSecret)),
            lte("bindingRevision", input.bindingRevision)))
        call.respond(HttpStatusCode.NoContent)
    }
    authenticate("auth-jwt") {
        route("/notifications") {
            install(PrivacyAccess)
            get("/preferences") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(NotificationSettings(preferences(call.authenticatedUserId), PushGateway.configured))
            }
            put("/preferences") {
                val actor = call.authenticatedUserId
                if (call.request.headers["X-Hisab-Account"] != actor) throw RequestFailure(HttpStatusCode.Conflict, "Account changed; retry from your current session")
                val input = call.receive<NotificationPreferences>()
                DB.transaction { session ->
                    lockAccount(session, actor)
                    DB.notificationPreferences.replaceOne(session, eq("_id", actor), Document("_id", actor)
                        .append("ownerId", actor).append("preferences", input.document()), ReplaceOptions().upsert(true))
                }
                call.respond(NotificationSettings(input, PushGateway.configured))
            }
            put("/devices") {
                val actor = call.authenticatedUserId
                if (call.request.headers["X-Hisab-Account"] != actor) throw RequestFailure(HttpStatusCode.Conflict, "Account changed; retry from your current session")
                val input = call.receive<RegisterDevice>()
                if (!installationPattern.matches(input.installationId) || !Regex("^[a-f0-9]{64}$").matches(input.installationSecret) ||
                    !Regex("^[A-Za-z0-9:_-]{20,4096}$").matches(input.token) || input.versionCode < 1 || input.bindingRevision < 1 ||
                    input.packageName !in setOf("com.example.hisab", "com.example.hisab.qa")) {
                    throw RequestFailure(HttpStatusCode.BadRequest, "Invalid device registration")
                }
                DB.transaction { session ->
                    val account = lockAccount(session, actor)
                    val existing = DB.devices.findOne(session, eq("_id", input.installationId))
                    val proof = digest(input.installationSecret)
                    if (existing != null && existing.getString("secretDigest") != proof) {
                        throw RequestFailure(HttpStatusCode.Forbidden, "Device proof required")
                    }
                    if (existing != null && ((existing["bindingRevision"] as? Number)?.toLong() ?: 0) > input.bindingRevision) {
                        throw RequestFailure(HttpStatusCode.Conflict, "A newer device registration is active")
                    }
                    if (existing != null && ((existing["bindingRevision"] as? Number)?.toLong() ?: 0) == input.bindingRevision &&
                        (existing.getString("ownerId") != actor || existing.getString("tokenDigest") != digest(input.token))) {
                        throw RequestFailure(HttpStatusCode.Conflict, "A newer device registration is active")
                    }
                    if (input.permissionGranted && (existing == null || existing.getString("ownerId") != actor || !existing.getBoolean("enabled", false)) && DB.devices.countDocuments(session,
                        and(eq("ownerId", actor), eq("enabled", true), ne("_id", input.installationId))) >= 20) {
                        throw RequestFailure(HttpStatusCode.Conflict, "Remove an older device before adding another")
                    }
                    val collision = DB.devices.findOne(session, and(eq("tokenDigest", digest(input.token)), ne("_id", input.installationId)))
                    if (collision != null) throw RequestFailure(HttpStatusCode.Conflict, "Device token is already bound")
                    DB.devices.replaceOne(session, eq("_id", input.installationId), Document("_id", input.installationId)
                        .append("ownerId", actor).append("secretDigest", proof).append("token", input.token).append("tokenDigest", digest(input.token))
                        .append("packageName", input.packageName).append("versionCode", input.versionCode)
                        .append("bindingRevision", input.bindingRevision)
                        .append("sessionVersion", account.sessionVersion)
                        .append("enabled", input.permissionGranted).append("updatedAt", System.currentTimeMillis())
                        .append("expiresAt", Date(System.currentTimeMillis() + 45L * 86_400_000)), ReplaceOptions().upsert(true))
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
