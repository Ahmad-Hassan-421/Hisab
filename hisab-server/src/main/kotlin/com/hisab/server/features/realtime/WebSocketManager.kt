package com.hisab.server.features.realtime

import com.hisab.server.database.MongoClientProvider
import com.hisab.server.features.accounts.isActiveAccount
import com.hisab.server.models.UserDocument
import com.mongodb.MongoException
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.litote.kmongo.eq
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WsMessage(val type: String, val payload: String = "")

object WebSocketManager {
    private val log = LoggerFactory.getLogger(WebSocketManager::class.java)
    private val connections = ConcurrentHashMap<String, MutableMap<WebSocketSession, Long>>()

    fun addSession(userId: String, session: WebSocketSession, sessionVersion: Long) {
        connections.compute(userId) { _, existing ->
            (existing ?: ConcurrentHashMap()).apply { put(session, sessionVersion) }
        }
    }

    fun removeSession(userId: String, session: WebSocketSession) {
        connections.computeIfPresent(userId) { _, userSessions ->
            userSessions.remove(session)
            userSessions.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun closeOlderSessions(userId: String, sessionVersion: Long) {
        connections[userId]?.forEach { (session, version) ->
            if (version < sessionVersion) closeRevokedSession(userId, session)
        }
    }

    private suspend fun closeRevokedSession(userId: String, session: WebSocketSession) {
        removeSession(userId, session)
        try {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Sign-in required"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.warn("Realtime session close failed ({})", error.javaClass.simpleName)
        }
    }

    suspend fun notifyUser(userId: String, type: String, payload: String = "") {
        val sessions = connections[userId] ?: return
        val version = try {
            MongoClientProvider.users.findOne(UserDocument::id eq userId)
                ?.takeIf { isActiveAccount(it.accountStatus) }?.sessionVersion
        } catch (error: MongoException) {
            log.warn("Realtime session check unavailable ({})", error.javaClass.simpleName)
            return
        }
        val message = Json.encodeToString(WsMessage(type, payload))
        sessions.forEach { (session, sessionVersion) ->
            if (sessionVersion != version) {
                closeRevokedSession(userId, session)
                return@forEach
            }
            try {
                session.send(Frame.Text(message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Realtime notification failed ({})", e.javaClass.simpleName)
                removeSession(userId, session)
            }
        }
    }
}
