package com.example.hisab.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HisabEntryDao {
    @androidx.room.Transaction
    suspend fun replaceGroupEntries(groupId: String, entries: List<HisabEntry>) {
        deleteEntriesForGroup(groupId)
        insertAll(entries)
    }

    @Query("SELECT * FROM hisab_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HisabEntry>>

    @Query("SELECT * FROM hisab_entries WHERE group_id = :groupId ORDER BY timestamp DESC")
    fun getEntriesForGroup(groupId: String): Flow<List<HisabEntry>>

    @Query("SELECT * FROM hisab_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): HisabEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HisabEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HisabEntry>)

    @Update
    suspend fun update(entry: HisabEntry)

    @Query("DELETE FROM hisab_entries WHERE id = :entryId")
    suspend fun delete(entryId: String)

    @Query("DELETE FROM hisab_entries WHERE group_id = :groupId")
    suspend fun deleteEntriesForGroup(groupId: String)

    @Query("DELETE FROM hisab_entries")
    suspend fun clearAll()
}
