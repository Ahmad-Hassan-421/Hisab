package com.example.hisab.features.profile.data

import android.content.Context
import android.net.Uri
import com.example.hisab.data.db.User
import com.example.hisab.data.db.UserDao
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.profile.data.UpdateUserRequest
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
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
    @ApplicationContext private val context: Context,
    private val failures: FailureReporter
) : UserRepository {

    override fun getCurrentUser(userId: String): Flow<User?> = userDao.getUser(userId)

    override suspend fun fetchRemoteUser(userId: String, allowCached: Boolean): User? {
        val existing = userDao.getUser(userId).first()
        return try {
            val dto = api.getUser(userId)
            val remotePic = dto.profilePicUrl
            val picToUse = if (!remotePic.isNullOrBlank() && !remotePic.endsWith("default_profile.png")) {
                remotePic
            } else if (!existing?.profilePicUrl.isNullOrBlank() && existing?.profilePicUrl?.endsWith("default_profile.png") == false) {
                existing.profilePicUrl
            } else {
                com.example.hisab.util.Constants.DEFAULT_GOOGLE_AVATAR_URL
            }

            val user = User(
                id = dto.id,
                name = dto.name,
                email = dto.email,
                profilePicUrl = picToUse,
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
            if (!allowCached) throw e
            if (failures.report("Fetch profile", e) == AppFailure.SESSION_EXPIRED) throw e
            existing ?: userDao.getUser(userId).first()
        }
    }

    override suspend fun createUserDocument(user: User) {
        val pic = if (user.profilePicUrl.isBlank() || user.profilePicUrl.endsWith("default_profile.png")) {
            com.example.hisab.util.Constants.DEFAULT_GOOGLE_AVATAR_URL
        } else {
            user.profilePicUrl
        }
        userDao.insert(user.copy(profilePicUrl = pic))
    }

    override suspend fun saveUser(user: User) {
        updateUser(user)
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
        val localPersistentAvatar = File(context.filesDir, "current_user_avatar.jpg")
        val file = File.createTempFile("profile-", ".jpg", context.cacheDir)
        try {
            val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected image" }
            input.use { source ->
                file.outputStream().use { output -> source.copyTo(output) }
            }
            // Cache locally in persistent internal filesDir so it survives app restarts
            try {
                file.copyTo(localPersistentAvatar, overwrite = true)
            } catch (e: java.io.IOException) {
                failures.report("Cache profile picture", e)
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
