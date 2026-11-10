package com.example.hisab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class, HisabEntry::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(HisabStatusConverter::class)
abstract class HisabDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun hisabEntryDao(): HisabEntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN unique_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN profile_pic_url TEXT NOT NULL DEFAULT ''")
            }
        }

    }
}
