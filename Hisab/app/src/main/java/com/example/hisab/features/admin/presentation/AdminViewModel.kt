package com.example.hisab.features.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val entries: List<HisabEntry> = emptyList(),
    val isLoading: Boolean = true,
    val failure: AppFailure? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val hisabRepository: HisabRepository,
    sessionManager: SessionManager,
    private val failures: FailureReporter
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = sessionManager.isAdmin

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init {
        loadGlobalEntries()
    }

    fun loadGlobalEntries() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            hisabRepository.getAllEntries()
                .catch { e ->
                    val failure = failures.report("Read cached ledger", e)
                    _uiState.update {
                        it.copy(isLoading = false, failure = failure)
                    }
                }
                .collect { entries ->
                    _uiState.update { it.copy(entries = entries, isLoading = false, failure = null) }
                }
        }
    }
}
