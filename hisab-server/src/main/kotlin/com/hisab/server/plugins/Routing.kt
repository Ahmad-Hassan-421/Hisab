package com.hisab.server.plugins

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.accounts.authRoutes
import com.hisab.server.features.accounts.userRoutes
import com.hisab.server.features.groups.hisabGroupRoutes
import com.hisab.server.features.ledger.hisabEntryRoutes
import com.hisab.server.features.invitations.requestRoutes
import com.hisab.server.features.media.storageRoutes
import com.hisab.server.features.realtime.webSocketRoutes
import com.hisab.server.features.releases.releaseRoutes
import com.mongodb.MongoException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

fun Application.configureRouting(uploadDirectory: File) {
    routing {
        get("/healthz") {
            val ready = try {
                withTimeoutOrNull(2_000) { MongoClientProvider.ping(); true } == true
            } catch (_: MongoException) {
                false
            }
            if (ready) call.respondText("ok") else call.respondText("unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
        authRoutes()
        userRoutes()
        hisabGroupRoutes()
        hisabEntryRoutes()
        requestRoutes()
        storageRoutes(uploadDirectory)
        webSocketRoutes()
        releaseRoutes()
        
        staticFiles("/static/uploads", uploadDirectory)
        staticResources("/static", "static")
    }
}
