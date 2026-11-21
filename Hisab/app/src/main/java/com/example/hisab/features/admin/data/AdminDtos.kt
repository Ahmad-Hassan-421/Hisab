package com.example.hisab.features.admin.data

data class AccountOverview(val registered: Long, val registeredInLast30Days: Long, val suspended: Long)
data class AccountDirectoryPage(val users: List<DirectoryAccount>, val nextCursor: String?)
data class DirectoryAccount(
    val id: String,
    val publicId: String?,
    val name: String?,
    val email: String?,
    val createdAt: Long?,
    val role: String,
    val status: String,
    val emailVerified: Boolean,
    val avatarUrl: String?
)

data class ChangeAccountStatus(val status: String)
