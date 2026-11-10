package com.hisab.server.features.realtime

import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WsMessage(val type: String, val payload: String = "")

object WebSocketManager {
    private val connections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    fun addSession(userId: String, session: WebSocketSession) {
        connections.compute(userId) { _, existing ->
            (existing ?: ConcurrentHashMap.newKeySet()).apply { add(session) }
        }
    }

    fun removeSession(userId: String, session: WebSocketSession) {
        connections.computeIfPresent(userId) { _, userSessions ->
            userSessions.remove(session)
            userSessions.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun notifyUser(userId: String, type: String, payload: String = "") {
        val message = Json.encodeToString(WsMessage(type, payload))
        connections[userId]?.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                removeSession(userId, session)
            }
        }
    }
}
