package com.example.hisab.features.invitations.data

import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.invitations.domain.RequestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiRequestRepository @Inject constructor(
    private val api: HisabApiService
) : RequestRepository {
    override suspend fun sendRequest(receiverUniqueId: String, groupId: String): HisabRequest =
        api.sendRequest(SendRequestRequest(receiverUniqueId, groupId)).toDomain()

    override suspend fun getPendingRequests(receiverId: String): List<HisabRequest> =
        api.getPendingRequests(receiverId).map { it.toDomain() }

    override suspend fun acceptRequest(requestId: String): HisabRequest =
        api.updateRequest(requestId, UpdateRequestRequest("accepted")).toDomain("accepted")

    override suspend fun declineRequest(requestId: String): HisabRequest =
        api.updateRequest(requestId, UpdateRequestRequest("declined")).toDomain("declined")

    private fun HisabRequestDto.toDomain(defaultStatus: String = "pending") = HisabRequest(
        id = id,
        senderId = senderId,
        senderName = senderName,
        senderEmail = senderEmail.orEmpty(),
        senderProfilePicUrl = profilePictureUrl ?: senderProfilePicUrl.orEmpty(),
        profilePictureUrl = profilePictureUrl ?: senderProfilePicUrl,
        receiverId = receiverId,
        groupId = groupId,
        groupName = groupName,
        status = status ?: defaultStatus,
        createdAt = createdAt
    )
}
