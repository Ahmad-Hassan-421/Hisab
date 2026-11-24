package com.example.hisab.features.privacy.data

import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.ledger.data.HisabEntryDto
import com.example.hisab.features.profile.data.UserDto
import javax.inject.Inject

data class ArchiveCard(val id: String, val name: String, val deletedAt: Long, val expiresAt: Long?,
    val recoveredAt: Long?, val recordCount: Long, val memberCount: Long, val status: String)
data class RecoveryReceipt(val id: String, val name: String, val status: String, val dueAt: Long)
data class HistoricalMember(val id: String, val name: String, val profilePicUrl: String)
data class DeleteAccountInput(val accountId: String, val password: String)

interface AccountDataSource {
    suspend fun archives(cursor: String?): CursorPage<ArchiveCard>
    suspend fun recovered(cursor: String?): CursorPage<ArchiveCard>
    suspend fun entries(id: String, kind: String, cursor: String?): CursorPage<HisabEntryDto>
    suspend fun members(id: String, cursor: String?): CursorPage<HistoricalMember>
    suspend fun recover(id: String): RecoveryReceipt
    suspend fun deleteCopy(id: String)
    suspend fun accounts(): List<UserDto>
    suspend fun deleteAccount(input: DeleteAccountInput)
}

class AccountDataRepository @Inject constructor(private val api: HisabApiService) : AccountDataSource {
    override suspend fun archives(cursor: String?) = api.getArchives(cursor)
    override suspend fun recovered(cursor: String?) = api.getRecovered(cursor)
    override suspend fun entries(id: String, kind: String, cursor: String?) = api.getRecoveredEntries(id, kind, cursor)
    override suspend fun members(id: String, cursor: String?) = api.getRecoveredMembers(id, cursor)
    override suspend fun recover(id: String) = api.recoverArchive(id)
    override suspend fun deleteCopy(id: String) { api.deleteRecovered(id) }
    override suspend fun accounts() = api.getDeletableAccounts()
    override suspend fun deleteAccount(input: DeleteAccountInput) { api.permanentlyDeleteAccount(input) }
}
