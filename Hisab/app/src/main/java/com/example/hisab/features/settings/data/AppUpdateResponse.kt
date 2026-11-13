package com.example.hisab.features.settings.data

import com.google.gson.annotations.SerializedName

data class AppUpdateResponse(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("releaseNotes") val releaseNotes: String,
    @SerializedName("downloadUrl") val downloadUrl: String
)
