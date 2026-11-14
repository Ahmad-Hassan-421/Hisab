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
    @SerializedName("required") val required: Boolean = false
) {
    internal fun validate(qaOrigin: String?): VerifiedUpdate? {
        val name = versionName?.takeIf(versionPattern::matches) ?: return null
        val size = sizeBytes?.takeIf { it > 0 } ?: return null
        val digest = sha256?.takeIf(digestPattern::matches) ?: return null
        val url = downloadUrl?.toHttpUrlOrNull() ?: return null
        val expectedUrl = "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v$name/hisab-v$name.apk"
        val local = qaOrigin?.toHttpUrlOrNull()
        val qaDownload = local != null && url.scheme == local.scheme && url.host == local.host &&
            url.port == local.port && url.host in setOf("localhost", "127.0.0.1", "10.0.2.2") &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null
        if (versionCode !in 1..2_100_000_000 || (downloadUrl != expectedUrl && !qaDownload)) return null
        return VerifiedUpdate(versionCode, name, releaseNotes.orEmpty(), url.toString(), size, digest)
    }

    private companion object {
        val versionPattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
        val digestPattern = Regex("[a-f0-9]{64}")
    }
}

data class VerifiedUpdate internal constructor(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String
) {
    internal fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode
}
