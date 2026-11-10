package com.example.hisab.features.profile.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val uniqueId: String = "",
    val profilePicUrl: String = "",
    val isDarkMode: Boolean = false,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val themePreferencesManager: ThemePreferencesManager,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            themePreferencesManager.themeMode.collect { mode ->
                val isDark = mode == ThemeMode.DARK
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }

        viewModelScope.launch {
            sessionManager.currentUserId.collect { userId ->
                if (userId != null && userId.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            userRepository.fetchRemoteUser(userId)
                        } catch (e: Exception) {}
                    }
                    userRepository.getCurrentUser(userId).collect { user ->
                        if (user != null) {
                            _uiState.update {
                                it.copy(
                                    name = user.name,
                                    email = user.email,
                                    uniqueId = user.uniqueId,
                                    profilePicUrl = user.profilePicUrl,
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch {
            val mode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
            themePreferencesManager.setThemeMode(mode)
            
            val userId = sessionManager.currentUserId.value ?: return@launch
            try {
                val user = userRepository.fetchRemoteUser(userId)
                if (user != null) userRepository.updateUser(user.copy(themePreference = mode.name.lowercase()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Theme changed on this device. Profile sync failed.") }
            }
        }
    }

    fun updateProfile() {
        val userId = sessionManager.currentUserId.value ?: return
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val user = userRepository.fetchRemoteUser(userId)
                if (user != null) {
                    userRepository.updateUser(user.copy(name = state.name))
                    _uiState.update { it.copy(isLoading = false, successMessage = "Profile updated successfully") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        val userId = sessionManager.currentUserId.value ?: return
        if (_uiState.value.isUploading) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, successMessage = null) }
            try {
                val user = checkNotNull(userRepository.fetchRemoteUser(userId)) { "Profile is unavailable" }
                val url = userRepository.uploadProfilePicture(uri)
                userRepository.updateUser(user.copy(profilePicUrl = url))
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        profilePicUrl = url,
                        successMessage = "Profile picture updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        error = "Failed to upload picture: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    fun logout() {
        tokenManager.clearAll()
        sessionManager.clear()
    }
}
