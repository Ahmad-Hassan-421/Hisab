package com.hisab.server.database

import com.hisab.server.models.*
import com.mongodb.MongoException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Filters
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
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
    val groupDirectory: CoroutineCollection<Document> = database.getCollection("hisabGroupDocument")
    val entryDirectory: CoroutineCollection<Document> = database.getCollection("hisabEntryDocument")
    val requestDirectory: CoroutineCollection<Document> = database.getCollection("hisabRequestDocument")
    val requests: CoroutineCollection<HisabRequestDocument> = database.getCollection()
    val inviteMessages: CoroutineCollection<InviteMessageDocument> = database.getCollection("invite_message_document")
    val recoveryLimits: CoroutineCollection<Document> = database.getCollection("account_recovery_limits")
    val archives: CoroutineCollection<Document> = database.getCollection("hard_copy_of_user_data")
    val archiveRecords: CoroutineCollection<Document> = database.getCollection("archived_hisab_records")
    val recovered: CoroutineCollection<Document> = database.getCollection("recovered_hisabs")
    val recoveredRecords: CoroutineCollection<Document> = database.getCollection("recovered_hisab_records")
    val recoveryJobs: CoroutineCollection<Document> = database.getCollection("hisab_recovery_jobs")
    val personalFiles: CoroutineCollection<Document> = database.getCollection("personal_files")
    val fileCleanup: CoroutineCollection<Document> = database.getCollection("file_cleanup")
    val devices: CoroutineCollection<Document> = database.getCollection("notification_devices")
    val notificationOutbox: CoroutineCollection<Document> = database.getCollection("notification_outbox")
    val notificationPreferences: CoroutineCollection<Document> = database.getCollection("notification_preferences")
    val notificationDeliveries: CoroutineCollection<Document> = database.getCollection("notification_deliveries")
    val releaseNotificationJobs: CoroutineCollection<Document> = database.getCollection("release_notification_jobs")
    val mediaAssets: CoroutineCollection<Document> = database.getCollection("media_assets")
    val mediaReferences: CoroutineCollection<Document> = database.getCollection("media_references")

    suspend fun ping() {
        checkNotNull(database.runCommand<Document>(Document("ping", 1)))
    }

    suspend fun seedInviteMessages() {
        val stream = MongoClientProvider::class.java.getResourceAsStream("/invite-messages.json")
            ?: error("invite-messages.json resource is missing")
        val raw = stream.bufferedReader().readText()
        val entries = Json.decodeFromString<List<InviteMessageDocument>>(raw)
        // One round trip instead of a lookup (and sometimes an insert) per template.
        // Existing templates may have been edited or deactivated by an administrator.
        val inserts = entries.map { entry ->
            UpdateOneModel<InviteMessageDocument>(
                Filters.eq("key", entry.key),
                Updates.combine(Updates.setOnInsert("_id", entry.id),
                    Updates.setOnInsert("key", entry.key), Updates.setOnInsert("text", entry.text),
                    Updates.setOnInsert("active", entry.active), Updates.setOnInsert("language", entry.language)),
                UpdateOptions().upsert(true)
            )
        }
        if (inserts.isNotEmpty()) inviteMessages.bulkWrite(inserts, BulkWriteOptions().ordered(false))
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
        groups.ensureIndex(Indexes.compoundIndex(Indexes.ascending("members"), Indexes.descending("createdAt", "_id")))
        entries.ensureIndex(Indexes.ascending("groupId"))
        entries.ensureIndex(Indexes.ascending("debtorId", "groupId"))
        entries.ensureIndex(Indexes.ascending("creditorId", "groupId"))
        entries.ensureIndex(Indexes.compoundIndex(Indexes.ascending("groupId"), Indexes.descending("timestamp", "_id")))
        entries.ensureIndex(Indexes.compoundIndex(Indexes.ascending("groupId", "type"), Indexes.descending("timestamp", "_id")))
        entries.ensureIndex(Indexes.compoundIndex(Indexes.ascending("groupId", "debtorId", "creditorId"), Indexes.descending("timestamp", "_id")))
        requests.ensureIndex(Indexes.ascending("receiverId", "status"))
        requests.ensureIndex(Indexes.ascending("receiverId", "status", "senderId"))
        requests.ensureIndex(Indexes.compoundIndex(Indexes.ascending("receiverId", "status"), Indexes.descending("createdAt", "_id")))
        requests.ensureIndex(Indexes.ascending("groupId", "senderId", "receiverId", "status"))
        inviteMessages.ensureIndex(Indexes.ascending("key"), IndexOptions().unique(true))
        recoveryLimits.ensureIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        for (collection in listOf(archives, archiveRecords, recoveryJobs)) {
            collection.ensureIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        }
        for (collection in listOf(archives, recovered)) {
            collection.ensureIndex(Indexes.compoundIndex(Indexes.ascending("ownerId"), Indexes.descending("createdAt", "_id")))
        }
        for (collection in listOf(archiveRecords, recoveredRecords)) {
            collection.ensureIndex(Indexes.ascending("snapshotId", "kind", "_id"))
            collection.ensureIndex(Indexes.compoundIndex(Indexes.ascending("snapshotId", "kind"), Indexes.descending("timestamp", "_id")))
            collection.ensureIndex(Indexes.ascending("ownerId"))
        }
        recoveryJobs.ensureIndex(Indexes.ascending("ownerId"))
        recoveryJobs.ensureIndex(Indexes.ascending("status", "dueAt"))
        personalFiles.ensureIndex(Indexes.ascending("ownerId"))
        devices.ensureIndex(Indexes.ascending("ownerId", "enabled"))
        devices.ensureIndex(Indexes.ascending("enabled", "ownerId", "versionCode"))
        devices.ensureIndex(Indexes.ascending("tokenDigest"), IndexOptions().unique(true).partialFilterExpression(Filters.type("tokenDigest", org.bson.BsonType.STRING)))
        for (collection in listOf(devices, notificationOutbox, notificationDeliveries)) {
            collection.ensureIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        }
        notificationOutbox.ensureIndex(Indexes.ascending("status", "dueAt", "leaseUntil"))
        notificationOutbox.ensureIndex(Indexes.ascending("ownerId"))
        notificationDeliveries.ensureIndex(Indexes.ascending("ownerId"))
        releaseNotificationJobs.ensureIndex(Indexes.ascending("status", "leaseUntil"))
        mediaAssets.ensureIndex(Indexes.ascending("kind", "subjectId"))
        mediaReferences.ensureIndex(Indexes.ascending("assetId", "kind", "ownerId"))
        mediaReferences.ensureIndex(Indexes.ascending("ownerId"))
        mediaReferences.ensureIndex(Indexes.ascending("subjectId", "kind"))
        mediaReferences.ensureIndex(Indexes.ascending("expiresAt"), IndexOptions().expireAfter(0, TimeUnit.SECONDS))
        privacyRequests.ensureIndex(Indexes.ascending("ownerId", "type"),
            IndexOptions().unique(true).partialFilterExpression(Filters.eq("status", "pending")))
    }

    suspend fun <T> transaction(operation: suspend (ClientSession) -> T): T =
        client.startSession().use { session ->
            repeat(8) { attempt ->
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
                    if (attempt == 7 || error !is MongoException ||
                        !error.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) ||
                        error.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                    ) throw error
                    // Large archival transactions need room to finish before a competing retry.
                    delay((25L shl attempt).coerceAtMost(1_000L) + kotlin.random.Random.nextLong(25))
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
