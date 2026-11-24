package com.hisab.server.features.privacy

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.accounts.allowAttempt
import com.hisab.server.features.accounts.toResponse
import com.hisab.server.features.paging.CursorPage
import com.hisab.server.features.paging.pageQuery
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.authenticatedUserId
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.bson.Document
import java.util.Date
import java.util.concurrent.TimeUnit
import com.hisab.server.features.realtime.WebSocketManager

@Serializable internal data class ArchiveCard(val id: String, val name: String, val deletedAt: Long,
    val expiresAt: Long?, val recoveredAt: Long?, val recordCount: Long, val memberCount: Long, val status: String)
@Serializable internal data class RecoveryReceipt(val id: String, val name: String, val status: String, val dueAt: Long)
@Serializable private data class DeleteAccountInput(val accountId: String, val password: String)
@Serializable internal data class HistoricalMember(val id: String, val name: String, val profilePicUrl: String)
@Serializable private data class RecoveredEntry(val id: String, val groupId: String, val debtorId: String, val creditorId: String,
    val amount: Double, val description: String, val timestamp: Long, val type: String, val debtorName: String, val creditorName: String)

private fun Document.number(key: String) = (get(key) as? Number)?.toLong() ?: 0L
private fun Document.card(status: String? = null) = ArchiveCard(getString("_id"), getString("name"), number("deletedAt"),
    getDate("expiresAt")?.time, (get("recoveredAt") as? Number)?.toLong(), number("recordCount"), number("memberCount"),
    status ?: getString("status"))

private val PrivateDataResponses = createRouteScopedPlugin("PrivateDataResponses") {
    onCallRespond { call, _ -> call.response.header(HttpHeaders.CacheControl, "no-store") }
}

fun Route.archiveRoutes() {
    authenticate("auth-jwt") {
        route("/account/data") {
            // Account rights remain available even when an updated policy has not been accepted.
            install(PrivateDataResponses)
            get("/accounts") {
                val self = DB.users.findOne(eq("_id", call.authenticatedUserId))
                    ?: throw RequestFailure(HttpStatusCode.Unauthorized, "Sign-in required")
                // Hisab has one account per session; admin status never grants deletion of other accounts here.
                call.respond(listOf(self.toResponse()))
            }
            post("/delete-account") {
                val actor = call.authenticatedUserId
                if (!allowAttempt("permanent-account-deletion", actor, 5)) throw RequestFailure(HttpStatusCode.TooManyRequests, "Try again later")
                val input = call.receive<DeleteAccountInput>()
                permanentlyDeleteAccount(actor, input.password, input.accountId)
                withTimeoutOrNull(500) { WebSocketManager.closeOlderSessions(actor, Long.MAX_VALUE) }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/archives") {
                val actor = call.authenticatedUserId
                val page = call.pageQuery("archives:$actor")
                val rows = DB.archives.find(and(eq("ownerId", actor), gt("expiresAt", Date()), page.filter("createdAt")))
                    .sort(Sorts.descending("createdAt", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                val jobs = DB.recoveryJobs.find(and(eq("ownerId", actor), `in`("_id", rows.map { it.getString("_id") })))
                    .limit(page.limit + 1).toList().associateBy { it.getString("_id") }
                call.respond(CursorPage(rows.take(page.limit).map { it.card(jobs[it.getString("_id")]?.getString("status")) }, page.next(rows, "createdAt")))
            }
            post("/archives/{id}/recover") {
                val actor = call.authenticatedUserId
                if (!allowAttempt("hisab-recovery", actor, 30)) throw RequestFailure(HttpStatusCode.TooManyRequests, "Try again later")
                val job = requestRecovery(actor, requireNotNull(call.parameters["id"]))
                call.respond(HttpStatusCode.Accepted, RecoveryReceipt(job.getString("_id"), job.getString("name"), job.getString("status"), job.number("dueAt")))
            }
            get("/recovered") {
                val actor = call.authenticatedUserId
                val page = call.pageQuery("recovered:$actor")
                val rows = DB.recovered.find(and(eq("ownerId", actor), page.filter("createdAt")))
                    .sort(Sorts.descending("createdAt", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                call.respond(CursorPage(rows.take(page.limit).map { it.card() }, page.next(rows, "createdAt")))
            }
            get("/recovered/{id}") {
                val snapshot = DB.recovered.findOne(and(eq("_id", requireNotNull(call.parameters["id"])), eq("ownerId", call.authenticatedUserId)))
                    ?: throw RequestFailure(HttpStatusCode.NotFound, "Recovered Hisab not found")
                call.respond(snapshot.card())
            }
            get("/recovered/{id}/entries") {
                val id = requireNotNull(call.parameters["id"])
                val actor = call.authenticatedUserId
                if (DB.recovered.findOne(and(eq("_id", id), eq("ownerId", actor))) == null) throw RequestFailure(HttpStatusCode.NotFound, "Recovered Hisab not found")
                val kind = call.request.queryParameters["kind"] ?: "all"
                if (kind !in setOf("all", "debts", "payments")) throw RequestFailure(HttpStatusCode.BadRequest, "Invalid history filter")
                val page = call.pageQuery("recovered-entries:$actor:$id:$kind")
                val typeFilter = when (kind) { "debts" -> ne("type", "REPAYMENT"); "payments" -> eq("type", "REPAYMENT"); else -> Document() }
                val rows = DB.recoveredRecords.find(and(eq("snapshotId", id), eq("ownerId", actor), eq("kind", "entry"), typeFilter, page.filter("timestamp")))
                    .sort(Sorts.descending("timestamp", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                val parties = rows.take(page.limit).flatMap { listOf(it.getString("debtorId"), it.getString("creditorId")) }.distinct()
                val names = DB.recoveredRecords.find(and(eq("snapshotId", id), eq("ownerId", actor), eq("kind", "member"), `in`("originalId", parties)))
                    .limit(200).toList().associate { it.getString("originalId") to it.getString("name") }
                call.respond(CursorPage(rows.take(page.limit).map { RecoveredEntry(it.getString("originalId"), id,
                    it.getString("debtorId"), it.getString("creditorId"), (it["amount"] as Number).toDouble(),
                    it.getString("description").orEmpty(), it.number("timestamp"), it.getString("type") ?: "EXPENSE",
                    names[it.getString("debtorId")] ?: "Former member", names[it.getString("creditorId")] ?: "Former member") }, page.next(rows, "timestamp")))
            }
            get("/recovered/{id}/members") {
                val id = requireNotNull(call.parameters["id"])
                val actor = call.authenticatedUserId
                if (DB.recovered.findOne(and(eq("_id", id), eq("ownerId", actor))) == null) throw RequestFailure(HttpStatusCode.NotFound, "Recovered Hisab not found")
                val page = call.pageQuery("recovered-members:$actor:$id")
                val rows = DB.recoveredRecords.find(and(eq("snapshotId", id), eq("ownerId", actor), eq("kind", "member"), page.filter("timestamp")))
                    .sort(Sorts.descending("timestamp", "_id")).limit(page.limit + 1).maxTime(3, TimeUnit.SECONDS).toList()
                call.respond(CursorPage(rows.take(page.limit).map { HistoricalMember(it.getString("originalId"), it.getString("name"),
                    it.getString("profilePicUrl").orEmpty()) }, page.next(rows, "timestamp")))
            }
            delete("/recovered/{id}") {
                deleteRecovered(call.authenticatedUserId, requireNotNull(call.parameters["id"]))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
