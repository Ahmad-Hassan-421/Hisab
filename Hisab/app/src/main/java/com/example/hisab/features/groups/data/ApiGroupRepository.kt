package com.example.hisab.features.groups.data

import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.groups.domain.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiGroupRepository @Inject constructor(
    private val api: HisabApiService
) : GroupRepository {
    override suspend fun getGroupsForUser(userId: String): List<HisabGroup> {
        return api.getGroupsForUser(userId).map { it.toDomain() }
    }

    override suspend fun getGroupsPage(userId: String, cursor: String?): CursorPage<HisabGroup> =
        api.getGroupsPage(cursor).let { CursorPage(it.items.map { dto -> dto.toDomain() }, it.nextCursor) }

    override suspend fun getGroup(userId: String, groupId: String): HisabGroup = api.getGroup(groupId).toDomain()

    override suspend fun createGroup(name: String, members: List<String>): HisabGroup {
        return api.createGroup(CreateGroupRequest(name, members)).toDomain()
    }

    override suspend fun deleteGroup(groupId: String) {
        api.deleteGroup(groupId)
    }

    override suspend fun leaveGroup(groupId: String) {
        api.leaveGroup(groupId)
    }

    private fun HisabGroupDto.toDomain() = HisabGroup(id, name, members.orEmpty(), exMembers.orEmpty(), createdAt, memberCount ?: members.orEmpty().size)
}
