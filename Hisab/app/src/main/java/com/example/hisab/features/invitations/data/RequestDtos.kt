package com.example.hisab.features.invitations.data

import com.google.gson.annotations.SerializedName

data class HisabRequestDto(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderEmail: String? = null,
    val senderProfilePicUrl: String? = null,
    @SerializedName("profilePictureUrl") val profilePictureUrl: String? = null,
    val receiverId: String,
    val groupId: String,
    val groupName: String,
    val status: String?,
    val createdAt: Long = 0L
)

data class SendRequestRequest(
    val receiverUniqueId: String,
    val groupId: String
)

data class UpdateRequestRequest(
    val status: String
)
