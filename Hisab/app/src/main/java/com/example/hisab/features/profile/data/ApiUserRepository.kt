package com.example.hisab.features.profile.data

import android.content.Context
import android.net.Uri
import com.example.hisab.data.db.User
import com.example.hisab.data.db.UserDao
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.profile.data.UpdateUserRequest
import com.example.hisab.features.profile.domain.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiUserRepository @Inject constructor(
    private val api: HisabApiService,
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) : UserRepository {

    override fun getCurrentUser(userId: String): Flow<User?> = userDao.getUser(userId)

    override suspend fun fetchRemoteUser(userId: String): User? {
        return try {
            val dto = api.getUser(userId)
            val user = User(
                id = dto.id,
                name = dto.name,
                email = dto.email,
                profilePicUrl = dto.profilePicUrl ?: "",
                themePreference = dto.themePreference ?: "system",
                role = dto.role ?: "user",
                uniqueId = dto.uniqueId ?: "",
                createdAt = dto.createdAt ?: System.currentTimeMillis(),
                updatedAt = dto.updatedAt ?: System.currentTimeMillis()
            )
            userDao.insert(user)
            user
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            userDao.getUser(userId).first()
        }
    }

    override suspend fun createUserDocument(user: User) {
        userDao.insert(user)
    }

    override suspend fun saveUser(user: User) {
        val userWithRole = preserveRole(user)
        api.updateUser(user.id, UpdateUserRequest(userWithRole.name, userWithRole.profilePicUrl, userWithRole.themePreference))
        userDao.insert(userWithRole)
    }

    override suspend fun updateUser(user: User) {
        val userWithRole = preserveRole(user).copy(updatedAt = System.currentTimeMillis())
        api.updateUser(user.id, UpdateUserRequest(userWithRole.name, userWithRole.profilePicUrl, userWithRole.themePreference))
        userDao.insert(userWithRole)
    }

    override suspend fun deleteUser(userId: String) {
        userDao.delete(userId)
    }

    override suspend fun uploadProfilePicture(uri: Uri): String = withContext(Dispatchers.IO) {
        val file = File.createTempFile("profile-", ".jpg", context.cacheDir)
        try {
            val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected image" }
            input.use { source ->
                file.outputStream().use { output -> source.copyTo(output) }
            }
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
            api.uploadProfilePic(body).url
        } finally {
            file.delete()
        }
    }

    private suspend fun preserveRole(user: User): User {
        val existing = userDao.getUser(user.id).first()
        return if (existing != null && existing.role != user.role) {
            user.copy(role = existing.role)
        } else {
            user
        }
    }
}
