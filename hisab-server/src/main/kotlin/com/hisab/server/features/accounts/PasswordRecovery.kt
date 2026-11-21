package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.realtime.WebSocketManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.bson.Document
import org.litote.kmongo.eq
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.Locale

@Serializable private data class RecoveryRequest(val email: String)
@Serializable private data class RecoveryReset(val email: String, val code: String, val password: String)
@Serializable private data class VerifyRecoveryEmail(val code: String)
@Serializable private data class VerificationStatus(val verified: Boolean)

private enum class CodePurpose(val field: String) {
    VERIFY("emailVerification"), RESET("passwordRecovery")
}

private const val CODE_MINUTES = 20
private const val MINUTE_MS = 60_000L
private const val RATE_WINDOW_MS = 30 * MINUTE_MS
private val random = SecureRandom()
private val codePattern = Regex("^[A-Za-z0-9_-]{43}$")

private fun digest(value: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

internal suspend fun allowAttempt(scope: String, identity: String, limit: Int): Boolean {
    val window = System.currentTimeMillis() / RATE_WINDOW_MS
    val key = "$scope:$window:${digest(identity)}"
    val limits = MongoClientProvider.recoveryLimits
    val filter = Filters.eq("_id", key)
    val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)
    val countAttempt = Updates.combine(
        Updates.inc("count", 1),
        Updates.setOnInsert("expiresAt", Date((window + 2) * RATE_WINDOW_MS))
    )
    val bucket = try {
        limits.findOneAndUpdate(filter, countAttempt, options)
    } catch (error: MongoWriteException) {
        if (error.error.category != ErrorCategory.DUPLICATE_KEY) throw error
        limits.findOneAndUpdate(filter, countAttempt, options.upsert(false))
    }
    return bucket != null && bucket.getInteger("count") <= limit
}

private suspend fun ApplicationCall.allowRecovery(scope: String, limit: Int): Boolean {
    if (allowAttempt("$scope-source", request.local.remoteHost, limit) &&
        allowAttempt("$scope-global", "all", 1_200)
    ) return true
    response.header(HttpHeaders.RetryAfter, (RATE_WINDOW_MS / 1000).toString())
    respondFailure(HttpStatusCode.TooManyRequests, "Too many attempts. Try again later.")
    return false
}

private fun newCode(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).apply(random::nextBytes))

private fun matchesCode(purpose: CodePurpose, code: String) = Filters.and(
    Filters.eq("${purpose.field}.digest", digest(code)),
    Filters.gt("${purpose.field}.expiresAt", System.currentTimeMillis())
)

private suspend fun deliverCode(user: UserDocument, purpose: CodePurpose): Boolean {
    if (!isActiveAccount(user.accountStatus)) return true
    val users = MongoClientProvider.users
    val code = newCode()
    val now = System.currentTimeMillis()
    val expires = now + CODE_MINUTES * MINUTE_MS
    val eligible = if (purpose == CodePurpose.VERIFY) Filters.eq("emailVerifiedAt", null)
        else Filters.gt("emailVerifiedAt", 0L)
    val priorExpiry = "${purpose.field}.expiresAt"
    val challenge = Document("digest", digest(code)).append("expiresAt", expires)
    val stored = users.updateOne(
        Filters.and(UserDocument::id eq user.id, UserDocument::email eq user.email, eligible,
            Filters.or(Filters.eq("accountStatus", null), Filters.eq("accountStatus", ACTIVE_ACCOUNT)),
            Filters.or(Filters.exists(priorExpiry, false), Filters.lte(priorExpiry, expires - MINUTE_MS))),
        Updates.set(purpose.field, challenge)
    ).modifiedCount != 0L
    if (!stored) return true
    if (EmailUtil.sendAccountCode(user.email, code, purpose == CodePurpose.VERIFY, CODE_MINUTES)) return true
    users.updateOne(
        Filters.and(UserDocument::id eq user.id, Filters.eq("${purpose.field}.digest", challenge.getString("digest"))),
        Updates.unset(purpose.field)
    )
    return false
}

