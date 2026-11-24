package com.example.hisab.features.profile.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.User
import com.example.hisab.features.profile.data.LocalProfilePhoto
import com.example.hisab.features.profile.data.LocalProfilePhotoStore
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.TokenManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val accountId: String? = null,
    val name: String = "",
    val email: String = "",
    val uniqueId: String = "",
    val profilePicUrl: String = "",
    val localPhoto: LocalProfilePhoto = LocalProfilePhoto.Unset,
    val isDarkMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploading: Boolean = false,
    val loadFailure: AppFailure? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val themePreferencesManager: ThemePreferencesManager,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val photos: LocalProfilePhotoStore,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var profileLoad: Job? = null
    private var photoWrite: Job? = null
    private var nameEdited = false

    init {
        viewModelScope.launch {
            themePreferencesManager.themeMode.collect { mode ->
                val isDark = mode == ThemeMode.DARK
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }

        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                profileLoad?.cancel()
                photoWrite?.cancel()
                nameEdited = false
                _uiState.update { ProfileUiState(accountId = userId, isDarkMode = it.isDarkMode) }
                if (userId != null) loadUserProfile()
            }
        }
    }

    fun loadUserProfile() {
        val userId = sessionManager.currentUserId.value ?: return
        if (profileLoad?.isActive == true) return
        profileLoad = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val writeAtStart = photoWrite
                val photo = try {
                    photos.load(userId)
                } catch (e: java.io.IOException) {
                    failures.report("Read profile picture", e)
                    LocalProfilePhoto.Removed
                }
                if (sessionManager.currentUserId.value != userId) return@launch
                if (photoWrite === writeAtStart && !_uiState.value.isUploading) {
                    _uiState.update { it.copy(localPhoto = photo) }
                }
                userRepository.getCurrentUser(userId).first()?.let(::showProfile)
                val user = userRepository.fetchRemoteUser(userId, allowCached = false)
                if (sessionManager.currentUserId.value != userId) return@launch
                if (user != null) showProfile(user)
                _uiState.update { it.copy(isLoading = false, loadFailure = if (user == null) AppFailure.MISSING else null) }
            } catch (e: Exception) {
                val failure = failures.report("Load profile", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isLoading = false, loadFailure = failure) }
                }
            }
        }
    }

    private fun showProfile(user: User) {
        if (user.id != sessionManager.currentUserId.value) return
        _uiState.update {
            it.copy(name = if (nameEdited) it.name else user.name, email = user.email,
                uniqueId = user.uniqueId, profilePicUrl = user.profilePicUrl)
        }
    }

    fun onNameChange(newName: String) {
        nameEdited = true
        _uiState.update { it.copy(name = newName) }
    }

    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch {
            val mode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
            val userId = sessionManager.currentUserId.value ?: return@launch
            try {
                themePreferencesManager.setThemeMode(mode)
                val user = userRepository.fetchRemoteUser(userId, allowCached = false)
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
        if (state.accountId != userId || state.isSaving || state.isUploading) return

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
            try {
                val user = userRepository.fetchRemoteUser(userId, allowCached = false)
                if (sessionManager.currentUserId.value != userId) return@launch
                if (user == null) {
                    _uiState.update { it.copy(isSaving = false, error = AppFailure.MISSING.message) }
                    return@launch
                }
                userRepository.updateUser(user.copy(name = state.name))
                if (sessionManager.currentUserId.value != userId) return@launch
                if (_uiState.value.name == state.name) nameEdited = false
                _uiState.update { it.copy(isSaving = false, successMessage = "Profile updated successfully") }
            } catch (e: Exception) {
                val failure = failures.report("Save profile", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isSaving = false, error = failure.message) }
                }
            }
        }
    }

    fun saveProfilePicture(accountId: String, uri: Uri) = changePhoto(accountId) { photos.save(accountId, uri) }

    fun removeProfilePicture() {
        val accountId = sessionManager.currentUserId.value ?: return
        changePhoto(accountId) {
            photos.remove(accountId)
            LocalProfilePhoto.Removed
        }
    }

    private fun changePhoto(accountId: String, change: suspend () -> LocalProfilePhoto) {
        if (sessionManager.currentUserId.value != accountId || _uiState.value.accountId != accountId) return
        if (_uiState.value.isUploading || _uiState.value.isSaving) return

        _uiState.update { it.copy(isUploading = true, error = null, successMessage = null) }
        photoWrite = viewModelScope.launch {
            try {
                val photo = change()
                if (sessionManager.currentUserId.value != accountId) return@launch
                _uiState.update {
                    it.copy(isUploading = false, localPhoto = photo, successMessage =
                        if (photo == LocalProfilePhoto.Removed) "Picture reset on this device" else "Picture saved on this device")
                }
            } catch (e: Exception) {
                val message = if (e is LocalProfilePhotoStore.InvalidPhoto) {
                    "Couldn’t read that crop. Choose a different picture."
                } else {
                    failures.report("Save profile picture", e)
                    "Couldn’t save your picture on this device. Try again."
                }
                if (sessionManager.currentUserId.value != accountId) return@launch
                _uiState.update {
                    it.copy(isUploading = false, error = message)
                }
            }
        }
    }

    fun reportImageSelectionError() {
        _uiState.update { it.copy(error = "Couldn’t open that picture. Choose a different image and try again.") }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    fun logout() {
        tokenManager.clearAll()
        sessionManager.clear()
    }
}
