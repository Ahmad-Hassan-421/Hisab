package com.example.hisab.features.groups.data

data class HisabGroupDto(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),
    val exMembers: List<String>? = null,
    val createdAt: Long,
    val memberCount: Int? = null
)

data class CreateGroupRequest(
    val name: String,
    val members: List<String>
)
