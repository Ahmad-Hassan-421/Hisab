package com.hisab.server.features.ledger

import com.hisab.server.features.groups.requireGroupMember
import com.hisab.server.features.groups.lockGroupForMember

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.respondFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.litote.kmongo.eq
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Serializable
data class CreateEntryRequest(
    val groupId: String,
    val debtorId: String,
    val creditorId: String,
    val amount: JsonPrimitive,
    val description: String,
    val clientEntryId: String? = null,
    val type: String = "EXPENSE"
)
@Serializable data class CreateBatchEntryRequest(val entries: List<CreateEntryRequest>)

private const val REPAYMENT = "REPAYMENT"
private val entryTypes = setOf("EXPENSE", "DIRECT_DEBT", REPAYMENT)
private const val AMOUNT_SCALE = 3
private val maxAmount = BigDecimal("1000000000")

private fun CreateEntryRequest.isValidFor(group: HisabGroupDocument, actor: String): Boolean =
    groupId == group.id && debtorId != creditorId &&
        (debtorId == actor || creditorId == actor) &&
        canRecordEntryFor(debtorId, group) && canRecordEntryFor(creditorId, group) &&
        description.length <= 500

private fun CreateEntryRequest.canRecordEntryFor(userId: String, group: HisabGroupDocument): Boolean =
    userId in group.members || (type == REPAYMENT && userId in group.exMembers)

private fun CreateEntryRequest.toDocument(actor: String): HisabEntryDocument {
    if (type !in entryTypes) throw RequestFailure(HttpStatusCode.BadRequest, "Invalid ledger entry type")
    val decimal = amount.content.toBigDecimalOrNull()
    val requiresLedgerPrecision = clientEntryId != null || type == REPAYMENT
    if (decimal == null || decimal.signum() <= 0 || decimal > maxAmount ||
        (requiresLedgerPrecision && decimal.stripTrailingZeros().scale() > AMOUNT_SCALE) ||
        decimal.toDouble() == 0.0
    ) {
        throw RequestFailure(HttpStatusCode.BadRequest, "Invalid ledger amount")
    }
    val id = clientEntryId?.let { provided ->
        val parsed = runCatching { UUID.fromString(provided) }.getOrNull()
        if (parsed == null || !parsed.toString().equals(provided, ignoreCase = true)) {
            throw RequestFailure(HttpStatusCode.BadRequest, "Invalid client entry ID")
        }
        UUID.nameUUIDFromBytes("$actor:$parsed".toByteArray(Charsets.UTF_8))
    } ?: UUID.randomUUID()
    return HisabEntryDocument(id.toString(), groupId, debtorId, creditorId, decimal.toDouble(), description, type = type)
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
            if (documents.any { it.type == REPAYMENT }) {
                val history = entries.find(session, HisabEntryDocument::groupId eq group.id).toList()
                history.validateRepayments(documents)
            }
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

private fun HisabEntryDocument.ledgerPair(): Pair<String, String> =
    if (debtorId <= creditorId) debtorId to creditorId else creditorId to debtorId

private class PairBalances {
    private val amounts = mutableMapOf<Pair<String, String>, BigDecimal>()
    private val legacyAmounts = mutableMapOf<Pair<String, String>, BigDecimal>()

    fun add(entry: HisabEntryDocument) {
        if (entry.debtorId == entry.creditorId) return
        val pair = entry.ledgerPair()
        val amount = BigDecimal.valueOf(entry.amount)
        val balances = if (amount.stripTrailingZeros().scale() > AMOUNT_SCALE) legacyAmounts else amounts
        val signedAmount = if (entry.debtorId == pair.first) amount else -amount
        balances[pair] = balances.getOrDefault(pair, BigDecimal.ZERO) + signedAmount
    }

    operator fun get(pair: Pair<String, String>): BigDecimal =
        amounts.getOrDefault(pair, BigDecimal.ZERO) +
            legacyAmounts.getOrDefault(pair, BigDecimal.ZERO).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)

    fun isSettled(): Boolean = (amounts.keys + legacyAmounts.keys).all { this[it].signum() == 0 }
}

private fun List<HisabEntryDocument>.balancesByPair(): PairBalances =
    PairBalances().also { balances -> forEach { balances.add(it) } }

private fun List<HisabEntryDocument>.validateRepayments(documents: List<HisabEntryDocument>) {
    if (any { !it.amount.isFinite() }) {
        throw RequestFailure(HttpStatusCode.Conflict, "The ledger contains an invalid amount")
    }
    val balances = balancesByPair()
    for (entry in documents) {
        if (entry.type == REPAYMENT) {
            val pair = entry.ledgerPair()
            val balance = balances[pair]
            val owed = if (entry.debtorId == pair.first) -balance else balance
            if (BigDecimal.valueOf(entry.amount) > owed) {
                throw RequestFailure(HttpStatusCode.Conflict, "Repayment exceeds the outstanding balance")
            }
        }
        balances.add(entry)
    }
}

private fun List<HisabEntryDocument>.isSettled(): Boolean =
    all { it.amount.isFinite() } && balancesByPair().isSettled()

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
                    call.respondFailure(HttpStatusCode.BadRequest, "groupId is required")
                }
            }
            
            post {
                val req = call.receive<CreateEntryRequest>()
                val entry = persistEntries(listOf(req), call.authenticatedUserId).single()
                call.respond(HttpStatusCode.Created, entry)
            }

            post("/batch") {
                val req = call.receive<CreateBatchEntryRequest>()
                if (req.entries.size !in 1..100) return@post call.respondFailure(HttpStatusCode.BadRequest, "Invalid batch size")
                call.respond(HttpStatusCode.Created, persistEntries(req.entries, call.authenticatedUserId))
            }
            
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respondFailure(HttpStatusCode.BadRequest)
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
                val groupId = call.parameters["groupId"] ?: return@delete call.respondFailure(HttpStatusCode.BadRequest)
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
