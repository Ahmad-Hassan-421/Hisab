package com.example.hisab.features.privacy.data

import android.content.Context
import com.example.hisab.data.remote.api.HisabApiService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PrivacySection(val title: String, val body: String)
data class PrivacyDocument(val version: String, val acknowledgmentVersion: String, val effectiveDate: String, val sections: List<PrivacySection>)
data class PrivacyStatus(val acknowledged: Boolean, val acknowledgmentVersion: String, val acceptedAt: Long?)
data class PolicyAcknowledgment(val version: String)

interface PrivacySource {
    val policy: StateFlow<PrivacyDocument?>
    suspend fun bundledPolicy(): PrivacyDocument
    suspend fun refreshPolicy(): PrivacyDocument
    suspend fun status(): PrivacyStatus
    suspend fun acknowledge(version: String): PrivacyStatus
}

@Singleton
class PrivacyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HisabApiService
) : PrivacySource {
    private val mutablePolicy = MutableStateFlow<PrivacyDocument?>(null)
    override val policy = mutablePolicy.asStateFlow()

    override suspend fun bundledPolicy(): PrivacyDocument = withContext(Dispatchers.IO) {
        context.assets.open("privacy-policy.json").bufferedReader().use {
            Gson().fromJson(it, PrivacyDocument::class.java)
        }
    }

    override suspend fun refreshPolicy(): PrivacyDocument {
        val document = try {
            withTimeoutOrNull(5_000) { api.getPrivacyPolicy() } ?: (mutablePolicy.value ?: bundledPolicy())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutablePolicy.value ?: bundledPolicy()
        }
        mutablePolicy.value = document
        return document
    }

    override suspend fun status(): PrivacyStatus = api.getPrivacyStatus()
    override suspend fun acknowledge(version: String): PrivacyStatus = api.acknowledgePrivacy(PolicyAcknowledgment(version))
}
