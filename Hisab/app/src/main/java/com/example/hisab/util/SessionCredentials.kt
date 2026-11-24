package com.example.hisab.util

/** Account-bound session access used by startup; storage remains encrypted in TokenManager. */
interface SessionCredentials {
    fun getUserId(): String?
    fun getToken(): String?
    fun clearAll()
    fun cachePrivacyVersion(userId: String, version: String?)
    fun cachedPrivacyVersion(userId: String): String?
}
