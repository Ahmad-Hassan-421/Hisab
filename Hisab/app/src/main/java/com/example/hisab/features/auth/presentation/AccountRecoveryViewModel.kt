package com.example.hisab.features.auth.presentation

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.auth.data.PasswordResetRequest
import com.example.hisab.features.auth.data.RecoveryEmailConfirmation
import com.example.hisab.features.auth.data.RecoveryRequest
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject

enum class RecoveryStep { Request, Sent, EnterCode, Complete }

data class AccountRecoveryState(
    val step: RecoveryStep = RecoveryStep.Request,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmation: String = "",
    val checkingVerification: Boolean = false,
    val verificationStatusLoaded: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountRecoveryViewModel @Inject constructor(
    private val api: HisabApiService,
    private val failures: FailureReporter
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountRecoveryState())
    val state = mutableState.asStateFlow()

    fun emailChanged(email: String) = mutableState.update { it.copy(email = email, error = null) }
    fun codeChanged(code: String) = mutableState.update { it.copy(code = code.trim(), error = null) }
    fun passwordChanged(password: String) = mutableState.update { it.copy(password = password, error = null) }
    fun confirmationChanged(confirmation: String) = mutableState.update { it.copy(confirmation = confirmation, error = null) }

    fun enterCode() = mutableState.update { it.copy(step = RecoveryStep.EnterCode, error = null) }

    fun requestAgain() = mutableState.update {
        it.copy(step = RecoveryStep.Request, error = null, code = "", password = "", confirmation = "")
    }

    fun requestReset() {
        val email = state.value.email.trim()
        if (!validEmail(email)) return showError("Enter your registered email address.")
        perform("Request password reset") {
            api.requestPasswordRecovery(RecoveryRequest(email)).requireSuccess()
            mutableState.update { it.copy(step = RecoveryStep.Sent, email = email) }
        }
    }

    fun resetPassword() {
        val form = state.value
        val email = form.email.trim()
        if (!validEmail(email)) return showError("Enter your registered email address.")
        if (!validCode(form.code)) return showError("Paste the full reset code from the email.")
        if (!validNewPassword(form.password)) return showError("Use at least 8 characters with a letter, number and special character. Keep it within 72 bytes.")
        if (form.password != form.confirmation) return showError("Passwords do not match.")
        perform("Reset password", "That reset code is invalid or expired. Check the code or request a new one.") {
            api.resetPassword(PasswordResetRequest(email, form.code, form.password)).requireSuccess()
            complete()
        }
    }

    fun loadVerification() {
        if (state.value.verificationStatusLoaded || state.value.checkingVerification) return
        mutableState.update { it.copy(checkingVerification = true, error = null) }
        viewModelScope.launch {
            try {
                val verified = api.getRecoveryEmailStatus().verified
                mutableState.update { it.copy(verificationStatusLoaded = true,
                    step = if (verified) RecoveryStep.Complete else RecoveryStep.Request) }
            } catch (error: Exception) {
                showError(message("Load recovery email", error, null))
            } finally {
                mutableState.update { it.copy(checkingVerification = false) }
            }
        }
    }

    fun requestVerification() = perform("Request recovery email verification") {
        val response = api.requestRecoveryEmailVerification()
        response.requireSuccess()
        if (response.code() == 204) complete()
        else mutableState.update { it.copy(step = RecoveryStep.EnterCode) }
    }

    fun verifyEmail() {
        val code = state.value.code
        if (!validCode(code)) return showError("Paste the full verification code from the email.")
        perform("Verify recovery email", "That verification code is invalid or expired. Check the code or request a new one.") {
            api.verifyRecoveryEmail(RecoveryEmailConfirmation(code)).requireSuccess()
            complete()
        }
    }

    private fun perform(action: String, invalidCode: String? = null, operation: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                showError(message(action, error, invalidCode))
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun showError(message: String) = mutableState.update { it.copy(error = message) }

    private fun complete() = mutableState.update {
        it.copy(step = RecoveryStep.Complete, code = "", password = "", confirmation = "", error = null)
    }

    private fun message(action: String, error: Exception, invalidCode: String?): String {
        val failure = failures.report(action, error)
        return when ((error as? HttpException)?.code()) {
            400 -> invalidCode ?: failure.message
            429 -> "Too many attempts. Try again later."
            503 -> "Account recovery is temporarily unavailable. Try again later."
            else -> failure.message
        }
    }

    private fun validEmail(email: String) = email.length <= 254 && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    private fun validCode(code: String) = code.length == 43 && code.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }

    private fun Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }
}

internal fun validNewPassword(password: String) = password.length >= 8 && password.toByteArray(Charsets.UTF_8).size <= 72 &&
    password.any(Char::isLetter) && password.any(Char::isDigit) && password.any { !it.isLetterOrDigit() }
