package com.hisab.server.features.paging

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.database.PairBalance
import com.hisab.server.database.groupPairBalances
import com.hisab.server.features.accounts.UserResponse
import com.hisab.server.features.accounts.toResponse
import com.hisab.server.features.groups.requireGroupMember
import com.hisab.server.features.media.ProfileImages
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.models.HisabRequestDocument
import com.hisab.server.plugins.PrivacyAccess
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.authenticatedUserId
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.bson.Document
import java.util.concurrent.TimeUnit

@Serializable
data class GroupCard(val id: String, val name: String, val memberCount: Int, val createdAt: Long)
@Serializable
data class GroupOverview(
    val group: HisabGroupDocument,
    val self: UserResponse,
    val pairs: List<PairBalance>,
    val names: Map<String, String>,
    val entryCount: Long,
    val settled: Boolean,
    val revision: Long
)

private fun Document.number(field: String) = (get(field) as? Number)?.toLong() ?: 0L
private fun Document.strings(field: String) = getList(field, String::class.java).orEmpty()
private fun Document.memberResponse() = UserResponse(getString("_id"), getString("name"), "", getString("uniqueId").orEmpty(),
    ProfileImages.publicUrl(getString("profilePicUrl").orEmpty()), "system", "user", number("createdAt"), number("updatedAt"))

private fun Document.entry() = HisabEntryDocument(
    getString("_id"), getString("groupId"), getString("debtorId"), getString("creditorId"),
    (get("amount") as Number).toDouble(), getString("description").orEmpty(), number("timestamp"),
    getString("type") ?: "EXPENSE"
)


