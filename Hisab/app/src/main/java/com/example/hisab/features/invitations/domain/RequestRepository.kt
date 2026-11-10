package com.example.hisab.features.invitations.domain

data class HisabRequest(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePicUrl: String = "",
    val receiverId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val status: String = "pending"
)

interface RequestRepository {
    suspend fun sendRequest(receiverUniqueId: String, groupId: String): HisabRequest
    suspend fun getPendingRequests(receiverId: String): List<HisabRequest>
    suspend fun acceptRequest(requestId: String): HisabRequest
    suspend fun declineRequest(requestId: String): HisabRequest
}
