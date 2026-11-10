package com.example.hisab.features.groups.domain

data class HisabGroup(
    val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val exMembers: List<String> = emptyList(),
    val createdAt: Long = 0
)
