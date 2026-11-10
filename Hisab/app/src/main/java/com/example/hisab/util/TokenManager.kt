package com.example.hisab.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {
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
        sharedPreferences.edit().putString("jwt_token", token).putString("user_id", userId).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString("jwt_token", null)
    }

    fun getUserId(): String? {
        return sharedPreferences.getString("user_id", null)
    }

    @Synchronized
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    @Synchronized
    fun clearIfMatching(token: String): Boolean {
        if (getToken() != token) return false
        clearAll()
        return true
    }
}
