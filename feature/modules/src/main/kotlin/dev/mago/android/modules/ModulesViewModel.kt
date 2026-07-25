package dev.mago.android.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModulesUiState(
    val type: MetasploitModuleType = MetasploitModuleType.EXPLOIT,
    val query: String = "",
    val modules: List<MetasploitModuleSummary> = emptyList(),
    val selected: MetasploitModuleInfo? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleModules: List<MetasploitModuleSummary>
        get() = if (query.isBlank()) modules else modules.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }
}

class ModulesViewModel(
    private val repository: MetasploitModuleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState = _uiState.asStateFlow()

    fun selectType(type: MetasploitModuleType) {
        if (type == _uiState.value.type && _uiState.value.modules.isNotEmpty()) return
        loadType(type)
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun selectModule(module: MetasploitModuleSummary) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            when (val result = repository.info(module.type, module.name)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, selected = result.value)
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = null) }
    }

    fun retry() {
        loadType(_uiState.value.type)
    }

    private fun loadType(type: MetasploitModuleType) {
        viewModelScope.launch {
            _uiState.value = ModulesUiState(type = type, loading = true)
            when (val result = repository.list(type)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, modules = result.value)
                }
            }
        }
    }

    companion object {
        fun factory(repository: MetasploitModuleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModulesViewModel(repository) as T
            }
    }
}
