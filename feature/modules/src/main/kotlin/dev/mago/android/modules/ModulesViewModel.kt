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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val searchResults: List<MetasploitModuleSummary> = emptyList(),
    val searching: Boolean = false,
    val searchErrorMessage: String? = null,
    val selected: MetasploitModuleInfo? = null,
    val optionValues: Map<String, String> = emptyMap(),
    val validationErrors: Map<String, String> = emptyMap(),
    val compatiblePayloads: List<String> = emptyList(),
    val confirmation: ModuleRunConfirmation? = null,
    val authorizationConfirmed: Boolean = false,
    val launch: MetasploitModuleLaunch? = null,
    val runResult: MetasploitModuleRunResult? = null,
    val runLoading: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val runErrorMessage: String? = null,
) {
    val visibleModules: List<MetasploitModuleSummary>
        get() = if (query.isBlank()) modules else searchResults

    val canCheck: Boolean
        get() = selected?.let {
            it.hasCheck && it.type in setOf(MetasploitModuleType.EXPLOIT, MetasploitModuleType.AUXILIARY)
        } == true

    val canExecute: Boolean
        get() = selected?.type in setOf(
            MetasploitModuleType.EXPLOIT,
            MetasploitModuleType.AUXILIARY,
            MetasploitModuleType.POST,
            MetasploitModuleType.EVASION,
        )
}

class ModulesViewModel(
    private val repository: MetasploitModuleRepository,
    private val validator: ModuleRunValidator = ModuleRunValidator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun selectType(type: MetasploitModuleType) {
        val current = _uiState.value
        if (type == current.type && current.modules.isNotEmpty()) return
        searchJob?.cancel()
        loadType(type, current.query)
        scheduleSearch(current.query, type)
    }

    fun setQuery(query: String) {
        val type = _uiState.value.type
        _uiState.update {
            it.copy(
                query = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
                searching = false,
                searchErrorMessage = null,
            )
        }
        scheduleSearch(query, type)
    }

    fun selectModule(module: MetasploitModuleSummary) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                    runErrorMessage = null,
                    confirmation = null,
                    authorizationConfirmed = false,
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
                authorizationConfirmed = false,
            )
        }
    }

    fun requestCheck() {
        requestRun(MetasploitModuleRunAction.CHECK)
    }

    fun requestExecute() {
        requestRun(MetasploitModuleRunAction.EXECUTE)
    }

    fun setAuthorizationConfirmed(confirmed: Boolean) {
        _uiState.update { current ->
            if (current.confirmation == null) current.copy(authorizationConfirmed = false)
            else current.copy(authorizationConfirmed = confirmed, runErrorMessage = null)
        }
    }

    fun cancelRun() {
        _uiState.update {
            it.copy(
                confirmation = null,
                authorizationConfirmed = false,
                runErrorMessage = null,
            )
        }
    }

    fun confirmRun() {
        val current = _uiState.value
        val confirmation = current.confirmation ?: return
        if (!current.authorizationConfirmed) {
            _uiState.update { it.copy(runErrorMessage = "請先確認僅在授權環境執行") }
            return
        }
        _uiState.update {
            it.copy(
                confirmation = null,
                authorizationConfirmed = false,
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
        if (_uiState.value.runLoading) return
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
                authorizationConfirmed = false,
                launch = null,
                runResult = null,
                runErrorMessage = null,
            )
        }
    }

    fun retry() {
        val current = _uiState.value
        loadType(current.type, current.query)
        scheduleSearch(current.query, current.type)
    }

    private fun scheduleSearch(query: String, type: MetasploitModuleType) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), searching = false, searchErrorMessage = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (_uiState.value.query.trim() != normalized || _uiState.value.type != type) return@launch
            _uiState.update { it.copy(searching = true, searchErrorMessage = null) }
            val qualifiedQuery = "$normalized type:${type.rpcName}"
            when (val result = repository.search(qualifiedQuery)) {
                is AppResult.Failure -> _uiState.update { current ->
                    if (current.query.trim() == normalized && current.type == type) {
                        current.copy(searching = false, searchErrorMessage = result.error.userMessage)
                    } else {
                        current
                    }
                }
                is AppResult.Success -> _uiState.update { current ->
                    if (current.query.trim() == normalized && current.type == type) {
                        current.copy(
                            searching = false,
                            searchResults = result.value.filter { it.type == type },
                            searchErrorMessage = null,
                        )
                    } else {
                        current
                    }
                }
            }
        }
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
                it.copy(
                    validationErrors = validation.errors,
                    runErrorMessage = "請先修正參數",
                    authorizationConfirmed = false,
                )
            }
            return
        }
        val request = MetasploitModuleRequest(
            type = selected.type,
            name = selected.name,
            options = validation.normalized,
            userConfirmed = false,
        )
        _uiState.update {
            it.copy(
                validationErrors = emptyMap(),
                runErrorMessage = null,
                authorizationConfirmed = false,
                confirmation = ModuleRunConfirmation(
                    action = action,
                    request = request,
                    redactedOptions = validator.redactedSummary(validation.normalized),
                ),
            )
        }
    }

    private fun loadType(type: MetasploitModuleType, query: String = _uiState.value.query) {
        _uiState.update {
            it.copy(
                type = type,
                query = query,
                modules = emptyList(),
                searchResults = emptyList(),
                searching = false,
                searchErrorMessage = null,
                selected = null,
                optionValues = emptyMap(),
                validationErrors = emptyMap(),
                compatiblePayloads = emptyList(),
                confirmation = null,
                authorizationConfirmed = false,
                launch = null,
                runResult = null,
                loading = true,
                errorMessage = null,
                runErrorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.list(type)) {
                is AppResult.Failure -> _uiState.update { current ->
                    if (current.type == type) current.copy(loading = false, errorMessage = result.error.userMessage)
                    else current
                }
                is AppResult.Success -> _uiState.update { current ->
                    if (current.type == type) current.copy(loading = false, modules = result.value)
                    else current
                }
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 250L

        fun factory(repository: MetasploitModuleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModulesViewModel(repository) as T
            }
    }
}
