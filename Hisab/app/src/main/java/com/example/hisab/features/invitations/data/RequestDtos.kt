package com.example.hisab.features.invitations.data

data class HisabRequestDto(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderProfilePicUrl: String?,
    val receiverId: String,
    val groupId: String,
    val groupName: String,
    val status: String?,
    val createdAt: Long
)

data class SendRequestRequest(
    val receiverUniqueId: String,
    val groupId: String
)

data class UpdateRequestRequest(
    val status: String
)
