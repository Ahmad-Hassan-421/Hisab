package com.example.hisab.features.notifications

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushState @Inject constructor(@ApplicationContext context: Context) {
    private val storage = EncryptedSharedPreferences.create(context, "push_private_state",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private val gson = Gson()
    @Synchronized fun installation(): RemovePushDevice {
        val id = storage.getString("installation", null) ?: UUID.randomUUID().toString()
        val secret = storage.getString("proof", null) ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(storage.edit().putString("installation", id).putString("proof", secret).commit())
        return RemovePushDevice(id, secret)
    }
    @Synchronized fun nextBinding(): Long {
        val next = Math.addExact(storage.getLong("binding", 0), 1)
        check(storage.edit().putLong("binding", next).commit()); return next
    }
    @Synchronized fun signOut(owner: String) {
        val revision = nextBinding()
        check(storage.edit().putLong("revoke", revision).putBoolean("enabled:$owner", false).commit()); changes.value++
    }
    @Synchronized fun pendingRevoke() = storage.getLong("revoke", 0)
    @Synchronized fun revoked(revision: Long) {
        if (pendingRevoke() == revision) check(storage.edit().remove("revoke").commit())
    }
    @Synchronized fun token(): String? = storage.getString("fcm", null)
    @Synchronized fun setToken(value: String) { check(storage.edit().putString("fcm", value).commit()); changes.value++ }
    @Synchronized fun asked(owner: String) = storage.getBoolean("asked:$owner", false)
    @Synchronized fun enabled(owner: String) = storage.getBoolean("enabled:$owner", false)
    @Synchronized fun decide(owner: String, enabled: Boolean) {
        check(storage.edit().putBoolean("asked:$owner", true).putBoolean("enabled:$owner", enabled).commit()); changes.value++
    }
    @Synchronized fun preferences(owner: String): PushPreferences = runCatching {
        gson.fromJson(storage.getString("preferences:$owner", null), PushPreferences::class.java)
    }.getOrNull() ?: PushPreferences()
    @Synchronized fun savePreferences(owner: String, value: PushPreferences) {
        check(storage.edit().putString("preferences:$owner", gson.toJson(value)).commit()); changes.value++
    }
    @Synchronized fun delivered(owner: String, event: String): Boolean = storage.getStringSet("seen:$owner", emptySet()).orEmpty().contains(event)
    @Synchronized fun markDelivered(owner: String, event: String) {
        val events = storage.getStringSet("seen:$owner", emptySet()).orEmpty().toMutableSet()
        // Stable Android notification tags also prevent duplicates outside this bounded persisted window.
        if (events.size >= 2000) events.clear()
        events.add(event); check(storage.edit().putStringSet("seen:$owner", events).commit())
    }
    @Synchronized fun erase(owner: String) {
        val edits = storage.edit()
        listOf("asked", "enabled", "preferences", "seen").forEach { edits.remove("$it:$owner") }
        check(edits.commit()); changes.value++
    }
}
