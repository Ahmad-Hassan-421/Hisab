package com.example.hisab.features.groups.data

import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.groups.domain.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiGroupRepository @Inject constructor(
    private val api: HisabApiService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : GroupRepository {
    override suspend fun pictures(groupId: String, members: List<String>) = api.groupPictures(groupId, members.joinToString(","))
    override suspend fun setPicture(groupId: String, uri: android.net.Uri?): String = if (uri == null) api.removeGroupPicture(groupId).url
        else com.example.hisab.features.profile.data.uploadCroppedImage(context, uri) { api.uploadGroupPicture(groupId, it).url }
    override suspend fun getSummary(): com.example.hisab.features.groups.domain.DashboardSummary {
        val value = api.getDashboardSummary()
        val debt = value.totalDebt.toBigDecimalOrNull()
        val receive = value.totalReceive.toBigDecimalOrNull()
        if (debt == null || receive == null || debt.signum() < 0 || receive.signum() < 0 ||
            debt.stripTrailingZeros().scale() > 3 || receive.stripTrailingZeros().scale() > 3 || value.groupCount < 0) {
            throw com.google.gson.JsonParseException("Invalid balance summary")
        }
        return com.example.hisab.features.groups.domain.DashboardSummary(debt, receive, value.groupCount, value.generatedAt)
    }
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

    private fun HisabGroupDto.toDomain() = HisabGroup(id, name, members.orEmpty(), exMembers.orEmpty(), createdAt, memberCount ?: members.orEmpty().size, pictureUrl.orEmpty(), pictureUpdatedAt)
}
