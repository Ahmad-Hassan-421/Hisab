package com.example.hisab.util

import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.notifications.PushPublisher
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface InvitationAlerts { fun showInviteNotification(request: HisabRequest) }

@Singleton
class InviteNotificationHelper @Inject constructor(private val publisher: PushPublisher, private val tokens: TokenManager) : InvitationAlerts {
    override fun showInviteNotification(request: HisabRequest) {
        val owner = tokens.getUserId() ?: return
        val now = System.currentTimeMillis()
        val eventId = UUID.nameUUIDFromBytes("INVITATION:$owner:${request.id}".toByteArray(Charsets.UTF_8)).toString()
        publisher.receive(mapOf("eventId" to eventId, "ownerId" to owner, "kind" to "INVITATION",
            "title" to "New Hisab invite", "body" to "${request.senderName} invited you to ${request.groupName}",
            "groupId" to request.groupId, "recordId" to "", "sourceId" to request.id,
            "expiresAt" to (now + 7L * 86_400_000).toString()))
    }
}
