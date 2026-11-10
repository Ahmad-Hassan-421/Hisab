package com.example.hisab.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "profile_pic_url") val profilePicUrl: String = "",
    @ColumnInfo(name = "theme_preference") val themePreference: String = "system",
    @ColumnInfo(name = "role") val role: String = "user",
    @ColumnInfo(name = "unique_id") val uniqueId: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)