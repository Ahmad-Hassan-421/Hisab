package com.example.hisab.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context,
    private val push: com.example.hisab.features.notifications.PushState) : SessionCredentials {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun saveSession(token: String, userId: String) {
        val edits = sharedPreferences.edit().putString("jwt_token", token).putString("user_id", userId)
        if (getUserId() != userId) edits.remove("privacy_ack_version")
        edits.apply()
    }

    override fun getToken(): String? {
        return sharedPreferences.getString("jwt_token", null)
    }

    override fun getUserId(): String? {
        return sharedPreferences.getString("user_id", null)
    }

    @Synchronized
    override fun cachePrivacyVersion(userId: String, version: String?) {
        if (getUserId() == userId) sharedPreferences.edit().putString("privacy_ack_version", version).apply()
    }

    @Synchronized
    override fun cachedPrivacyVersion(userId: String): String? =
        if (getUserId() == userId) sharedPreferences.getString("privacy_ack_version", null) else null

    @Synchronized
    override fun clearAll() {
        getUserId()?.let {
            // Notification storage/scheduling must never keep an authentication session alive during logout.
            runCatching { push.signOut(it) }
            runCatching { androidx.core.app.NotificationManagerCompat.from(context).cancelAll() }
            runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false }
            runCatching { com.example.hisab.features.notifications.schedulePushSync(context) }
        }
        sharedPreferences.edit().clear().apply()
    }

    @Synchronized
    fun clearIfMatching(token: String): Boolean {
        if (getToken() != token) return false
        clearAll()
        return true
    }
}
