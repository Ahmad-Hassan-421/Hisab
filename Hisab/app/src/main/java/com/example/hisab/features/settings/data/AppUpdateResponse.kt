package com.example.hisab.features.settings.data

import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AppUpdateResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String?,
    @SerializedName("releaseNotes") val releaseNotes: String?,
    @SerializedName("downloadUrl") val downloadUrl: String?,
    @SerializedName("sizeBytes") val sizeBytes: Long?,
    @SerializedName("sha256") val sha256: String?,
    @SerializedName("required") val required: Boolean = false,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("minimumSupportedVersionCode") val minimumSupportedVersionCode: Int? = null
) {
    internal fun validate(qaOrigin: String?): VerifiedUpdate? {
        val name = versionName?.takeIf(versionPattern::matches) ?: return null
        val size = sizeBytes?.takeIf { it in 1..536_870_912L } ?: return null
        val digest = sha256?.takeIf(digestPattern::matches) ?: return null
        val url = downloadUrl?.toHttpUrlOrNull() ?: return null
        val expectedUrl = "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v$name/hisab-v$name.apk"
        val local = qaOrigin?.toHttpUrlOrNull()
        val qaDownload = local != null && url.scheme == local.scheme && url.host == local.host &&
            url.port == local.port && url.host in setOf("localhost", "127.0.0.1", "10.0.2.2") &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null
        val correctChannel = if (qaOrigin == null) downloadUrl == expectedUrl else qaDownload
        if (versionCode !in 1..2_100_000_000 || !correctChannel) return null
        if (minimumSupportedVersionCode?.let { it !in 1..versionCode } == true ||
            releaseDate?.let { releaseDateMillis(it) == null } == true) return null
        return VerifiedUpdate(versionCode, name, releaseNotes.orEmpty().take(4_000), url.toString(), size, digest,
            required, releaseDate, minimumSupportedVersionCode)
    }

    private companion object {
        val versionPattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
        val digestPattern = Regex("[a-f0-9]{64}")
    }
}

internal fun releaseDateMillis(value: String): Long? {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z").matches(value)) return null
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    return runCatching { formatter.parse(value)?.time }.getOrNull()
}

data class VerifiedUpdate internal constructor(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val required: Boolean = false,
    val releaseDate: String? = null,
    val minimumSupportedVersionCode: Int? = null
) {
    internal fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode
    internal fun isRequiredFor(installedVersionCode: Int): Boolean = required ||
        minimumSupportedVersionCode?.let { installedVersionCode < it } == true
}
