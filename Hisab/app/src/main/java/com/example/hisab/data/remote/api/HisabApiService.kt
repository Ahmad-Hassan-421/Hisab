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
import com.example.hisab.features.privacy.data.PrivacyDocument
import com.example.hisab.features.privacy.data.PrivacyStatus
import com.example.hisab.features.privacy.data.ArchiveCard
import com.example.hisab.features.privacy.data.RecoveryReceipt
import com.example.hisab.features.privacy.data.HistoricalMember
import com.example.hisab.features.privacy.data.DeleteAccountInput
import com.example.hisab.features.settings.data.AppUpdateResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface HisabApiService {
    @GET("account/data/archives")
    suspend fun getArchives(@Query("cursor") cursor: String?): CursorPage<ArchiveCard>
    @GET("account/data/recovered")
    suspend fun getRecovered(@Query("cursor") cursor: String?): CursorPage<ArchiveCard>
    @POST("account/data/archives/{id}/recover")
    suspend fun recoverArchive(@Path("id") id: String): RecoveryReceipt
    @GET("account/data/recovered/{id}/entries")
    suspend fun getRecoveredEntries(@Path("id") id: String, @Query("kind") kind: String, @Query("cursor") cursor: String?): CursorPage<HisabEntryDto>
    @GET("account/data/recovered/{id}/members")
    suspend fun getRecoveredMembers(@Path("id") id: String, @Query("cursor") cursor: String?): CursorPage<HistoricalMember>
    @DELETE("account/data/recovered/{id}")
    suspend fun deleteRecovered(@Path("id") id: String)
    @GET("account/data/accounts")
    suspend fun getDeletableAccounts(): List<UserDto>
    @POST("account/data/delete-account")
    suspend fun permanentlyDeleteAccount(@Body input: DeleteAccountInput)
    @GET("v2/groups/page")
    suspend fun getGroupsPage(@Query("cursor") cursor: String? = null): CursorPage<HisabGroupDto>

    @GET("v2/groups/{id}")
    suspend fun getGroup(@Path("id") groupId: String): HisabGroupDto

    @GET("v2/groups/{id}/overview")
    suspend fun getLedgerOverview(@Path("id") groupId: String): LedgerOverview

    @GET("v2/groups/{id}/members")
    suspend fun getMembersPage(@Path("id") groupId: String, @Query("cursor") cursor: String? = null): CursorPage<UserDto>

    @GET("v2/groups/{id}/suggested-members")
    suspend fun getSuggestedMembersPage(@Path("id") groupId: String, @Query("cursor") cursor: String? = null): CursorPage<UserDto>

    @GET("v2/entries/page")
    suspend fun getEntriesPage(@Query("groupId") groupId: String, @Query("cursor") cursor: String? = null,
        @Query("kind") kind: String = "all", @Query("otherUserId") otherUserId: String? = null): CursorPage<HisabEntryDto>

    @GET("v2/requests/page")
    suspend fun getRequestsPage(@Query("cursor") cursor: String? = null): CursorPage<HisabRequestDto>

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
