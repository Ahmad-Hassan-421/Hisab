package com.hisab.server.features.media

import java.net.URI

internal object ProfileImages {
    private const val LEGACY_ORIGIN = "http://192.168.10.4:8080"
    private val renderOrigin = System.getenv("RENDER_EXTERNAL_URL")?.trimEnd('/')?.takeIf { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        uri != null && uri.scheme == "https" && uri.host != null && uri.userInfo == null &&
            uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
    }

    fun storedPath(value: String): String = when {
        value.startsWith("$LEGACY_ORIGIN/static/") -> value.removePrefix(LEGACY_ORIGIN)
        renderOrigin != null && value.startsWith("$renderOrigin/static/") -> value.removePrefix(renderOrigin)
        else -> value
    }

    fun isSupported(value: String): Boolean {
        if (value.isEmpty()) return true
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (value.startsWith("/static/")) {
            return uri.path.split('/').none { it == "." || it == ".." } &&
                uri.rawQuery == null && uri.rawFragment == null
        }
        return uri.scheme in setOf("https", "http") && uri.host != null && uri.userInfo == null
    }

    fun publicUrl(value: String): String {
        val path = storedPath(value).takeIf(::isSupported) ?: DEFAULT_PROFILE_PATH
        return if (renderOrigin != null && path.startsWith("/static/")) "$renderOrigin$path" else path
    }
}
