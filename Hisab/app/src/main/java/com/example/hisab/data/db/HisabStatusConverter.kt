package com.example.hisab.data.db

import androidx.room.TypeConverter

class HisabStatusConverter {
    @TypeConverter
    fun fromStatus(status: HisabStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): HisabStatus = HisabStatus.valueOf(value)
}