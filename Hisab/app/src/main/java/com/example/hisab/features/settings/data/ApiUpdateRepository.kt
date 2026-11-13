package com.example.hisab.features.settings.data

import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.settings.domain.UpdateRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiUpdateRepository @Inject constructor(
    private val apiService: HisabApiService
) : UpdateRepository {
    override suspend fun getLatestRelease(): AppUpdateResponse {
        return apiService.getLatestRelease()
    }
}
