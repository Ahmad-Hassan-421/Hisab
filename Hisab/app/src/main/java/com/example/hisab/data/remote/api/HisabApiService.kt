package com.example.hisab.data.remote.api

import com.example.hisab.features.groups.data.*
import com.example.hisab.features.ledger.data.*
import com.example.hisab.features.invitations.data.*
import com.example.hisab.features.profile.data.*
import com.example.hisab.features.auth.data.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface HisabApiService {
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): AuthResponse

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
}
