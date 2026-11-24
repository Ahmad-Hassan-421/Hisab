package com.hisab.server

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.database.databaseStartupTimeout
import com.hisab.server.database.initializeDatabase
import com.hisab.server.features.accounts.EmailUtil
import com.hisab.server.features.privacy.startPrivacyWorker
import com.hisab.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureSecurity()
    val uploadDirectory = System.getenv("UPLOAD_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: if (System.getenv("HISAB_ENV") == "production") error("UPLOAD_DIR is required in production") else File("uploads")
    check((uploadDirectory.isDirectory || uploadDirectory.mkdirs() || uploadDirectory.isDirectory) &&
        uploadDirectory.canWrite()) { "UPLOAD_DIR must be a writable directory" }

    val startupTimeout = databaseStartupTimeout(System.getenv("MONGO_STARTUP_TIMEOUT_MS"))
    try {
        runBlocking {
            initializeDatabase(startupTimeout,
                initialize = { MongoClientProvider.initialize(); log.info("MongoDB indexes ready") },
                seed = { MongoClientProvider.seedInviteMessages(); log.info("Invitation templates ready") })
        }
    } catch (error: Exception) {
        runCatching { MongoClientProvider.close() }
        log.error("MongoDB initialization failed (${error.javaClass.simpleName})")
        throw IllegalStateException("Check MONGO_URI, the replica set, database permissions and index compatibility")
    }
    environment.monitor.subscribe(ApplicationStopped) {
        EmailUtil.close()
        MongoClientProvider.close()
    }

    configureErrors()
    configureCORS()
    configureSerialization()
    configureSockets()
    configureRouting(uploadDirectory)
    startPrivacyWorker(uploadDirectory)
}
