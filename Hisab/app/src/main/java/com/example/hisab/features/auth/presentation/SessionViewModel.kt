package com.example.hisab.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.TokenManager
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager,
    private val failures: FailureReporter
) : ViewModel() {

    val currentUserId: StateFlow<String?> = sessionManager.currentUserId
    val role: StateFlow<String?> = sessionManager.role
    val isAdmin: StateFlow<Boolean> = sessionManager.isAdmin
    val requiresSignIn: StateFlow<Boolean> = sessionManager.requiresSignIn
    
    init {
        checkAutoLogin()
    }
    
    private fun checkAutoLogin() {
        val userId = tokenManager.getUserId()
        if (userId != null) {
            viewModelScope.launch {
                // 1. Immediately hydrate session from local cached user in Room on cold start
                val localUser = try {
                    userRepository.getCurrentUser(userId).first()
                } catch (e: Exception) {
                    failures.report("Read saved session profile", e)
                    null
                }
                if (tokenManager.getUserId() == userId) {
                    sessionManager.setSession(userId = userId, role = localUser?.role ?: "user")
                }
                // 2. Fetch latest remote state in background and update session
                try {
                    val remoteUser = userRepository.fetchRemoteUser(userId, allowCached = false)
                    if (remoteUser != null && tokenManager.getUserId() == userId) {
                        sessionManager.setSession(userId = userId, role = remoteUser.role)
                    }
                } catch (e: Exception) {
                    failures.report("Restore session profile", e)
                }
            }
        }
    }
}