fun Route.pagedRoutes() {
    authenticate("auth-jwt") {
        route("/v2") {
            install(PrivacyAccess)
            get("/groups/page") {
                val actor = call.authenticatedUserId
                val page = call.pageQuery("groups:$actor")
                val rows = MongoClientProvider.groupDirectory.find(Filters.and(Filters.eq("members", actor), page.filter("createdAt")))
                    .projection(Projections.include("_id", "name", "members", "createdAt"))
                    .sort(Sorts.descending("createdAt", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(CursorPage(rows.take(page.limit).map {
                    GroupCard(it.getString("_id"), it.getString("name"), it.strings("members").distinct().size, it.number("createdAt"))
                }, page.next(rows, "createdAt")))
            }

            get("/groups/{id}") {
                val group = call.requireGroupMember(requireNotNull(call.parameters["id"])) ?: return@get
                call.respond(group)
            }

            get("/groups/{id}/overview") {
                val id = requireNotNull(call.parameters["id"])
                val actor = call.authenticatedUserId
                // Retry a bounded number of times if a writer changes the ledger during aggregation.
                repeat(3) {
                    val before = MongoClientProvider.groupDirectory.findOne(Filters.eq("_id", id))?.number("ledgerRevision") ?: 0
                    val group = call.requireGroupMember(id) ?: return@get
                    val pairs = groupPairBalances(id)
                    val count = MongoClientProvider.entries.countDocuments(Filters.eq("groupId", id))
                    val profiles = MongoClientProvider.accountDirectory.find(Filters.`in`("_id", (group.members + group.exMembers).distinct()))
                        .projection(Projections.include("_id", "name")).maxTime(3, TimeUnit.SECONDS).toList()
                    val self = MongoClientProvider.users.findOne(Filters.eq("_id", actor))
                        ?: throw RequestFailure(HttpStatusCode.Unauthorized, "Sign-in required")
                    val after = MongoClientProvider.groupDirectory.findOne(Filters.eq("_id", id))
                    if (after != null && actor in after.strings("members") && after.number("ledgerRevision") == before) {
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respond(GroupOverview(group, self.toResponse(), pairs,
                            profiles.associate { it.getString("_id") to it.getString("name") }, count,
                            count > 0 && pairs.all { it.amount.toBigDecimal().signum() == 0 }, before))
                        return@get
                    }
                }
                throw RequestFailure(HttpStatusCode.Conflict, "The Hisab changed. Refresh to continue")
            }

            get("/groups/{id}/members") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val id = requireNotNull(call.parameters["id"])
                val actor = call.authenticatedUserId
                val group = call.requireGroupMember(id) ?: return@get
                val page = call.pageQuery("members:$actor:$id")
                val rows = MongoClientProvider.accountDirectory.find(Filters.and(
                    Filters.`in`("_id", (group.members + group.exMembers).distinct()), page.filter("createdAt")))
                    .projection(Projections.include("_id", "name", "uniqueId", "profilePicUrl", "createdAt", "updatedAt"))
                    .sort(Sorts.descending("createdAt", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                call.respond(CursorPage(rows.take(page.limit).map { it.memberResponse() }, page.next(rows, "createdAt")))
            }

            get("/groups/{id}/suggested-members") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val id = requireNotNull(call.parameters["id"])
                val actor = call.authenticatedUserId
                val group = call.requireGroupMember(id) ?: return@get
                val page = call.pageQuery("suggested-members:$actor:$id")
                val rows = MongoClientProvider.groupDirectory.aggregate<Document>(listOf(
                    Document("$" + "match", Filters.eq("members", actor)),
                    Document("$" + "unwind", "$" + "members"),
                    Document("$" + "match", Filters.nin("members", group.members + actor)),
                    Document("$" + "group", Document("_id", "$" + "members")),
                    Document("$" + "lookup", Document("from", "userDocument").append("localField", "_id")
                        .append("foreignField", "_id").append("as", "profile")),
                    Document("$" + "unwind", "$" + "profile"),
                    Document("$" + "replaceRoot", Document("newRoot", "$" + "profile")),
                    Document("$" + "match", page.filter("createdAt")),
                    Document("$" + "sort", Document("createdAt", -1).append("_id", -1)),
                    Document("$" + "limit", page.limit + 1),
                    Document("$" + "project", Document("_id", 1).append("name", 1).append("uniqueId", 1)
                        .append("profilePicUrl", 1).append("createdAt", 1).append("updatedAt", 1))
                )).allowDiskUse(true).maxTime(5, TimeUnit.SECONDS).toList()
                call.respond(CursorPage(rows.take(page.limit).map { it.memberResponse() }, page.next(rows, "createdAt")))
            }

            get("/entries/page") {
                val id = call.request.queryParameters["groupId"]
                    ?: throw RequestFailure(HttpStatusCode.BadRequest, "groupId is required")
                val actor = call.authenticatedUserId
                call.requireGroupMember(id) ?: return@get
                val kind = call.request.queryParameters["kind"] ?: "all"
                val other = call.request.queryParameters["otherUserId"]
                if (kind !in setOf("all", "debts", "payments") || other != null && other.length !in 1..128) {
                    throw RequestFailure(HttpStatusCode.BadRequest, "Invalid history filter")
                }
                val page = call.pageQuery("entries:$actor:$id:$kind:${other.orEmpty()}")
                val group = MongoClientProvider.groupDirectory.findOne(Filters.eq("_id", id))
                    ?: throw RequestFailure(HttpStatusCode.NotFound, "Hisab not found")
                val revision = group.number("ledgerRevision")
                if (page.position?.revision != null && page.position.revision != revision) {
                    throw RequestFailure(HttpStatusCode.Conflict, "The Hisab changed. Refresh to continue")
                }
                val filters = mutableListOf(Filters.eq("groupId", id), page.filter("timestamp"))
                if (kind == "payments") filters += Filters.eq("type", "REPAYMENT")
                if (kind == "debts") filters += Filters.ne("type", "REPAYMENT")
                if (other != null) filters += Filters.or(
                    Filters.and(Filters.eq("debtorId", actor), Filters.eq("creditorId", other)),
                    Filters.and(Filters.eq("debtorId", other), Filters.eq("creditorId", actor)))
                val rows = MongoClientProvider.entryDirectory.find(Filters.and(filters))
                    .sort(Sorts.descending("timestamp", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                val current = MongoClientProvider.groupDirectory.findOne(Filters.eq("_id", id))
                if (current == null || actor !in current.strings("members") || current.number("ledgerRevision") != revision) {
                    throw RequestFailure(HttpStatusCode.Conflict, "The Hisab changed. Refresh to continue")
                }
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(CursorPage(rows.take(page.limit).map { it.entry() }, page.next(rows, "timestamp", revision), revision))
            }

            get("/requests/page") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val actor = call.authenticatedUserId
                val page = call.pageQuery("requests:$actor:pending")
                val rows = MongoClientProvider.requestDirectory.find(Filters.and(Filters.eq("receiverId", actor),
                    Filters.eq("status", "pending"), page.filter("createdAt")))
                    .sort(Sorts.descending("createdAt", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                val items = rows.take(page.limit).map {
                    val picture = ProfileImages.publicUrl(it.getString("profilePictureUrl") ?: it.getString("senderProfilePicUrl").orEmpty())
                    HisabRequestDocument(it.getString("_id"), it.getString("senderId"), it.getString("senderName"),
                        senderProfilePicUrl = picture, profilePictureUrl = picture, receiverId = actor,
                        groupId = it.getString("groupId"), groupName = it.getString("groupName"),
                        status = it.getString("status"), invitationMessage = it.getString("invitationMessage"), createdAt = it.number("createdAt"))
                }
                call.respond(CursorPage(items, page.next(rows, "createdAt")))
            }
        }
    }
}
