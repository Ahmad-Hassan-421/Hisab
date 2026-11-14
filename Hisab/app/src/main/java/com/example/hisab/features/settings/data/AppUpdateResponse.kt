package com.example.hisab.features.settings.data

import com.google.gson.annotations.SerializedName

data class AppUpdateResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("releaseNotes") val releaseNotes: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("sizeBytes") val sizeBytes: Long? = null
) {
    internal fun isNewerThan(installedVersionName: String, installedVersionCode: Int): Boolean {
        val remote = parseVersion(versionName)
        val installed = parseVersion(installedVersionName)
        if (remote != null && installed != null) {
            for (index in 0..maxOf(remote.lastIndex, installed.lastIndex)) {
                val difference = remote.getOrElse(index) { 0 }.compareTo(installed.getOrElse(index) { 0 })
                if (difference != 0) return difference > 0
            }
            return false
        }

        // A numeric release tag can identify an Android build directly.
        val taggedBuild = versionName.trim().removePrefix("v").removePrefix("V").toIntOrNull()
        return taggedBuild != null && taggedBuild == versionCode && versionCode > installedVersionCode
    }

    private fun parseVersion(value: String): List<Int>? {
        val parts = value.trim().removePrefix("v").removePrefix("V").substringBefore('+').split('.')
        if (parts.size !in 2..3) return null
        return parts.map { part ->
            if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
            part.toIntOrNull() ?: return null
        }
    }
}
