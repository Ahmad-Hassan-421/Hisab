package com.hisab.server.features.realtime

import com.hisab.server.features.accounts.hisabSessionVersion
import com.hisab.server.plugins.PrivacyAccess
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.webSocketRoutes() {
    authenticate("auth-jwt") {
        route("/ws") {
            install(PrivacyAccess)
            webSocket {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                WebSocketManager.addSession(userId, this, principal.payload.hisabSessionVersion())
                try {
                    incoming.consumeEach {
                        // Currently we only send server-to-client notifications
                    }
                } finally {
                    WebSocketManager.removeSession(userId, this)
                }
            }
        }
    }
}
