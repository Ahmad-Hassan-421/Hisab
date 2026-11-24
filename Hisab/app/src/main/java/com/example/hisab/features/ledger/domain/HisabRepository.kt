package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import kotlinx.coroutines.flow.Flow
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.ledger.data.LedgerOverview
import com.example.hisab.data.db.User
import kotlinx.coroutines.flow.first

interface HisabRepository {
    fun getAllEntries(): Flow<List<HisabEntry>>
    fun getEntriesForGroup(groupId: String): Flow<List<HisabEntry>>
    suspend fun createHisabEntry(entry: HisabEntry)
    suspend fun createBatchEntries(entries: List<HisabEntry>)
    suspend fun syncGroupEntries(groupId: String)
    suspend fun clearGroupEntries(groupId: String)
    suspend fun getOverview(groupId: String): LedgerOverview? = null
    suspend fun getMembersPage(groupId: String, cursor: String? = null): CursorPage<User> = CursorPage(emptyList())
    suspend fun getSuggestedMembersPage(groupId: String, cursor: String? = null): CursorPage<User> = CursorPage(emptyList())
    suspend fun getEntriesPage(groupId: String, cursor: String? = null, kind: String = "all",
        otherUserId: String? = null): CursorPage<HisabEntry> = CursorPage(getEntriesForGroup(groupId).first())
}
