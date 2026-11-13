package com.example.hisab.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class HisabStatus {
    PENDING,
    PAID,
    SETTLED,
    CANCELLED
}

@Entity(tableName = "hisab_entries")
data class HisabEntry(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "debtor_id") val debtorId: String,
    @ColumnInfo(name = "creditor_id") val creditorId: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "type", defaultValue = "'EXPENSE'") val type: String = "EXPENSE"
)
