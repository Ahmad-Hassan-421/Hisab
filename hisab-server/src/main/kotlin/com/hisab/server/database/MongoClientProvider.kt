package com.hisab.server.database

import com.hisab.server.models.*
import com.mongodb.MongoException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

object MongoClientProvider {
    private val connectionString = requireNotNull(System.getenv("MONGO_URI")?.takeIf { it.isNotBlank() }) {
        "MONGO_URI must be set"
    }
    private val client = KMongo.createClient(connectionString).coroutine
    private val database = client.getDatabase(System.getenv("MONGO_DATABASE")?.takeIf { it.isNotBlank() } ?: "hisab_db")

    val users: CoroutineCollection<UserDocument> = database.getCollection()
    val entries: CoroutineCollection<HisabEntryDocument> = database.getCollection()
    val groups: CoroutineCollection<HisabGroupDocument> = database.getCollection()
    val requests: CoroutineCollection<HisabRequestDocument> = database.getCollection()

    suspend fun ping() {
        checkNotNull(database.runCommand<Document>(Document("ping", 1)))
    }

    suspend fun initialize() {
        ping()
        val topology = checkNotNull(database.runCommand<Document>(Document("hello", 1)))
        check(topology.containsKey("setName") || topology["msg"] == "isdbgrid") {
            "MongoDB must be a replica set or sharded cluster for ledger transactions"
        }
        users.ensureIndex(Indexes.ascending("email"), IndexOptions().unique(true))
        users.ensureIndex(Indexes.ascending("uniqueId"), IndexOptions().unique(true))
        groups.ensureIndex(Indexes.ascending("members"))
        entries.ensureIndex(Indexes.ascending("groupId"))
        requests.ensureIndex(Indexes.ascending("receiverId", "status"))
        requests.ensureIndex(Indexes.ascending("groupId", "senderId", "receiverId", "status"))
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
