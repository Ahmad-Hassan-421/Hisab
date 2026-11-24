package com.example.hisab.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class StartupState(val animationFinished: Boolean = false, val destination: String? = null)

/** Retained through rotation, recreated for a fresh process, and never changes an entered graph. */
@HiltViewModel
class StartupViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(StartupState())
    val state = mutableState.asStateFlow()
    fun finishAnimation() = mutableState.update { it.copy(animationFinished = true) }
    fun enter(destination: String) = mutableState.update {
        if (it.destination == null) it.copy(destination = destination) else it
    }
}
