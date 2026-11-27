package com.hisab.server.features.ledger

import com.hisab.server.features.groups.requireGroupMember
import com.hisab.server.features.groups.lockGroupForMember

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.database.groupPairBalances
import com.hisab.server.features.notifications.notificationDocument
import org.bson.Document
import com.mongodb.client.model.Filters
import com.hisab.server.models.HisabEntryDocument
import com.hisab.server.models.HisabGroupDocument
import com.hisab.server.plugins.authenticatedUserId
import com.hisab.server.plugins.RequestFailure
import com.hisab.server.plugins.PrivacyAccess
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
        val existingById = entries.find(session, Filters.`in`("_id", documents.map { it.id })).toList().associateBy { it.id }
        val previous = documents.map { existingById[it.id] }
        if (previous.all { it == null }) {
            if (documents.any { it.type == REPAYMENT }) {
                val balances = groupPairBalances(group.id, session).associate {
                    (it.firstId to it.secondId) to it.amount.toBigDecimal()
                }.toMutableMap()
                for (entry in documents) {
                    val pair = entry.ledgerPair()
                    val balance = balances.getOrDefault(pair, BigDecimal.ZERO)
                    val amount = BigDecimal.valueOf(entry.amount)
                    val owed = if (entry.debtorId == pair.first) -balance else balance
                    if (entry.type == REPAYMENT && amount > owed) {
                        throw RequestFailure(HttpStatusCode.Conflict, "Repayment exceeds the outstanding balance")
                    }
                    balances[pair] = balance + if (entry.debtorId == pair.first) amount else -amount
                }
            }
            entries.insertMany(session, documents)
            val finalBalances = if (documents.any { it.type == REPAYMENT }) groupPairBalances(group.id, session).associate {
                (it.firstId to it.secondId) to it.amount.toBigDecimal()
            } else emptyMap()
            val parties = (documents.flatMap { listOf(it.debtorId, it.creditorId) } + actor).distinct()
            val names = MongoClientProvider.accountDirectory.find(session, Filters.and(Filters.`in`("_id", parties),
                Filters.or(Filters.eq("accountStatus", null), Filters.eq("accountStatus", "active"))))
                .projection(Document("_id", 1).append("name", 1)).toList().associate { it.getString("_id") to it.getString("name") }
            val notifications = mutableListOf<Document>()
            for (entry in documents) {
                val amount = BigDecimal.valueOf(entry.amount).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()
                if (entry.type == REPAYMENT) {
                    val pair = entry.ledgerPair()
                    val net = finalBalances[pair] ?: BigDecimal.ZERO
                    val remaining = (if (entry.creditorId == pair.first) net else -net).max(BigDecimal.ZERO).setScale(3).toPlainString()
                    val recipient = if (entry.creditorId == actor) entry.debtorId else entry.creditorId
                    val body = if (recipient == entry.creditorId) "You paid PKR $amount to ${names[entry.debtorId] ?: "a member"}. Remaining debt: PKR $remaining. Hisab: ${group.name}"
                        else "${names[entry.creditorId] ?: "A member"} paid you PKR $amount. Remaining debt: PKR $remaining. Hisab: ${group.name}"
                    if (recipient in names) notifications.add(notificationDocument(recipient, "PAYMENT", entry.id, "Payment recorded", body, group.id, actor, entry.id))
                } else {
                    val recipient = if (entry.debtorId == actor) entry.creditorId else entry.debtorId
                    val body = "PKR $amount · ${entry.description.ifBlank { "Shared expense" }} · Added by ${names[actor] ?: "a member"} · Hisab: ${group.name}"
                    if (recipient in names) notifications.add(notificationDocument(recipient, "DEBT", entry.id, "New debt added", body, group.id, actor, entry.id))
                }
            }
            if (notifications.isNotEmpty()) MongoClientProvider.notificationOutbox.insertMany(session, notifications)
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

fun Route.hisabEntryRoutes() {
    authenticate("auth-jwt") {
        route("/entries") {
            install(PrivacyAccess)
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
                if (req.entries.size !in 1..1000) return@post call.respondFailure(HttpStatusCode.BadRequest, "Use 1 to 1000 entries per batch")
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
                    if (groupPairBalances(groupId, session).any { it.amount.toBigDecimal().signum() != 0 }) {
                        throw RequestFailure(HttpStatusCode.Conflict, "Settle the ledger before clearing records")
                    }
                    entries.deleteMany(session, filter)
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
