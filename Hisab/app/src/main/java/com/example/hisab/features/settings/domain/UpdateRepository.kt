package com.example.hisab.features.settings.domain

import com.example.hisab.features.settings.data.AppUpdateResponse

interface UpdateRepository {
    suspend fun getLatestRelease(): AppUpdateResponse
}