fun Route.passwordRecoveryRoutes() {
    val app = application
    val recoveryQueue = Channel<String>(64)
    repeat(2) {
        app.launch {
            for (email in recoveryQueue) {
                try {
                    if (!allowAttempt("request-account", email.lowercase(Locale.ROOT), 4)) continue
                    val user = MongoClientProvider.users.findOne(UserDocument::email eq email) ?: continue
                    if (user.emailVerifiedAt != null) deliverCode(user, CodePurpose.RESET)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    app.log.warn("Password recovery request could not be processed ({})", error.javaClass.simpleName)
                }
            }
        }
    }

    route("/auth") {
        route("/recovery") {
            post("/request") {
                val email = call.receive<RecoveryRequest>().email.trim()
                if (!validAccountEmail(email)) return@post call.respondFailure(HttpStatusCode.BadRequest, "Enter a valid email address")
                if (!call.allowRecovery("request", 120)) return@post
                if (!EmailUtil.isConfigured() || recoveryQueue.trySend(email).isFailure) {
                    return@post call.respondFailure(HttpStatusCode.ServiceUnavailable, "Recovery email is temporarily unavailable")
                }
                call.respond(HttpStatusCode.Accepted)
            }

            post("/reset") {
                val request = call.receive<RecoveryReset>()
                val email = request.email.trim()
                if (!call.allowRecovery("reset", 120)) return@post
                if (!validAccountEmail(email) || !codePattern.matches(request.code) ||
                    !PasswordUtil.meetsRequirements(request.password)
                ) return@post call.respondFailure(HttpStatusCode.BadRequest, "Check the recovery code and password requirements")

                val users = MongoClientProvider.users
                val eligible = Filters.and(UserDocument::email eq email, Filters.gt("emailVerifiedAt", 0L),
                    Filters.or(Filters.eq("accountStatus", null), Filters.eq("accountStatus", ACTIVE_ACCOUNT)))
                val user = users.findOne(Filters.and(eligible, matchesCode(CodePurpose.RESET, request.code)))
                    ?: return@post call.respondFailure(HttpStatusCode.BadRequest, "Recovery code is invalid or expired")
                val hash = withContext(Dispatchers.Default) { PasswordUtil.hash(request.password) }
                val updated = users.findOneAndUpdate(
                    Filters.and(UserDocument::id eq user.id, eligible, matchesCode(CodePurpose.RESET, request.code)),
                    Updates.combine(Updates.set("passwordHash", hash), Updates.unset(CodePurpose.RESET.field),
                        Updates.inc("sessionVersion", 1L), Updates.set("updatedAt", System.currentTimeMillis())),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                ) ?: return@post call.respondFailure(HttpStatusCode.BadRequest, "Recovery code is invalid or expired")
                withTimeoutOrNull(500) { WebSocketManager.closeOlderSessions(updated.id, updated.sessionVersion) }
                call.respond(HttpStatusCode.NoContent)
            }
        }

        authenticate("auth-jwt") {
            route("/verification") {
                get {
                    val user = MongoClientProvider.users.findOne(UserDocument::id eq call.authenticatedUserId)
                        ?: return@get call.respondFailure(HttpStatusCode.NotFound)
                    call.respond(VerificationStatus(user.emailVerifiedAt != null))
                }
                post("/request") {
                    val user = MongoClientProvider.users.findOne(UserDocument::id eq call.authenticatedUserId)
                        ?: return@post call.respondFailure(HttpStatusCode.NotFound)
                    if (user.emailVerifiedAt != null) return@post call.respond(HttpStatusCode.NoContent)
                    if (!call.allowRecovery("verify-request", 120)) return@post
                    if (!allowAttempt("verify-account", user.id, 4)) {
                        call.response.header(HttpHeaders.RetryAfter, (RATE_WINDOW_MS / 1000).toString())
                        return@post call.respondFailure(HttpStatusCode.TooManyRequests, "Too many attempts. Try again later.")
                    }
                    if (!EmailUtil.isConfigured() || !deliverCode(user, CodePurpose.VERIFY)) {
                        return@post call.respondFailure(HttpStatusCode.ServiceUnavailable, "Verification email is temporarily unavailable")
                    }
                    call.respond(HttpStatusCode.Accepted)
                }
                post("/confirm") {
                    if (!call.allowRecovery("verify-confirm", 120)) return@post
                    val request = call.receive<VerifyRecoveryEmail>()
                    if (!codePattern.matches(request.code)) return@post call.respondFailure(HttpStatusCode.BadRequest, "Verification code is invalid or expired")
                    val result = MongoClientProvider.users.updateOne(
                        Filters.and(UserDocument::id eq call.authenticatedUserId, Filters.eq("emailVerifiedAt", null),
                            matchesCode(CodePurpose.VERIFY, request.code)),
                        Updates.combine(Updates.set("emailVerifiedAt", System.currentTimeMillis()), Updates.unset(CodePurpose.VERIFY.field))
                    )
                    if (result.modifiedCount == 0L) return@post call.respondFailure(HttpStatusCode.BadRequest, "Verification code is invalid or expired")
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
