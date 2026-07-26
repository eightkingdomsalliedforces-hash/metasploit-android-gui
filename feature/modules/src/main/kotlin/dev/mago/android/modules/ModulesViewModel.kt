package dev.mago.android.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.ModuleCatalogRepository
import dev.mago.android.metasploit.ModuleExecutionHistoryRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
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
    val favorites: Set<MetasploitModuleSummary> = emptySet(),
    val recent: List<MetasploitModuleSummary> = emptyList(),
    val executionHistory: List<ModuleExecutionRecord> = emptyList(),
    val activeCorrelationId: String? = null,
    val offline: Boolean = false,
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
        get() = if (query.isBlank()) modules else modules.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }

    val selectedSummary: MetasploitModuleSummary?
        get() = selected?.let { MetasploitModuleSummary(it.type, it.name) }

    val selectedIsFavorite: Boolean
        get() = selectedSummary?.let(favorites::contains) == true

    val selectedHistory: List<ModuleExecutionRecord>
        get() = selected?.let { selectedInfo ->
            executionHistory.filter {
                it.request.type == selectedInfo.type && it.request.name == selectedInfo.name
            }
        }.orEmpty()

    val canCheck: Boolean
        get() = selected?.let {
            !offline && it.hasCheck &&
                it.type in setOf(MetasploitModuleType.EXPLOIT, MetasploitModuleType.AUXILIARY)
        } == true

    val canExecute: Boolean
        get() = !offline && selected?.type in setOf(
            MetasploitModuleType.EXPLOIT,
            MetasploitModuleType.AUXILIARY,
            MetasploitModuleType.POST,
            MetasploitModuleType.EVASION,
        )
}

class ModulesViewModel(
    private val repository: MetasploitModuleRepository,
    private val historyRepository: ModuleExecutionHistoryRepository? = null,
    private val validator: ModuleRunValidator = ModuleRunValidator(),
) : ViewModel() {
    private val catalogRepository = repository as? ModuleCatalogRepository
    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        catalogRepository?.let { catalog ->
            viewModelScope.launch {
                catalog.catalogStatus.collect { status ->
                    _uiState.update { it.copy(offline = status.offline) }
                }
            }
            viewModelScope.launch {
                catalog.observeFavorites().collect { favorites ->
                    _uiState.update { it.copy(favorites = favorites) }
                }
            }
            viewModelScope.launch {
                catalog.observeRecent().collect { recent ->
                    _uiState.update { it.copy(recent = recent) }
                }
            }
        }
        historyRepository?.let { history ->
            viewModelScope.launch {
                history.observe().collect { records ->
                    _uiState.update { it.copy(executionHistory = records) }
                }
            }
        }
    }

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
                    authorizationConfirmed = false,
                    launch = null,
                    runResult = null,
                    activeCorrelationId = null,
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
                    catalogRepository?.recordRecent(module)
                    if (!_uiState.value.offline &&
                        (info.type == MetasploitModuleType.EXPLOIT || info.type == MetasploitModuleType.EVASION)
                    ) {
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

    fun toggleFavorite() {
        val catalog = catalogRepository ?: return
        val module = _uiState.value.selectedSummary ?: return
        val favorite = !_uiState.value.selectedIsFavorite
        viewModelScope.launch {
            when (val result = catalog.setFavorite(module, favorite)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(runErrorMessage = result.error.userMessage)
                }
                is AppResult.Success -> Unit
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
        if (current.offline) {
            _uiState.update { it.copy(runErrorMessage = "離線快取不可執行模組") }
            return
        }
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
                activeCorrelationId = null,
            )
        }
        viewModelScope.launch {
            val confirmedRequest = confirmation.request.copy(userConfirmed = true)
            val correlationId = when (val history = historyRepository) {
                null -> null
                else -> when (
                    val recorded = history.begin(
                        action = confirmation.action,
                        request = confirmedRequest,
                        workspace = null,
                        redactedParameters = confirmation.redactedOptions,
                    )
                ) {
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(runLoading = false, runErrorMessage = recorded.error.userMessage)
                        }
                        return@launch
                    }
                    is AppResult.Success -> recorded.value
                }
            }
            _uiState.update { it.copy(activeCorrelationId = correlationId) }
            val result = when (confirmation.action) {
                MetasploitModuleRunAction.CHECK -> repository.check(confirmedRequest)
                MetasploitModuleRunAction.EXECUTE -> repository.execute(confirmedRequest)
            }
            when (result) {
                is AppResult.Failure -> {
                    correlationId?.let { historyRepository?.markFailed(it) }
                    _uiState.update {
                        it.copy(runLoading = false, runErrorMessage = result.error.userMessage)
                    }
                }
                is AppResult.Success -> {
                    correlationId?.let { historyRepository?.markLaunched(it, result.value) }
                    _uiState.update {
                        it.copy(runLoading = false, launch = result.value)
                    }
                }
            }
        }
    }

    fun refreshResult() {
        val uuid = _uiState.value.launch?.uuid ?: return
        if (_uiState.value.runLoading || _uiState.value.offline) return
        _uiState.update { it.copy(runLoading = true, runErrorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.result(uuid)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(runLoading = false, runErrorMessage = result.error.userMessage)
                }
                is AppResult.Success -> {
                    _uiState.value.activeCorrelationId?.let {
                        historyRepository?.markResult(it, result.value)
                    }
                    _uiState.update {
                        it.copy(runLoading = false, runResult = result.value)
                    }
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
                activeCorrelationId = null,
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
        if (current.offline) {
            _uiState.update { it.copy(runErrorMessage = "離線快取不可執行模組") }
            return
        }
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

    private fun loadType(type: MetasploitModuleType) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = ModulesUiState(
                type = type,
                loading = true,
                favorites = current.favorites,
                recent = current.recent,
                executionHistory = current.executionHistory,
                offline = current.offline,
            )
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
        fun factory(
            repository: MetasploitModuleRepository,
            historyRepository: ModuleExecutionHistoryRepository? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ModulesViewModel(repository, historyRepository) as T
        }
    }
}
