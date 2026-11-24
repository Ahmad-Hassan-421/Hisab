package com.hisab.server.plugins

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.accounts.authRoutes
import com.hisab.server.features.accounts.adminRoutes
import com.hisab.server.features.accounts.privacyRoutes
import com.hisab.server.features.accounts.passwordRecoveryRoutes
import com.hisab.server.features.accounts.userRoutes
import com.hisab.server.features.groups.hisabGroupRoutes
import com.hisab.server.features.ledger.hisabEntryRoutes
import com.hisab.server.features.invitations.requestRoutes
import com.hisab.server.features.media.storageRoutes
import com.hisab.server.features.realtime.webSocketRoutes
import com.hisab.server.features.releases.releaseRoutes
import com.hisab.server.features.health.healthRoutes
import com.hisab.server.features.paging.pagedRoutes
import com.hisab.server.features.privacy.archiveRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting(uploadDirectory: File) {
    routing {
        healthRoutes()
        pagedRoutes()
        archiveRoutes()
        authRoutes()
        passwordRecoveryRoutes()
        adminRoutes()
        privacyRoutes()
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
