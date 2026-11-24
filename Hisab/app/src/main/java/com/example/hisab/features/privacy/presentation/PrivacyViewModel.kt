package com.example.hisab.features.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.privacy.data.PrivacyDocument
import com.example.hisab.features.privacy.data.PrivacySource
import com.example.hisab.features.privacy.data.PrivacyStatus
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import com.example.hisab.util.SessionCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

enum class PrivacyGate { Checking, Continue, Review, Unavailable }

data class PrivacyState(
    val gate: PrivacyGate = PrivacyGate.Checking,
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val privacy: PrivacySource,
    private val session: SessionManager,
    private val tokens: SessionCredentials,
    private val failures: FailureReporter
) : ViewModel() {
    private val mutableState = MutableStateFlow(PrivacyState())
    val state = mutableState.asStateFlow()
    val document = privacy.policy
    private var statusJob: Job? = null
    private var writingJob: Job? = null
    private var activeAccount: String? = null

    init {
        viewModelScope.launch {
            session.currentUserId.combine(session.privacyReviewNeeded, ::Pair).distinctUntilChanged().collect { (id, requiresReview) ->
                if (id != activeAccount) {
                    activeAccount = id
                    statusJob?.cancel()
                    writingJob?.cancel()
                    mutableState.value = PrivacyState()
                }
                if (id != null && (requiresReview || state.value.gate == PrivacyGate.Checking)) checkStatus()
            }
        }
    }

    fun viewPolicy() { viewModelScope.launch { privacy.refreshPolicy() } }

    fun checkStatus() {
        val userId = session.currentUserId.value ?: return
        if (statusJob?.isActive == true) return
        statusJob = viewModelScope.launch {
            mutableState.update { it.copy(gate = PrivacyGate.Checking, error = null) }
            try {
                withTimeout(12_000) { applyStatus(userId, privacy.status()) }
            } catch (_: TimeoutCancellationException) {
                statusUnavailable(userId, SocketTimeoutException())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { statusUnavailable(userId, e) }
        }
    }

    private suspend fun statusUnavailable(userId: String, error: Exception) {
        if (!current(userId)) return
        val cached = tokens.cachedPrivacyVersion(userId)
        val bundled = try { privacy.bundledPolicy().acknowledgmentVersion }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        val offlineAllowed = !session.privacyReviewNeeded.value && cached != null &&
            (error is IOException || error is HttpException && error.code() >= 500) && cached == bundled &&
            (privacy.policy.value == null || cached == privacy.policy.value?.acknowledgmentVersion)
        if (!current(userId)) return
        val failure = failures.report("Load privacy status", error)
        mutableState.update { it.copy(gate = if (offlineAllowed) PrivacyGate.Continue else PrivacyGate.Unavailable,
            error = failure.message.takeUnless { offlineAllowed }) }
    }

    fun acknowledge(policy: PrivacyDocument) {
        val userId = session.currentUserId.value ?: return
        if (state.value.saving || state.value.gate != PrivacyGate.Review || document.value?.version != policy.version) return
        mutableState.update { it.copy(saving = true, error = null) }
        writingJob = viewModelScope.launch {
            try {
                withTimeout(12_000) { applyStatus(userId, privacy.acknowledge(policy.version)) }
            } catch (_: TimeoutCancellationException) {
                failures.report("Acknowledge privacy policy", SocketTimeoutException())
                if (current(userId)) mutableState.update { it.copy(error = "Couldn't confirm your acknowledgment yet. Please retry.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!current(userId)) return@launch
                val failure = failures.report("Acknowledge privacy policy", e)
                if (e is HttpException && e.code() == 409) {
                    checkStatus()
                } else mutableState.update { it.copy(error = failure.message) }
            } finally {
                if (current(userId)) mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun signOut() {
        tokens.clearAll()
        session.clear()
    }

    private suspend fun applyStatus(userId: String, status: PrivacyStatus) {
        if (!current(userId)) return
        require(status.acknowledgmentVersion.isNotBlank())
        tokens.cachePrivacyVersion(userId, status.acknowledgmentVersion.takeIf { status.acknowledged })
        if (status.acknowledged) {
            session.completePrivacyReview()
            mutableState.update { it.copy(gate = PrivacyGate.Continue, error = null) }
        } else {
            val latest = privacy.refreshPolicy()
            if (current(userId)) mutableState.update {
                if (latest.acknowledgmentVersion == status.acknowledgmentVersion) it.copy(gate = PrivacyGate.Review, error = null)
                else it.copy(gate = PrivacyGate.Unavailable, error = "The latest policy isn't available. Connect and retry.")
            }
        }
    }

    private fun current(userId: String) = session.currentUserId.value == userId
}
