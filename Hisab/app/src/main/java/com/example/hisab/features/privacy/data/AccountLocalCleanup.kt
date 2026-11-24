package com.example.hisab.features.privacy.data

import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.features.profile.data.LocalProfilePhotoStore
import com.example.hisab.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AccountLocalCleanup { suspend fun erase(accountId: String) }
class DeviceAccountCleanup @Inject constructor(private val database: HisabDatabase, private val photos: LocalProfilePhotoStore,
    private val tokens: TokenManager) : AccountLocalCleanup {
    override suspend fun erase(accountId: String) = withContext(Dispatchers.IO) {
        try { database.clearAllTables(); photos.erase(accountId) }
        finally { if (tokens.getUserId() == accountId) tokens.clearAll() }
    }
}
