package com.example.hisab.util

import com.example.hisab.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Constants {
    const val API_BASE_URL = BuildConfig.API_BASE_URL
    const val DEFAULT_GOOGLE_AVATAR_URL = "https://ssl.gstatic.com/images/branding/product/1x/avatar_circle_blue_512dp.png"

    fun profileImageUrl(path: String?): String {
        if (path.isNullOrBlank()) return DEFAULT_GOOGLE_AVATAR_URL
        return if (path.startsWith("/static/")) {
            API_BASE_URL.toHttpUrl().resolve(path)?.takeIf { it.encodedPath.startsWith("/static/") }?.toString()
                ?: DEFAULT_GOOGLE_AVATAR_URL
        } else {
            path.toHttpUrlOrNull()?.toString() ?: path
        }
    }
}
