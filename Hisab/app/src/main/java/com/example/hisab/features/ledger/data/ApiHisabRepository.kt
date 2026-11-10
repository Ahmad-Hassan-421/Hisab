package com.example.hisab.features.ledger.data

import com.example.hisab.data.db.HisabEntry
import com.example.hisab.data.db.HisabEntryDao
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.ledger.domain.HisabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiHisabRepository @Inject constructor(
    private val api: HisabApiService,
    private val hisabEntryDao: HisabEntryDao
) : HisabRepository {
    private val cacheUpdates = Mutex()

    override fun getAllEntries(): Flow<List<HisabEntry>> = hisabEntryDao.getAllEntries()

    override fun getEntriesForGroup(groupId: String): Flow<List<HisabEntry>> {
        return hisabEntryDao.getEntriesForGroup(groupId)
    }

    override suspend fun createHisabEntry(entry: HisabEntry) = cacheUpdates.withLock {
        hisabEntryDao.insert(api.createEntry(entry.toRequest()).toEntity())
    }

    override suspend fun createBatchEntries(entries: List<HisabEntry>) = cacheUpdates.withLock {
        val requests = entries.map { it.toRequest() }
        val dtos = api.createBatchEntries(CreateBatchEntryRequest(requests))
        hisabEntryDao.insertAll(dtos.map { it.toEntity() })
    }
    
    override suspend fun syncGroupEntries(groupId: String) = cacheUpdates.withLock {
        val entries = api.getEntries(groupId).map { it.toEntity() }
        hisabEntryDao.replaceGroupEntries(groupId, entries)
    }
    
    override suspend fun clearGroupEntries(groupId: String) = cacheUpdates.withLock {
        api.clearGroupEntries(groupId)
        hisabEntryDao.deleteEntriesForGroup(groupId)
    }

    private fun HisabEntry.toRequest() = CreateEntryRequest(groupId, debtorId, creditorId, amount, description, id)

    private fun HisabEntryDto.toEntity() = HisabEntry(id, groupId, debtorId, creditorId, amount, description.orEmpty(), timestamp)
}
