package com.example.hisab.features.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val entries: List<HisabEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val hisabRepository: HisabRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = sessionManager.isAdmin

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadGlobalEntries()
    }

    private fun loadGlobalEntries() {
        viewModelScope.launch {
            hisabRepository.getAllEntries()
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load entries")
                    }
                }
                .collect { entries ->
                    _uiState.update { it.copy(entries = entries, isLoading = false) }
                }
        }
    }
}