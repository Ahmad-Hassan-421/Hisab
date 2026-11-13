package com.hisab.server.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.codecs.pojo.annotations.BsonId
import java.util.UUID

@Serializable
data class UserDocument(
    @BsonId val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val passwordHash: String,
    val uniqueId: String,
    val profilePicUrl: String,
    val themePreference: String = "system",
    val role: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class HisabEntryDocument(
    @BsonId val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val debtorId: String,   // Person who owes money
    val creditorId: String, // Person who is owed money
    val amount: Double,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "EXPENSE"
)

@Serializable
data class HisabGroupDocument(
    @BsonId val id: String = UUID.randomUUID().toString(),
    val name: String,
    val members: List<String>,
    val exMembers: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    @Transient val ledgerRevision: Long = 0
)

@Serializable
data class HisabRequestDocument(
    @BsonId val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderEmail: String = "",
    val senderProfilePicUrl: String = "",
    val profilePictureUrl: String? = null,
    val receiverId: String,
    val groupId: String,
    val groupName: String,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis()
)
