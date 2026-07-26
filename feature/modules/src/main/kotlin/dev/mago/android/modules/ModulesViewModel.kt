package dev.mago.android.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.metasploit.NoOpModuleLocalStore
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleRunConfirmation(
    val correlationId: String,
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
    val authorizationConfirmed: Boolean = false,
    val launch: MetasploitModuleLaunch? = null,
    val runResult: MetasploitModuleRunResult? = null,
    val executionHistory: List<ModuleExecutionRecord> = emptyList(),
    val offlineCatalog: Boolean = false,
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
            MetasploitModuleType.EVASION,
        )
}

class ModulesViewModel(
    private val repository: MetasploitModuleRepository,
    private val localStore: ModuleLocalStore = NoOpModuleLocalStore,
    private val validator: ModuleRunValidator = ModuleRunValidator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshHistory()
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
            runCatching { localStore.recordOpened(module) }
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
                    runCatching { localStore.cacheInfo(info) }
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
            val now = System.currentTimeMillis()
            when (result) {
                is AppResult.Failure -> {
                    persistExecution(
                        ModuleExecutionRecord(
                            correlationId = confirmation.correlationId,
                            action = confirmation.action,
                            type = confirmedRequest.type,
                            name = confirmedRequest.name,
                            status = MetasploitModuleRunStatus.ERRORED,
                            jobId = null,
                            uuid = null,
                            redactedOptions = confirmation.redactedOptions,
                            resultSummary = null,
                            error = result.error.userMessage,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                    _uiState.update {
                        it.copy(runLoading = false, runErrorMessage = result.error.userMessage)
                    }
                }
                is AppResult.Success -> {
                    persistExecution(
                        ModuleExecutionRecord(
                            correlationId = confirmation.correlationId,
                            action = confirmation.action,
                            type = confirmedRequest.type,
                            name = confirmedRequest.name,
                            status = MetasploitModuleRunStatus.RUNNING,
                            jobId = result.value.jobId,
                            uuid = result.value.uuid,
                            redactedOptions = confirmation.redactedOptions,
                            resultSummary = null,
                            error = null,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                    _uiState.update {
                        it.copy(runLoading = false, launch = result.value)
                    }
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
                is AppResult.Success -> {
                    runCatching {
                        localStore.updateExecution(
                            uuid = uuid,
                            status = result.value.status,
                            resultSummary = null,
                            error = result.value.error,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                    }
                    refreshHistoryNow()
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
                    correlationId = UUID.randomUUID().toString(),
                    action = action,
                    request = request,
                    redactedOptions = validator.redactedSummary(validation.normalized),
                ),
            )
        }
    }

    private fun loadType(type: MetasploitModuleType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                type = type,
                query = "",
                modules = emptyList(),
                selected = null,
                loading = true,
                errorMessage = null,
                offlineCatalog = false,
            )
            when (val result = repository.list(type)) {
                is AppResult.Failure -> {
                    val cached = runCatching { localStore.cachedModules(type) }.getOrDefault(emptyList())
                    if (cached.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                modules = cached,
                                offlineCatalog = true,
                                errorMessage = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(loading = false, errorMessage = result.error.userMessage)
                        }
                    }
                }
                is AppResult.Success -> {
                    runCatching { localStore.cacheModules(type, result.value) }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            modules = result.value,
                            offlineCatalog = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistExecution(record: ModuleExecutionRecord) {
        runCatching { localStore.recordExecution(record) }
        refreshHistoryNow()
    }

    private fun refreshHistory() {
        viewModelScope.launch { refreshHistoryNow() }
    }

    private suspend fun refreshHistoryNow() {
        val history = runCatching { localStore.executionHistory(HISTORY_LIMIT) }.getOrDefault(emptyList())
        _uiState.update { it.copy(executionHistory = history) }
    }

    companion object {
        private const val HISTORY_LIMIT = 50

        fun factory(
            repository: MetasploitModuleRepository,
            localStore: ModuleLocalStore = NoOpModuleLocalStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ModulesViewModel(repository, localStore) as T
        }
    }
}
