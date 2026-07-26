package dev.mago.android.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleRunConfirmation(
    val action: MetasploitModuleRunAction,
    val request: MetasploitModuleRequest,
    val redactedOptions: Map<String, String>,
)

data class ModulesUiState(
    val type: MetasploitModuleType = MetasploitModuleType.EXPLOIT,
    val query: String = "",
    val modules: List<MetasploitModuleSummary> = emptyList(),
    val selected: MetasploitModuleInfo? = null,
    val optionValues: Map<String, String> = emptyMap(),
    val validationErrors: Map<String, String> = emptyMap(),
    val compatiblePayloads: List<String> = emptyList(),
    val confirmation: ModuleRunConfirmation? = null,
    val launch: MetasploitModuleLaunch? = null,
    val runResult: MetasploitModuleRunResult? = null,
    val runLoading: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val runErrorMessage: String? = null,
) {
    val visibleModules: List<MetasploitModuleSummary>
        get() = if (query.isBlank()) modules else modules.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }

    val canCheck: Boolean
        get() = selected?.let {
            it.hasCheck && it.type in setOf(MetasploitModuleType.EXPLOIT, MetasploitModuleType.AUXILIARY)
        } == true

    val canExecute: Boolean
        get() = selected?.type in setOf(
            MetasploitModuleType.EXPLOIT,
            MetasploitModuleType.AUXILIARY,
            MetasploitModuleType.POST,
            MetasploitModuleType.PAYLOAD,
            MetasploitModuleType.EVASION,
        )
}

class ModulesViewModel(
    private val repository: MetasploitModuleRepository,
    private val validator: ModuleRunValidator = ModuleRunValidator(),
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
            _uiState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                    runErrorMessage = null,
                    confirmation = null,
                    launch = null,
                    runResult = null,
                )
            }
            when (val result = repository.info(module.type, module.name)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> {
                    val info = result.value
                    val defaults = info.options.mapNotNull { option ->
                        option.defaultValue?.let { option.name to it }
                    }.toMap()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            selected = info,
                            optionValues = defaults,
                            validationErrors = emptyMap(),
                            compatiblePayloads = emptyList(),
                        )
                    }
                    if (info.type == MetasploitModuleType.EXPLOIT || info.type == MetasploitModuleType.EVASION) {
                        when (val payloads = repository.compatiblePayloads(info.type, info.name)) {
                            is AppResult.Failure -> Unit
                            is AppResult.Success -> _uiState.update { current ->
                                if (current.selected?.type == info.type && current.selected.name == info.name) {
                                    current.copy(compatiblePayloads = payloads.value)
                                } else {
                                    current
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setOption(name: String, value: String) {
        _uiState.update { current ->
            current.copy(
                optionValues = current.optionValues + (name to value),
                validationErrors = current.validationErrors - name,
                runErrorMessage = null,
            )
        }
    }

    fun requestCheck() {
        requestRun(MetasploitModuleRunAction.CHECK)
    }

    fun requestExecute() {
        requestRun(MetasploitModuleRunAction.EXECUTE)
    }

    fun cancelRun() {
        _uiState.update { it.copy(confirmation = null) }
    }

    fun confirmRun() {
        val confirmation = _uiState.value.confirmation ?: return
        _uiState.update {
            it.copy(
                confirmation = null,
                runLoading = true,
                runErrorMessage = null,
                launch = null,
                runResult = null,
            )
        }
        viewModelScope.launch {
            val confirmedRequest = confirmation.request.copy(userConfirmed = true)
            val result = when (confirmation.action) {
                MetasploitModuleRunAction.CHECK -> repository.check(confirmedRequest)
                MetasploitModuleRunAction.EXECUTE -> repository.execute(confirmedRequest)
            }
            when (result) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(runLoading = false, runErrorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(runLoading = false, launch = result.value)
                }
            }
        }
    }

    fun refreshResult() {
        val uuid = _uiState.value.launch?.uuid ?: return
        _uiState.update { it.copy(runLoading = true, runErrorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.result(uuid)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(runLoading = false, runErrorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(runLoading = false, runResult = result.value)
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selected = null,
                optionValues = emptyMap(),
                validationErrors = emptyMap(),
                compatiblePayloads = emptyList(),
                confirmation = null,
                launch = null,
                runResult = null,
                runErrorMessage = null,
            )
        }
    }

    fun retry() {
        loadType(_uiState.value.type)
    }

    private fun requestRun(action: MetasploitModuleRunAction) {
        val current = _uiState.value
        val selected = current.selected ?: return
        if (action == MetasploitModuleRunAction.CHECK && !current.canCheck) {
            _uiState.update { it.copy(runErrorMessage = "此模組不支援 Check") }
            return
        }
        if (action == MetasploitModuleRunAction.EXECUTE && !current.canExecute) {
            _uiState.update { it.copy(runErrorMessage = "此模組類型不能透過 RPC 執行") }
            return
        }

        val validation = validator.validate(selected.options, current.optionValues)
        if (!validation.valid) {
            _uiState.update {
                it.copy(validationErrors = validation.errors, runErrorMessage = "請先修正參數")
            }
            return
        }
        val request = MetasploitModuleRequest(
            type = selected.type,
            name = selected.name,
            options = validation.normalized,
        )
        _uiState.update {
            it.copy(
                validationErrors = emptyMap(),
                runErrorMessage = null,
                confirmation = ModuleRunConfirmation(
                    action = action,
                    request = request,
                    redactedOptions = validator.redactedSummary(validation.normalized),
                ),
            )
        }
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
