package com.example.hisab.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.User
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.auth.data.AuthResponse
import com.example.hisab.features.auth.data.LoginRequest
import com.example.hisab.features.auth.data.RegisterRequest
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.Constants
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import com.example.hisab.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isLoginMode: Boolean = true
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val tokenManager: TokenManager,
    private val apiService: HisabApiService,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        val savedUserId = tokenManager.getUserId()
        if (savedUserId != null) {
            viewModelScope.launch {
                val localUser = try {
                    userRepository.getCurrentUser(savedUserId).first()
                } catch (e: Exception) {
                    failures.report("Read saved account", e)
                    null
                }
                val restored = tokenManager.getUserId() == savedUserId
                if (restored) sessionManager.setSession(savedUserId, localUser?.role ?: "user")
                _uiState.update { it.copy(isSignedIn = restored, isLoading = false) }

                try {
                    val remoteUser = userRepository.fetchRemoteUser(savedUserId, allowCached = false)
                    if (restored && remoteUser != null && tokenManager.getUserId() == savedUserId) {
                        sessionManager.setSession(savedUserId, remoteUser.role)
                    }
                } catch (e: Exception) {
                    failures.report("Refresh saved account", e)
                }
            }
        } else {
            sessionManager.clear()
            _uiState.update { it.copy(isSignedIn = false, isLoading = false) }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, error = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                error = null,
                successMessage = null,
                confirmPassword = "",
                name = ""
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return
        if (!validateEmail(state.email)) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your password") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                establishSession(apiService.login(LoginRequest(state.email, state.password)))
                _uiState.update {
                    it.copy(isLoading = false, isSignedIn = true, password = "", confirmPassword = "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Sign in", e)
                val message = if (failure == AppFailure.SESSION_EXPIRED) "The email or password is incorrect." else failure.message
                _uiState.update {
                    it.copy(isLoading = false, error = message)
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your name") }
            return
        }
        if (!validateEmail(state.email)) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return
        }
        if (!validatePassword(state.password)) {
            _uiState.update { it.copy(error = "Password must be at least 8 characters, contain a letter, a number and a special character") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                establishSession(apiService.register(RegisterRequest(state.name, state.email, state.password)))

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = true,
                        password = "",
                        confirmPassword = "",
                        successMessage = "Account created."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Create account", e)
                val userFriendlyMsg = if (failure == AppFailure.CONFLICT) "An account with this email already exists." else failure.message
                _uiState.update {
                    it.copy(isLoading = false, error = userFriendlyMsg)
                }
            }
        }
    }

    private suspend fun establishSession(response: AuthResponse) {
        val dto = response.user
        val rawPic = dto.profilePicUrl
        val finalPic = if (rawPic.isNullOrBlank() || rawPic.endsWith("default_profile.png")) {
            Constants.DEFAULT_GOOGLE_AVATAR_URL
        } else {
            rawPic
        }
        val user = User(
            id = dto.id,
            name = dto.name,
            email = dto.email,
            profilePicUrl = finalPic,
            themePreference = dto.themePreference ?: "system",
            role = dto.role ?: "user",
            uniqueId = dto.uniqueId.orEmpty(),
            createdAt = dto.createdAt ?: System.currentTimeMillis(),
            updatedAt = dto.updatedAt ?: System.currentTimeMillis()
        )
        userRepository.createUserDocument(user)
        tokenManager.saveSession(response.token, user.id)
        sessionManager.setSession(user.id, user.role)
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearAll()
            sessionManager.clear()
            _uiState.update {
                AuthUiState(isSignedIn = false)
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasDigit && hasSpecial
    }
}
