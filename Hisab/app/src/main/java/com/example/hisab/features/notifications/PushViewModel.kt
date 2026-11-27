package com.example.hisab.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PushUiState(val owner: String? = null, val asked: Boolean = true, val optedIn: Boolean = false,
    val preferences: PushPreferences = PushPreferences(), val loading: Boolean = false, val saving: Boolean = false,
    val loaded: Boolean = false, val configured: Boolean = true, val error: String? = null)

@HiltViewModel
class PushViewModel @Inject constructor(private val api: HisabApiService, private val session: SessionManager,
    private val local: PushState, private val coordinator: PushCoordinator, private val failures: FailureReporter) : ViewModel() {
    private val mutable = MutableStateFlow(PushUiState())
    val state = mutable.asStateFlow()
    private var loading: Job? = null
    private var saving: Job? = null
    init {
        viewModelScope.launch { session.currentUserId.collectLatest { owner ->
            loading?.cancel(); saving?.cancel()
            mutable.value = if (owner == null) PushUiState() else PushUiState(owner, local.asked(owner), local.enabled(owner), local.preferences(owner))
            if (owner != null) refresh()
        } }
        viewModelScope.launch { local.revision.collect {
            val owner = session.currentUserId.value ?: return@collect
            mutable.update { it.copy(asked = local.asked(owner), optedIn = local.enabled(owner), preferences = local.preferences(owner)) }
        } }
    }
    fun refresh() {
        val owner = session.currentUserId.value ?: return
        if (loading?.isActive == true) return
        mutable.update { it.copy(loading = true, error = null) }
        loading = viewModelScope.launch {
            try {
                val value = api.getPushPreferences()
                if (session.currentUserId.value == owner) {
                    local.savePreferences(owner, value.preferences)
                    mutable.update { it.copy(preferences = value.preferences, loaded = true, configured = value.deliveryConfigured) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (session.currentUserId.value == owner) mutable.update { it.copy(error = failures.report("Load notification preferences", error).message) } }
            finally { if (session.currentUserId.value == owner) mutable.update { it.copy(loading = false) } }
        }
    }
    fun decide(enabled: Boolean) {
        val owner = session.currentUserId.value ?: return
        coordinator.decide(owner, enabled)
    }
    fun save(value: PushPreferences) {
        val owner = session.currentUserId.value ?: return
        if (mutable.value.saving || !mutable.value.loaded) return
        mutable.update { it.copy(saving = true, error = null) }
        saving = viewModelScope.launch {
            try {
                val response = api.savePushPreferences(value, owner)
                if (session.currentUserId.value == owner) {
                    local.savePreferences(owner, response.preferences)
                    mutable.update { it.copy(preferences = response.preferences, configured = response.deliveryConfigured) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (session.currentUserId.value == owner) mutable.update { it.copy(error = failures.report("Save notification preferences", error).message) } }
            finally { if (session.currentUserId.value == owner) mutable.update { it.copy(saving = false) } }
        }
    }
}
