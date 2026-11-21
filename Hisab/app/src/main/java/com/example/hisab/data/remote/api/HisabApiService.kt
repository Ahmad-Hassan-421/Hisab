package com.example.hisab.data.remote.api

import com.example.hisab.features.groups.data.*
import com.example.hisab.features.ledger.data.*
import com.example.hisab.features.invitations.data.*
import com.example.hisab.features.profile.data.*
import com.example.hisab.features.auth.data.*
import com.example.hisab.features.admin.data.AccountDirectoryPage
import com.example.hisab.features.admin.data.AccountOverview
import com.example.hisab.features.admin.data.ChangeAccountStatus
import com.example.hisab.features.privacy.data.PolicyAcknowledgment
import com.example.hisab.features.privacy.data.PrivacyDataReceipt
import com.example.hisab.features.privacy.data.PrivacyDataRequest
import com.example.hisab.features.privacy.data.PrivacyDocument
import com.example.hisab.features.privacy.data.PrivacyStatus
import com.example.hisab.features.settings.data.AppUpdateResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface HisabApiService {
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): AuthResponse

    @POST("auth/recovery/request")
    suspend fun requestPasswordRecovery(@Body request: RecoveryRequest): Response<Unit>

    @POST("auth/recovery/reset")
    suspend fun resetPassword(@Body request: PasswordResetRequest): Response<Unit>

    @GET("auth/verification")
    suspend fun getRecoveryEmailStatus(): RecoveryEmailStatus

    @POST("auth/verification/request")
    suspend fun requestRecoveryEmailVerification(): Response<Unit>

    @POST("auth/verification/confirm")
    suspend fun verifyRecoveryEmail(@Body request: RecoveryEmailConfirmation): Response<Unit>

    @GET("privacy/policy")
    suspend fun getPrivacyPolicy(): PrivacyDocument

    @GET("account/privacy/status")
    suspend fun getPrivacyStatus(): PrivacyStatus

    @POST("account/privacy/acknowledge")
    suspend fun acknowledgePrivacy(@Body acknowledgment: PolicyAcknowledgment): PrivacyStatus

    @POST("account/privacy/data-requests")
    suspend fun requestPrivacyData(@Body request: PrivacyDataRequest): PrivacyDataReceipt

    @GET("admin/overview")
    suspend fun getAccountOverview(): AccountOverview

    @GET("admin/users")
    suspend fun getAccountDirectory(@Query("q") query: String, @Query("status") status: String,
                                    @Query("cursor") cursor: String?): AccountDirectoryPage

    @PATCH("admin/users/{id}/status")
    suspend fun changeAccountStatus(@Path("id") id: String, @Body request: ChangeAccountStatus): Response<Unit>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto

    @GET("users/by-unique-id/{uid}")
    suspend fun getUserByUniqueId(@Path("uid") uid: String): UserDto

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: String, @Body req: UpdateUserRequest): UserDto

    @GET("groups")
    suspend fun getGroupsForUser(@Query("userId") userId: String): List<HisabGroupDto>

    @POST("groups")
    suspend fun createGroup(@Body req: CreateGroupRequest): HisabGroupDto

    @DELETE("groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String)

    @POST("groups/{id}/leave")
    suspend fun leaveGroup(@Path("id") id: String)

    @POST("requests")
    suspend fun sendRequest(@Body req: SendRequestRequest): HisabRequestDto

    @GET("requests")
    suspend fun getPendingRequests(
        @Query("receiverId") receiverId: String,
        @Query("status") status: String = "pending"
    ): List<HisabRequestDto>

    @PATCH("requests/{id}")
    suspend fun updateRequest(@Path("id") id: String, @Body req: UpdateRequestRequest): HisabRequestDto

    @GET("entries")
    suspend fun getEntries(@Query("groupId") groupId: String): List<HisabEntryDto>

    @DELETE("entries/group/{groupId}")
    suspend fun clearGroupEntries(@Path("groupId") groupId: String)

    @POST("entries")
    suspend fun createEntry(@Body req: CreateEntryRequest): HisabEntryDto

    @POST("entries/batch")
    suspend fun createBatchEntries(@Body req: CreateBatchEntryRequest): List<HisabEntryDto>

    @Multipart
    @POST("upload/profile-pic")
    suspend fun uploadProfilePic(
        @Part image: MultipartBody.Part
    ): UploadResponse

    @GET("api/releases/latest")
    suspend fun getLatestRelease(): Response<AppUpdateResponse>
}
