package com.example.hisab.domain.model

data class HisabUser(
    val id: String,
    val name: String,
    val email: String,
    val profilePicUrl: String = "",
    val themePreference: String = "system",
    val role: String = "user"
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}