package com.hisab.server.database

import com.hisab.server.models.*
import com.mongodb.MongoException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import com.mongodb.client.model.Updates
import org.litote.kmongo.reactivestreams.KMongo
import java.util.concurrent.TimeUnit

object MongoClientProvider {
    private val connectionString = requireNotNull(System.getenv("MONGO_URI")?.takeIf { it.isNotBlank() }) {
        "MONGO_URI must be set"
    }
    private val client = KMongo.createClient(connectionString).coroutine
    private val database = client.getDatabase(System.getenv("MONGO_DATABASE")?.takeIf { it.isNotBlank() } ?: "hisab_db")

    val users: CoroutineCollection<UserDocument> = database.getCollection()
    val accountDirectory: CoroutineCollection<Document> = database.getCollection("userDocument")
    val adminAudit: CoroutineCollection<Document> = database.getCollection("admin_audit_log")
    val privacyRequests: CoroutineCollection<Document> = database.getCollection("privacy_data_requests")
    val entries: CoroutineCollection<HisabEntryDocument> = database.getCollection()
    val groups: CoroutineCollection<HisabGroupDocument> = database.getCollection()
    val requests: CoroutineCollection<HisabRequestDocument> = database.getCollection()
    val inviteMessages: CoroutineCollection<InviteMessageDocument> = database.getCollection("invite_message_document")
    val recoveryLimits: CoroutineCollection<Document> = database.getCollection("account_recovery_limits")

    suspend fun ping() {
        checkNotNull(database.runCommand<Document>(Document("ping", 1)))
    }

    suspend fun seedInviteMessages() {
        val stream = MongoClientProvider::class.java.getResourceAsStream("/invite-messages.json")
            ?: error("invite-messages.json resource is missing")
        val raw = stream.bufferedReader().readText()
        val entries = Json.decodeFromString<List<InviteMessageDocument>>(raw)
        entries.forEach { entry ->
            val existing = inviteMessages.findOne(InviteMessageDocument::key eq entry.key)
            if (existing == null) {
                inviteMessages.insertOne(entry)
            }
        }
    }

    suspend fun migratePrivacyAcknowledgment(policyVersion: String, acknowledgmentVersion: String) {
        val now = System.currentTimeMillis()
        users.updateMany(
            Filters.or(Filters.exists("privacyPolicyAcknowledgmentVersion", false), Filters.eq("privacyPolicyAcknowledgmentVersion", null)),
            Updates.combine(
                Updates.set("privacyPolicyVersion", policyVersion),
                Updates.set("privacyPolicyAcknowledgmentVersion", acknowledgmentVersion),
                Updates.set("privacyPolicyAcceptedAt", now)
            )
        )
    }

    suspend fun initialize() {
        ping()
        val topology = checkNotNull(database.runCommand<Document>(Document("hello", 1)))
        check(topology.containsKey("setName") || topology["msg"] == "isdbgrid") {
            "MongoDB must be a replica set or sharded cluster for ledger transactions"
        }
        users.ensureIndex(Indexes.ascending("email"), IndexOptions().unique(true))
        users.ensureIndex(Indexes.ascending("uniqueId"), IndexOptions().unique(true))
        accountDirectory.ensureIndex(Indexes.descending("createdAt", "_id"))
        accountDirectory.ensureIndex(Indexes.compoundIndex(Indexes.ascending("accountStatus"), Indexes.descending("createdAt", "_id")))
        groups.ensureIndex(Indexes.ascending("members"))
        entries.ensureIndex(Indexes.ascending("groupId"))
        requests.ensureIndex(Indexes.ascending("receiverId", "status"))
        requests.ensureIndex(Indexes.ascending("groupId", "senderId", "receiverId", "status"))
        inviteMessages.ensureIndex(Indexes.ascending("key"), IndexOptions().unique(true))
        recoveryLimits.ensureIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        privacyRequests.ensureIndex(Indexes.ascending("ownerId", "type"),
            IndexOptions().unique(true).partialFilterExpression(Filters.eq("status", "pending")))
    }

    suspend fun <T> transaction(operation: suspend (ClientSession) -> T): T =
        client.startSession().use { session ->
            repeat(3) { attempt ->
                session.startTransaction()
                try {
                    val result = operation(session)
                    commit(session)
                    return@use result
                } catch (error: Throwable) {
                    if (session.hasActiveTransaction()) {
                        withContext(NonCancellable) {
                            runCatching { session.abortTransaction().awaitFirstOrNull() }
                        }
                    }
                    if (attempt == 2 || error !is MongoException ||
                        !error.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) ||
                        error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                    ) throw error
                    delay((attempt + 1) * 10L)
                }
            }
            error("MongoDB transaction exceeded its retry limit")
        }

    private suspend fun commit(session: ClientSession) {
        repeat(3) { attempt ->
            try {
                session.commitTransaction().awaitFirstOrNull()
                return
            } catch (error: MongoException) {
                if (attempt == 2 || !error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    throw error
                }
            }
        }
    }

    fun close() = client.close()
}
