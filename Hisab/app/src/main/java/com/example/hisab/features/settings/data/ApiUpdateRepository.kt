package com.example.hisab.features.settings.data

import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.settings.domain.UpdateRepository
import com.google.gson.JsonParseException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiUpdateRepository @Inject constructor(
    private val apiService: HisabApiService
) : UpdateRepository {
    override suspend fun getLatestRelease(): AppUpdateResponse? {
        val response = apiService.getLatestRelease()
        if (!response.isSuccessful) throw HttpException(response)
        if (response.code() == 204) return null
        return response.body() ?: throw JsonParseException("Missing update metadata")
    }
}
