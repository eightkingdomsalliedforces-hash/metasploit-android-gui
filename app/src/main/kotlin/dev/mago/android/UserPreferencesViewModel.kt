package dev.mago.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.datastore.FontScale
import dev.mago.android.datastore.ThemeMode
import dev.mago.android.datastore.UserPreferences
import dev.mago.android.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserPreferencesUiState(
    val preferences: UserPreferences = UserPreferences(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: String? = null,
)

class UserPreferencesViewModel(
    private val repository: UserPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserPreferencesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferences
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            loaded = true,
                            errorMessage = exception.message ?: "無法讀取顯示設定",
                        )
                    }
                }
                .collect { preferences ->
                    _uiState.update {
                        it.copy(preferences = preferences, loaded = true, saving = false)
                    }
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) = save { repository.setThemeMode(mode) }

    fun setFontScale(scale: FontScale) = save { repository.setFontScale(scale) }

    fun setReducedMotion(enabled: Boolean) = save { repository.setReducedMotion(enabled) }

    private fun save(block: suspend () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            errorMessage = exception.message ?: "無法儲存顯示設定",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: UserPreferencesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UserPreferencesViewModel(repository) as T
            }
    }
}
