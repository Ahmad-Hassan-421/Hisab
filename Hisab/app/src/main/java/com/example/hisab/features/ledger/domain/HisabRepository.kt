package com.example.hisab.features.ledger.domain

import com.example.hisab.data.db.HisabEntry
import kotlinx.coroutines.flow.Flow

interface HisabRepository {
    fun getAllEntries(): Flow<List<HisabEntry>>
    fun getEntriesForGroup(groupId: String): Flow<List<HisabEntry>>
    suspend fun createHisabEntry(entry: HisabEntry)
    suspend fun createBatchEntries(entries: List<HisabEntry>)
    suspend fun syncGroupEntries(groupId: String)
    suspend fun clearGroupEntries(groupId: String)
}
