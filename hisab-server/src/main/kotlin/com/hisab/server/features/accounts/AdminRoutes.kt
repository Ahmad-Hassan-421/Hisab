package com.hisab.server.features.accounts

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.media.ProfileImages
import com.hisab.server.features.realtime.WebSocketManager
import com.hisab.server.models.UserDocument
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.PrivacyAccess
import com.hisab.server.plugins.authenticatedAccountRole
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.respondFailure
import com.mongodb.client.model.Filters
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.bson.Document
import org.litote.kmongo.eq
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Serializable private data class AccountOverview(val registered: Long, val registeredInLast30Days: Long, val suspended: Long)
@Serializable private data class AccountDirectoryPage(val users: List<DirectoryAccount>, val nextCursor: String?)
@Serializable private data class DirectoryAccount(
    val id: String,
    val publicId: String?,
    val name: String?,
    val email: String?,
    val createdAt: Long?,
    val role: String,
    val status: String,
    val emailVerified: Boolean,
    val avatarUrl: String?
)

@Serializable private data class ChangeAccountStatus(val status: String)

private val activeAccounts = Filters.or(Filters.eq("accountStatus", null), Filters.eq("accountStatus", ACTIVE_ACCOUNT))
private val suspendedAccounts = Filters.and(
    Filters.exists("accountStatus"), Filters.ne("accountStatus", null), Filters.ne("accountStatus", ACTIVE_ACCOUNT)
)
private val directoryFields = Projections.include("_id", "uniqueId", "name", "email", "createdAt", "role",
    "accountStatus", "emailVerifiedAt", "profilePicUrl")

private fun Document.registrationTime(): Long? = (get("createdAt") as? Number)?.toLong()

private fun Document.toDirectoryAccount() = DirectoryAccount(
    id = getString("_id"),
    publicId = getString("uniqueId"),
    name = getString("name"),
    email = getString("email"),
    createdAt = registrationTime(),
    role = accountRole(getString("role")),
    status = if (isActiveAccount(getString("accountStatus"))) ACTIVE_ACCOUNT else SUSPENDED_ACCOUNT,
    emailVerified = ((get("emailVerifiedAt") as? Number)?.toLong() ?: 0L) > 0,
    avatarUrl = getString("profilePicUrl")?.let(ProfileImages::publicUrl)
)

private data class DirectoryCursor(val time: Long?, val id: String) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("${time ?: ""}|$id".toByteArray(Charsets.UTF_8))

    fun after() = if (time == null) {
        Filters.and(Filters.eq("createdAt", null), Filters.lt("_id", id))
    } else {
        Filters.or(Filters.lt("createdAt", time), Filters.eq("createdAt", null),
            Filters.and(Filters.eq("createdAt", time), Filters.lt("_id", id)))
    }

    companion object {
        fun decode(value: String): DirectoryCursor? {
            if (value.length !in 1..256) return null
            val decoded = runCatching { Base64.getUrlDecoder().decode(value).toString(Charsets.UTF_8) }.getOrNull() ?: return null
            val parts = decoded.split('|', limit = 2)
            if (parts.size != 2 || parts[1].isBlank() || parts[1].length > 128) return null
            val time = if (parts[0].isEmpty()) null else parts[0].toLongOrNull() ?: return null
            return DirectoryCursor(time, parts[1])
        }
    }
}

private suspend fun ApplicationCall.allowAdminOperation(operation: String, limit: Int): Boolean {
    if (authenticatedAccountRole != ADMIN_ROLE) {
        respondFailure(HttpStatusCode.Forbidden, "Administrator access is required")
        return false
    }
    response.header(HttpHeaders.CacheControl, "no-store")
    if (allowAttempt("admin-$operation", authenticatedUserId, limit)) return true
    response.header(HttpHeaders.RetryAfter, "1800")
    respondFailure(HttpStatusCode.TooManyRequests, "Too many requests. Try again later.")
    return false
}

