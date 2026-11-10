package com.example.hisab.features.profile.data

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val uniqueId: String? = null,
    val profilePicUrl: String? = null,
    val themePreference: String? = null,
    val role: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class UpdateUserRequest(
    val name: String,
    val profilePicUrl: String,
    val themePreference: String
)

data class UploadResponse(
    val url: String
)
