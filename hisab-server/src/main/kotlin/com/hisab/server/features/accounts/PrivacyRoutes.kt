package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.respondFailure
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
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
import kotlinx.serialization.json.Json
import org.bson.Document
import org.litote.kmongo.eq
import java.util.UUID

@Serializable internal data class PrivacySection(val title: String, val body: String)

@Serializable internal data class PrivacyDocument(
    val version: String,
    val acknowledgmentVersion: String,
    val effectiveDate: String,
    val sections: List<PrivacySection>
)

@Serializable private data class PrivacyStatus(val acknowledged: Boolean, val acknowledgmentVersion: String, val acceptedAt: Long?)
@Serializable private data class AcknowledgePolicy(val version: String)
@Serializable private data class DataRequestInput(val type: String)
@Serializable private data class DataRequestReceipt(val reference: String, val requestedAt: Long)

internal val privacyAcknowledgmentRequiredStatus = HttpStatusCode(428, "Precondition Required")

internal val currentPrivacyPolicy: PrivacyDocument by lazy {
    val resource = checkNotNull(PrivacyDocument::class.java.getResourceAsStream("/policies/privacy-policy.json"))
    val document = resource.bufferedReader().use { Json.decodeFromString<PrivacyDocument>(it.readText()) }
    val version = Regex("^[A-Za-z0-9_.-]{1,64}$")
    check(version.matches(document.version) && version.matches(document.acknowledgmentVersion) &&
        document.effectiveDate.isNotBlank() && document.sections.isNotEmpty() &&
        document.sections.all { it.title.isNotBlank() && it.body.isNotBlank() }) { "The bundled Privacy Policy is invalid" }
    document
}

internal fun UserDocument.hasAcknowledgedPrivacy(): Boolean =
    privacyPolicyAcknowledgmentVersion == currentPrivacyPolicy.acknowledgmentVersion && privacyPolicyAcceptedAt != null

private fun UserDocument.privacyStatus() = PrivacyStatus(
    hasAcknowledgedPrivacy(), currentPrivacyPolicy.acknowledgmentVersion, privacyPolicyAcceptedAt
)

fun Route.privacyRoutes() {
    get("/privacy/policy") {
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respond(currentPrivacyPolicy)
    }
    authenticate("auth-jwt") {
        route("/account/privacy") {
            get("/status") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val user = MongoClientProvider.users.findOne(UserDocument::id eq call.authenticatedUserId)
                    ?: return@get call.respondFailure(HttpStatusCode.NotFound)
                call.respond(user.privacyStatus())
            }
            post("/acknowledge") {
                val policy = currentPrivacyPolicy
                if (call.receive<AcknowledgePolicy>().version != policy.version) {
                    return@post call.respondFailure(HttpStatusCode.Conflict, "The Privacy Policy has changed. Review the latest version.")
                }
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val identity = UserDocument::id eq call.authenticatedUserId
                val time = System.currentTimeMillis()
                val history = Document("version", policy.version).append("acknowledgmentVersion", policy.acknowledgmentVersion)
                    .append("acceptedAt", time)
                val users = MongoClientProvider.users
                val updated = users.findOneAndUpdate(
                    Filters.and(identity, Filters.or(Filters.ne("privacyPolicyAcknowledgmentVersion", policy.acknowledgmentVersion),
                        Filters.eq("privacyPolicyAcceptedAt", null))),
                    Updates.combine(Updates.set("privacyPolicyVersion", policy.version),
                        Updates.set("privacyPolicyAcknowledgmentVersion", policy.acknowledgmentVersion),
                        Updates.set("privacyPolicyAcceptedAt", time), Updates.push("privacyPolicyHistory", history)),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                ) ?: users.findOne(identity) ?: return@post call.respondFailure(HttpStatusCode.NotFound)
                call.respond(updated.privacyStatus())
            }
            post("/data-requests") {
                val type = call.receive<DataRequestInput>().type
                if (type !in setOf("access", "correction", "deletion")) {
                    return@post call.respondFailure(HttpStatusCode.BadRequest, "Choose a valid privacy request")
                }
                val userId = call.authenticatedUserId
                if (!allowAttempt("privacy-data-request", userId, 10)) {
                    return@post call.respondFailure(HttpStatusCode.TooManyRequests, "Too many requests. Try again later.")
                }
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val collection = MongoClientProvider.privacyRequests
                val pending = Filters.and(Filters.eq("ownerId", userId), Filters.eq("type", type), Filters.eq("status", "pending"))
                val record = try {
                    collection.findOneAndUpdate(pending,
                        Updates.combine(Updates.setOnInsert("_id", UUID.randomUUID().toString()),
                            Updates.setOnInsert("createdAt", System.currentTimeMillis())),
                        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true))
                } catch (error: MongoWriteException) {
                    if (error.error.category != ErrorCategory.DUPLICATE_KEY) throw error
                    collection.findOne(pending)
                }
                if (record == null) return@post call.respondFailure(HttpStatusCode.ServiceUnavailable, "Couldn’t record your request. Try again.")
                call.respond(HttpStatusCode.Accepted, DataRequestReceipt(record.getString("_id"), record.getLong("createdAt")))
            }
        }
    }
}
