package com.hisab.server.features.realtime

import com.hisab.server.features.realtime.WebSocketManager
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.webSocketRoutes() {
    authenticate("auth-jwt") {
        webSocket("/ws") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()
            
            if (userId != null) {
                WebSocketManager.addSession(userId, this)
                try {
                    incoming.consumeEach {
                        // Currently we only send server-to-client notifications
                    }
                } finally {
                    WebSocketManager.removeSession(userId, this)
                }
            } else {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            }
        }
    }
}
