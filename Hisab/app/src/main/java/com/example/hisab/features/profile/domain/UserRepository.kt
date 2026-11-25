package com.example.hisab.features.profile.domain

import android.net.Uri
import com.example.hisab.data.db.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(userId: String): Flow<User?>
    suspend fun fetchRemoteUser(userId: String, allowCached: Boolean = true): User?
    suspend fun createUserDocument(user: User)
    suspend fun saveUser(user: User)
    suspend fun updateUser(user: User)
    suspend fun uploadProfilePicture(uri: Uri): String
    suspend fun removeProfilePicture(): String = throw UnsupportedOperationException("Remote picture removal unavailable")
    suspend fun updateProfileFields(userId: String, name: String? = null, theme: String? = null) {
        val user = fetchRemoteUser(userId, false) ?: return
        updateUser(user.copy(name = name ?: user.name, themePreference = theme ?: user.themePreference))
    }
    suspend fun deleteUser(userId: String)
}
