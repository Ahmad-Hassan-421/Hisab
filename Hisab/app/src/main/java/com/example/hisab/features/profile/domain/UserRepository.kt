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
    suspend fun deleteUser(userId: String)
}
