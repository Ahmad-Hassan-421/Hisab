package com.example.hisab.util

import com.example.hisab.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Constants {
    const val API_BASE_URL = BuildConfig.API_BASE_URL
    const val DEFAULT_GOOGLE_AVATAR_URL = "https://ssl.gstatic.com/images/branding/product/1x/avatar_circle_blue_512dp.png"

    fun profileImageUrl(path: String?): String? {
        if (path.isNullOrBlank() || path == DEFAULT_GOOGLE_AVATAR_URL) return null
        return if (path.startsWith("/static/")) {
            API_BASE_URL.toHttpUrl().resolve(path)?.takeIf {
                it.encodedPath.startsWith("/static/") && it.encodedPath != "/static/default_profile.png"
            }?.toString()
        } else {
            path.toHttpUrlOrNull()?.takeIf {
                it.encodedPath != "/static/default_profile.png" && it.username.isEmpty() && it.password.isEmpty()
            }?.toString()
        }
    }
}
