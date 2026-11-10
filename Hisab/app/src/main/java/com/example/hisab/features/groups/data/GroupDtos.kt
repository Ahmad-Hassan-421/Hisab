package com.example.hisab.features.groups.data

data class HisabGroupDto(
    val id: String,
    val name: String,
    val members: List<String>,
    val exMembers: List<String>? = null,
    val createdAt: Long
)

data class CreateGroupRequest(
    val name: String,
    val members: List<String>
)
