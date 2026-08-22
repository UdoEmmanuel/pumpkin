package com.pumpkin.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpkin.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) = launchAuthAction {
        repository.signInWithEmail(email, password)
    }

    fun signUp(email: String, password: String, displayName: String) = launchAuthAction {
        repository.signUpWithEmail(email, password, displayName)
    }

    fun signInWithGoogle(idToken: String) = launchAuthAction {
        repository.signInWithGoogleIdToken(idToken)
    }

    private fun launchAuthAction(block: suspend () -> Unit) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                block()
                _state.value = AuthUiState.Idle
                // Navigation on success is driven by PumpkinNavHost observing
                // AuthRepository.observeAuthState(), not by this state — keeps
                // "is the user signed in" as a single source of truth.
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }
}
