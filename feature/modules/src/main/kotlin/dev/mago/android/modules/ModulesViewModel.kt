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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

enum class ModuleListMode { ALL, FAVORITES, RECENT }

data class ModulesUiState(
    val type: MetasploitModuleType = MetasploitModuleType.EXPLOIT,
    val query: String = "",
    val modules: List<MetasploitModuleSummary> = emptyList(),
    val searchResults: List<MetasploitModuleSummary> = emptyList(),
    val searching: Boolean = false,
    val searchErrorMessage: String? = null,
    val recentModules: List<MetasploitModuleSummary> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val listMode: ModuleListMode = ModuleListMode.ALL,
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
        get() {
            val source = when (listMode) {
                ModuleListMode.ALL -> if (query.isBlank()) modules else searchResults
                ModuleListMode.FAVORITES -> modules.filter { it.fullName in favorites }
                ModuleListMode.RECENT -> recentModules.filter { it.type == type }
            }
            if (query.isBlank() || listMode == ModuleListMode.ALL) return source
            val normalized = query.trim()
            return source.filter {
                it.name.contains(normalized, ignoreCase = true) ||
                    it.displayName?.contains(normalized, ignoreCase = true) == true
            }
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
    private var searchJob: Job? = null
    private var payloadJob: Job? = null

    init {
        refreshLocalState()
    }

    fun selectType(type: MetasploitModuleType) {
        val current = _uiState.value
        if (type == current.type && current.modules.isNotEmpty()) return
        searchJob?.cancel()
        payloadJob?.cancel()
        loadType(type, current.query)
        if (current.listMode == ModuleListMode.ALL) scheduleSearch(current.query, type)
    }

    fun setQuery(query: String) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                query = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
                searching = false,
                searchErrorMessage = null,
            )
        }
        if (state.listMode == ModuleListMode.ALL) scheduleSearch(query, state.type)
    }

    fun setListMode(mode: ModuleListMode) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                listMode = mode,
                query = "",
                searchResults = emptyList(),
                searching = false,
                searchErrorMessage = null,
            )
        }
    }

    fun toggleFavorite(module: MetasploitModuleSummary) {
        viewModelScope.launch {
            val favorite = module.fullName !in _uiState.value.favorites
            runCatching { localStore.setFavorite(module, favorite) }
            refreshLibraryNow()
        }
    }

    fun selectModule(module: MetasploitModuleSummary) {
        payloadJob?.cancel()
        viewModelScope.launch {
            runCatching { localStore.recordOpened(module) }
            refreshLibraryNow()
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
                    if (info.type in PAYLOAD_CAPABLE_TYPES) {
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
        if (name.equals("TARGET", ignoreCase = true)) reloadPayloadsForTarget(value)
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
                    _uiState.update { it.copy(runLoading = false, launch = result.value) }
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
                    _uiState.update { it.copy(runLoading = false, runResult = result.value) }
                }
            }
        }
    }

    fun clearSelection() {
        payloadJob?.cancel()
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
        if (current.listMode == ModuleListMode.ALL) scheduleSearch(current.query, current.type)
    }

    private fun reloadPayloadsForTarget(rawTarget: String) {
        val target = rawTarget.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return
        val selected = _uiState.value.selected ?: return
        if (selected.type !in PAYLOAD_CAPABLE_TYPES) return
        payloadJob?.cancel()
        payloadJob = viewModelScope.launch {
            when (val result = repository.compatiblePayloads(selected.type, selected.name, target)) {
                is AppResult.Failure -> Unit
                is AppResult.Success -> _uiState.update { current ->
                    val currentTarget = current.optionValues.entries
                        .firstOrNull { it.key.equals("TARGET", ignoreCase = true) }
                        ?.value
                        ?.trim()
                        ?.toIntOrNull()
                    if (
                        current.selected?.type == selected.type &&
                        current.selected.name == selected.name &&
                        currentTarget == target
                    ) {
                        current.copy(compatiblePayloads = result.value)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun scheduleSearch(query: String, type: MetasploitModuleType) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            _uiState.update {
                it.copy(searchResults = emptyList(), searching = false, searchErrorMessage = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (
                _uiState.value.query.trim() != normalized ||
                _uiState.value.type != type ||
                _uiState.value.listMode != ModuleListMode.ALL
            ) {
                return@launch
            }
            _uiState.update { it.copy(searching = true, searchErrorMessage = null) }
            val qualifiedQuery = "$normalized type:${type.rpcName}"
            when (val result = repository.search(qualifiedQuery)) {
                is AppResult.Failure -> _uiState.update { current ->
                    if (current.query.trim() != normalized || current.type != type) {
                        current
                    } else {
                        val fallback = current.modules.filter { summary ->
                            summary.name.contains(normalized, ignoreCase = true) ||
                                summary.displayName?.contains(normalized, ignoreCase = true) == true
                        }
                        if (fallback.isNotEmpty() || current.modules.isNotEmpty()) {
                            current.copy(
                                searching = false,
                                searchResults = fallback,
                                searchErrorMessage = null,
                                offlineCatalog = true,
                            )
                        } else {
                            current.copy(
                                searching = false,
                                searchErrorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
                is AppResult.Success -> {
                    val filtered = result.value.filter { it.type == type }
                    runCatching { localStore.cacheModules(type, filtered) }
                    _uiState.update { current ->
                        if (
                            current.query.trim() == normalized &&
                            current.type == type &&
                            current.listMode == ModuleListMode.ALL
                        ) {
                            current.copy(
                                searching = false,
                                searchResults = filtered,
                                searchErrorMessage = null,
                                offlineCatalog = false,
                            )
                        } else {
                            current
                        }
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
                    correlationId = UUID.randomUUID().toString(),
                    action = action,
                    request = request,
                    redactedOptions = validator.redactedSummary(validation.normalized),
                ),
            )
        }
    }

    private fun loadType(type: MetasploitModuleType, query: String = _uiState.value.query) {
        payloadJob?.cancel()
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
                offlineCatalog = false,
                runErrorMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.list(type)) {
                is AppResult.Failure -> {
                    val cached = runCatching { localStore.cachedModules(type) }.getOrDefault(emptyList())
                    if (cached.isNotEmpty()) {
                        _uiState.update { current ->
                            if (current.type == type) {
                                current.copy(
                                    loading = false,
                                    modules = cached,
                                    offlineCatalog = true,
                                    errorMessage = null,
                                )
                            } else {
                                current
                            }
                        }
                    } else {
                        _uiState.update { current ->
                            if (current.type == type) {
                                current.copy(loading = false, errorMessage = result.error.userMessage)
                            } else {
                                current
                            }
                        }
                    }
                }
                is AppResult.Success -> {
                    runCatching { localStore.cacheModules(type, result.value) }
                    _uiState.update { current ->
                        if (current.type == type) {
                            current.copy(
                                loading = false,
                                modules = result.value,
                                offlineCatalog = false,
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private suspend fun persistExecution(record: ModuleExecutionRecord) {
        runCatching { localStore.recordExecution(record) }
        refreshHistoryNow()
    }

    private fun refreshLocalState() {
        viewModelScope.launch {
            refreshLibraryNow()
            refreshHistoryNow()
        }
    }

    private suspend fun refreshLibraryNow() {
        val favorites = runCatching { localStore.favorites() }.getOrDefault(emptySet())
        val recent = runCatching { localStore.recent(RECENT_LIMIT) }.getOrDefault(emptyList())
        _uiState.update { it.copy(favorites = favorites, recentModules = recent) }
    }

    private suspend fun refreshHistoryNow() {
        val history = runCatching { localStore.executionHistory(HISTORY_LIMIT) }.getOrDefault(emptyList())
        _uiState.update { it.copy(executionHistory = history) }
    }

    companion object {
        private const val HISTORY_LIMIT = 50
        private const val RECENT_LIMIT = 50
        private const val SEARCH_DEBOUNCE_MILLIS = 250L
        private val PAYLOAD_CAPABLE_TYPES = setOf(
            MetasploitModuleType.EXPLOIT,
            MetasploitModuleType.EVASION,
        )

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
