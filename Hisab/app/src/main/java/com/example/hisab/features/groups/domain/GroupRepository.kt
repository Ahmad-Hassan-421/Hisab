package com.example.hisab.features.groups.domain

import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    suspend fun getGroupsForUser(userId: String): List<HisabGroup>
    suspend fun createGroup(name: String, members: List<String>): HisabGroup
    suspend fun deleteGroup(groupId: String)
    suspend fun leaveGroup(groupId: String)
}
