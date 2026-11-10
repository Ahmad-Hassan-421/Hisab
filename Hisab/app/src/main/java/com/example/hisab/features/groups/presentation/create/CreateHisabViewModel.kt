package com.example.hisab.features.groups.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateHisabUiState(
    val hisabName: String = "",
    val isLoading: Boolean = false,
    val successGroupId: String? = null,
    val error: String? = null
)

@HiltViewModel
class CreateHisabViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateHisabUiState())
    val uiState: StateFlow<CreateHisabUiState> = _uiState.asStateFlow()

    fun onHisabNameChange(name: String) {
        _uiState.update { it.copy(hisabName = name, error = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successGroupId = null) }
    }

    fun createHisab() {
        val state = _uiState.value
        if (state.hisabName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a Hisab name") }
            return
        }

        val userId = sessionManager.currentUserId.value ?: return

        _uiState.update { it.copy(isLoading = true, error = null, successGroupId = null) }

        viewModelScope.launch {
            try {
                val group = groupRepository.createGroup(state.hisabName, listOf(userId))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successGroupId = group.id,
                        hisabName = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create Hisab"
                    )
                }
            }
        }
    }
}
