package com.pumpkin.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumpkin.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditNameViewModel(
    private val authRepository: AuthRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            _currentName.value = authRepository.getUser(currentUserId)?.displayName.orEmpty()
        }
    }

    fun save(newName: String) {
        if (newName.isBlank()) {
            _error.value = "Name can't be empty"
            return
        }
        viewModelScope.launch {
            try {
                // NonCancellable so navigating away right after tapping Save
                // can't truncate the request mid-flight (see AuthRepository
                // kdoc for why this class of bug keeps recurring here).
                withContext(NonCancellable) {
                    authRepository.updateDisplayName(newName)
                }
                _error.value = null
                _saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PumpkinEditName", "save failed", e)
                _error.value = e.javaClass.simpleName + ": " + (e.message ?: "Couldn't save name")
            }
        }
    }
}