fun Route.adminRoutes() {
    authenticate("auth-jwt") {
        route("/admin") {
            install(PrivacyAccess)
            get("/overview") {
                if (!call.allowAdminOperation("read", 240)) return@get
                val accounts = MongoClientProvider.accountDirectory
                val since = System.currentTimeMillis() - Duration.ofDays(30).toMillis()
                val countLimit = CountOptions().maxTime(2, TimeUnit.SECONDS)
                call.respond(AccountOverview(accounts.countDocuments(Document(), countLimit),
                    accounts.countDocuments(Filters.gte("createdAt", since), countLimit),
                    accounts.countDocuments(suspendedAccounts, countLimit)))
            }

            get("/users") {
                if (!call.allowAdminOperation("read", 240)) return@get
                val params = call.request.queryParameters
                val query = params["q"]?.trim().orEmpty()
                val requestedLimit = params["limit"] ?: "20"
                val limit = requestedLimit.toIntOrNull()?.takeIf { it in 1..50 }
                    ?: return@get call.respondFailure(HttpStatusCode.BadRequest, "Page size must be 1 to 50")
                if (query.length > 100) return@get call.respondFailure(HttpStatusCode.BadRequest, "Search terms must be 100 characters or fewer")
                val filters = mutableListOf<org.bson.conversions.Bson>()
                when (params["status"] ?: "all") {
                    "all" -> Unit
                    ACTIVE_ACCOUNT -> filters += activeAccounts
                    SUSPENDED_ACCOUNT -> filters += suspendedAccounts
                    else -> return@get call.respondFailure(HttpStatusCode.BadRequest, "Invalid account status filter")
                }
                if (query.isNotEmpty()) {
                    val prefix = Pattern.compile("^${Pattern.quote(query)}", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                    filters += Filters.or(Filters.regex("name", prefix), Filters.regex("email", prefix), Filters.regex("uniqueId", prefix))
                }
                params["cursor"]?.let { value ->
                    val cursor = DirectoryCursor.decode(value)
                        ?: return@get call.respondFailure(HttpStatusCode.BadRequest, "Invalid page cursor")
                    filters += cursor.after()
                }
                val filter = if (filters.isEmpty()) Document() else Filters.and(filters)
                val page = MongoClientProvider.accountDirectory.find(filter).projection(directoryFields)
                    .sort(Sorts.descending("createdAt", "_id")).limit(limit + 1).maxTime(2, TimeUnit.SECONDS).toList()
                val results = page.take(limit)
                val next = if (page.size > limit) results.last().let { DirectoryCursor(it.registrationTime(), it.getString("_id")).encode() } else null
                call.respond(AccountDirectoryPage(results.map { it.toDirectoryAccount() }, next))
            }

            patch("/users/{id}/status") {
                if (!call.allowAdminOperation("status", 40)) return@patch
                val actorId = call.authenticatedUserId
                val actorVersion = requireNotNull(call.principal<JWTPrincipal>()).payload.hisabSessionVersion()
                val targetId = call.parameters["id"]?.takeIf { it.isNotBlank() && it.length <= 128 }
                    ?: return@patch call.respondFailure(HttpStatusCode.BadRequest, "Invalid account ID")
                val requested = call.receive<ChangeAccountStatus>().status
                if (requested !in setOf(ACTIVE_ACCOUNT, SUSPENDED_ACCOUNT)) {
                    return@patch call.respondFailure(HttpStatusCode.BadRequest, "Invalid account status")
                }
                val newVersion = changeAccountStatus(actorId, actorVersion, targetId, requested)
                if (newVersion != null) withTimeoutOrNull(500) { WebSocketManager.closeOlderSessions(targetId, newVersion) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun changeAccountStatus(actorId: String, actorVersion: Long, targetId: String, requested: String): Long? =
    MongoClientProvider.transaction { session ->
        val accounts = MongoClientProvider.users
        val actor = accounts.findOne(session, UserDocument::id eq actorId)
        if (actor == null || actor.sessionVersion != actorVersion || accountRole(actor.role) != ADMIN_ROLE ||
            !isActiveAccount(actor.accountStatus)) throw RequestFailure(HttpStatusCode.Forbidden, "Administrator access is required")
        val target = accounts.findOne(session, UserDocument::id eq targetId)
            ?: throw RequestFailure(HttpStatusCode.NotFound, "Account not found")
        if (accountRole(target.role) == ADMIN_ROLE) throw RequestFailure(HttpStatusCode.Forbidden, "Administrator accounts are protected")
        val oldStatus = if (isActiveAccount(target.accountStatus)) ACTIVE_ACCOUNT else SUSPENDED_ACCOUNT
        if (oldStatus == requested) return@transaction null
        val time = System.currentTimeMillis()
        accounts.updateOne(session, UserDocument::id eq targetId,
            Updates.combine(Updates.set("accountStatus", requested), Updates.inc("sessionVersion", 1L),
                Updates.unset("passwordRecovery"), Updates.unset("emailVerification"), Updates.set("updatedAt", time)))
        MongoClientProvider.adminAudit.insertOne(session, Document("_id", UUID.randomUUID().toString())
            .append("action", "account_status_changed").append("actorId", actorId).append("targetId", targetId)
            .append("from", oldStatus).append("to", requested).append("createdAt", time))
        target.sessionVersion + 1L
    }
