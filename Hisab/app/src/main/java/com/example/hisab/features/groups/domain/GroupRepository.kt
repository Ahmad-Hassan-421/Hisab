package com.example.hisab.features.groups.domain

import kotlinx.coroutines.flow.Flow
import com.example.hisab.data.remote.api.CursorPage

interface GroupRepository {
    suspend fun pictures(groupId: String, members: List<String>): com.example.hisab.features.profile.data.GroupPictures? = null
    suspend fun setPicture(groupId: String, uri: android.net.Uri?): String = throw UnsupportedOperationException("Picture changes unavailable")
    suspend fun getSummary(): DashboardSummary? = null
    suspend fun getGroupsForUser(userId: String): List<HisabGroup>
    suspend fun getGroupsPage(userId: String, cursor: String? = null): CursorPage<HisabGroup> =
        CursorPage(getGroupsForUser(userId))
    suspend fun getGroup(userId: String, groupId: String): HisabGroup? = getGroupsForUser(userId).find { it.id == groupId }
    suspend fun createGroup(name: String, members: List<String>): HisabGroup
    suspend fun deleteGroup(groupId: String)
    suspend fun leaveGroup(groupId: String)
}
