package com.example.hisab.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HisabMigrationTest {
    @Test
    fun versionOneKeepsTheUserAndThreeDecimalLedgerEntry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE users (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, theme_preference TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE hisab_entries (id TEXT NOT NULL PRIMARY KEY, group_id TEXT NOT NULL, debtor_id TEXT NOT NULL, creditor_id TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, timestamp INTEGER NOT NULL)")
                old.execSQL("INSERT INTO users VALUES ('owner','Owner','owner@example.invalid','dark',1,2)")
                old.execSQL("INSERT INTO hisab_entries VALUES ('debt','group','owner','friend',1234.567,'Shared bill',3)")
                old.version = 1
            }
            val db = Room.databaseBuilder(context, HisabDatabase::class.java, name)
                .addMigrations(HisabDatabase.MIGRATION_1_2, HisabDatabase.MIGRATION_2_3, HisabDatabase.MIGRATION_3_4)
                .build()
            try {
                val user = checkNotNull(db.userDao().getUser("owner").first())
                assertEquals("dark", user.themePreference)
                assertEquals("user", user.role)
                val debt = checkNotNull(db.hisabEntryDao().getEntryById("debt"))
                assertEquals("Shared bill", debt.description)
                assertEquals(1234.567, debt.amount, 0.0)
                assertEquals("EXPENSE", debt.type)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }
}
