package com.hisab.server.features.ledger

import com.hisab.server.features.groups.requireGroupMember
import com.hisab.server.features.groups.lockGroupForMember

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.RequestFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Serializable
data class CreateEntryRequest(
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: Double,
    val description: String,
    val clientEntryId: String? = null
)
@Serializable data class CreateBatchEntryRequest(val entries: List<CreateEntryRequest>)

private fun CreateEntryRequest.isValidFor(group: HisabGroupDocument, actor: String): Boolean =
    groupId == group.id && debtorId != creditorId &&
        (debtorId == actor || creditorId == actor) &&
        debtorId in group.members && creditorId in group.members &&
        amount.isFinite() && amount > 0 && amount <= 1_000_000_000 && description.length <= 500 &&
        (clientEntryId == null || BigDecimal.valueOf(amount).stripTrailingZeros().scale() <= 3)

private fun CreateEntryRequest.toDocument(actor: String): HisabEntryDocument {
    val id = clientEntryId?.let { provided ->
        val parsed = runCatching { UUID.fromString(provided) }.getOrNull()
        if (parsed == null || !parsed.toString().equals(provided, ignoreCase = true)) {
            throw RequestFailure(HttpStatusCode.BadRequest, "Invalid client entry ID")
        }
        UUID.nameUUIDFromBytes("$actor:$parsed".toByteArray(Charsets.UTF_8))
    } ?: UUID.randomUUID()
    return HisabEntryDocument(id.toString(), groupId, debtorId, creditorId, amount, description)
}

private suspend fun persistEntries(inputs: List<CreateEntryRequest>, actor: String): List<HisabEntryDocument> {
    val documents = inputs.map { it.toDocument(actor) }
    if (documents.map { it.id }.distinct().size != documents.size) {
        throw RequestFailure(HttpStatusCode.BadRequest, "Duplicate client entry ID in batch")
    }
    val entries = MongoClientProvider.entries
    return MongoClientProvider.transaction { session ->
        val group = lockGroupForMember(session, documents.first().groupId, actor)
        if (inputs.any { !it.isValidFor(group, actor) }) {
            throw RequestFailure(HttpStatusCode.BadRequest, "Invalid ledger entry")
        }
        val previous = documents.map { entries.findOne(session, HisabEntryDocument::id eq it.id) }
        if (previous.all { it == null }) {
            entries.insertMany(session, documents)
            documents
        } else {
            if (!documents.zip(previous).all { (requested, existing) ->
                    existing != null && requested.copy(timestamp = existing.timestamp) == existing
                }
            ) throw RequestFailure(HttpStatusCode.Conflict, "Client entry ID was already used")
            previous.filterNotNull()
        }
    }
}

private fun List<HisabEntryDocument>.isSettled(): Boolean {
    val balances = mutableMapOf<Pair<String, String>, BigDecimal>()
    for (entry in this) {
        if (!entry.amount.isFinite()) return false
        val ascending = entry.debtorId <= entry.creditorId
        val pair = if (ascending) entry.debtorId to entry.creditorId else entry.creditorId to entry.debtorId
        val amount = BigDecimal.valueOf(entry.amount)
        balances[pair] = balances.getOrDefault(pair, BigDecimal.ZERO) + if (ascending) amount else -amount
    }
    return balances.values.all { it.setScale(3, RoundingMode.HALF_UP).signum() == 0 }
}

fun Route.hisabEntryRoutes() {
    authenticate("auth-jwt") {
        route("/entries") {
            get {
                val groupId = call.request.queryParameters["groupId"]
                if (groupId != null) {
                    call.requireGroupMember(groupId) ?: return@get
                    val entries = MongoClientProvider.entries.find(HisabEntryDocument::groupId eq groupId).toList()
                    call.respond(entries)
                } else {
                    call.respond(HttpStatusCode.BadRequest, "groupId is required")
                }
            }
            
            post {
                val req = call.receive<CreateEntryRequest>()
                val entry = persistEntries(listOf(req), call.authenticatedUserId).single()
                call.respond(HttpStatusCode.Created, entry)
            }

            post("/batch") {
                val req = call.receive<CreateBatchEntryRequest>()
                if (req.entries.size !in 1..100) return@post call.respond(HttpStatusCode.BadRequest, "Invalid batch size")
                call.respond(HttpStatusCode.Created, persistEntries(req.entries, call.authenticatedUserId))
            }
            
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val actor = call.authenticatedUserId
                val entries = MongoClientProvider.entries
                MongoClientProvider.transaction { session ->
                    val entry = entries.findOne(session, HisabEntryDocument::id eq id)
                        ?: throw RequestFailure(HttpStatusCode.NotFound, "Entry not found")
                    if (actor != entry.debtorId && actor != entry.creditorId) {
                        throw RequestFailure(HttpStatusCode.Forbidden, "Entry participant required")
                    }
                    lockGroupForMember(session, entry.groupId, actor)
                    entries.deleteOne(session, HisabEntryDocument::id eq id)
                }
                call.respond(HttpStatusCode.OK)
            }
            delete("/group/{groupId}") {
                val groupId = call.parameters["groupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val actor = call.authenticatedUserId
                val entries = MongoClientProvider.entries
                MongoClientProvider.transaction { session ->
                    lockGroupForMember(session, groupId, actor)
                    val filter = HisabEntryDocument::groupId eq groupId
                    if (!entries.find(session, filter).toList().isSettled()) {
                        throw RequestFailure(HttpStatusCode.Conflict, "Settle the ledger before clearing records")
                    }
                    entries.deleteMany(session, filter)
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
