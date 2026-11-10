package com.example.hisab.util

import com.example.hisab.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Constants {
    const val API_BASE_URL = BuildConfig.API_BASE_URL

    fun profileImageUrl(path: String): String? {
        if (path.isBlank()) return null
        return if (path.startsWith("/static/")) {
            API_BASE_URL.toHttpUrl().resolve(path)?.takeIf { it.encodedPath.startsWith("/static/") }?.toString()
        } else {
            path.toHttpUrlOrNull()?.toString()
        }
    }
}
