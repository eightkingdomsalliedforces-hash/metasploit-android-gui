package dev.mago.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.datastore.AppLockSettingsStore
import dev.mago.android.security.AppLockEvent
import dev.mago.android.security.AppLockState
import dev.mago.android.security.AppLockStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppLockAuthPurpose {
    UNLOCK,
    ENABLE,
    DISABLE,
}

data class AppLockUiState(
    val lock: AppLockState = AppLockState(),
    val pendingAuthPurpose: AppLockAuthPurpose? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val initialized: Boolean get() = lock.initialized
    val enabled: Boolean get() = lock.enabled
    val locked: Boolean get() = lock.locked
}

class AppLockViewModel(
    private val settingsStore: AppLockSettingsStore,
    private val stateMachine: AppLockStateMachine = AppLockStateMachine(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.enabled
                .catch { exception ->
                    _uiState.update {
                        it.copy(errorMessage = exception.message ?: "無法讀取 App 鎖設定")
                    }
                }
                .collect { enabled ->
                    _uiState.update { current ->
                        current.copy(
                            lock = stateMachine.reduce(
                                current.lock,
                                AppLockEvent.SettingsLoaded(enabled),
                            ),
                        )
                    }
                }
        }
    }

    fun onBackgrounded() {
        _uiState.update { current ->
            current.copy(
                lock = stateMachine.reduce(current.lock, AppLockEvent.AppBackgrounded),
            )
        }
    }

    fun beginAuthentication(purpose: AppLockAuthPurpose) {
        _uiState.update { current ->
            if (current.pendingAuthPurpose != null || current.saving) {
                current
            } else {
                current.copy(pendingAuthPurpose = purpose, errorMessage = null)
            }
        }
    }

    fun onAuthenticationSucceeded() {
        val purpose = _uiState.value.pendingAuthPurpose ?: return
        _uiState.update { current ->
            current.copy(
                lock = stateMachine.reduce(current.lock, AppLockEvent.AuthenticationSucceeded),
                pendingAuthPurpose = null,
                errorMessage = null,
            )
        }
        when (purpose) {
            AppLockAuthPurpose.UNLOCK -> Unit
            AppLockAuthPurpose.ENABLE -> persistEnabled(true)
            AppLockAuthPurpose.DISABLE -> persistEnabled(false)
        }
    }

    fun onAuthenticationAttemptFailed() {
        _uiState.update { current ->
            current.copy(
                lock = stateMachine.reduce(current.lock, AppLockEvent.AuthenticationFailed),
                errorMessage = "驗證未通過，請再試一次。",
            )
        }
    }

    fun onAuthenticationError(message: String) {
        _uiState.update { current ->
            current.copy(
                lock = stateMachine.reduce(current.lock, AppLockEvent.AuthenticationFailed),
                pendingAuthPurpose = null,
                errorMessage = message,
            )
        }
    }

    private fun persistEnabled(enabled: Boolean) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { settingsStore.setEnabled(enabled) }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            errorMessage = exception.message ?: "無法儲存 App 鎖設定",
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { it.copy(saving = false, errorMessage = null) }
                }
        }
    }

    companion object {
        fun factory(settingsStore: AppLockSettingsStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppLockViewModel(settingsStore) as T
            }
    }
}
