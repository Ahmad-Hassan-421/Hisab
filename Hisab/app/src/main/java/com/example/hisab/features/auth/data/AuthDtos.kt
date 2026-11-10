package com.example.hisab.features.auth.data

import com.example.hisab.features.profile.data.UserDto

data class AuthResponse(val token: String, val user: UserDto)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)
