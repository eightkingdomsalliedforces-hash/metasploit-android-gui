package dev.mago.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryTab(val label: String) {
    HOSTS("Hosts"),
    SERVICES("Services"),
    VULNERABILITIES("Vulnerabilities"),
}

data class InventoryUiState(
    val workspaces: List<MetasploitWorkspaceSummary> = emptyList(),
    val selectedWorkspace: String? = null,
    val activeWorkspace: MetasploitWorkspaceSummary? = null,
    val selectedTab: InventoryTab = InventoryTab.HOSTS,
    val hosts: List<MetasploitHostRecord> = emptyList(),
    val services: List<MetasploitServiceRecord> = emptyList(),
    val vulnerabilities: List<MetasploitVulnerabilityRecord> = emptyList(),
    val createWorkspaceDialogVisible: Boolean = false,
    val workspaceDraft: String = "",
    val workspaceValidationError: String? = null,
    val workspaceMutationLoading: Boolean = false,
    val workspaceMutationError: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleCount: Int
        get() = when (selectedTab) {
            InventoryTab.HOSTS -> hosts.size
            InventoryTab.SERVICES -> services.size
            InventoryTab.VULNERABILITIES -> vulnerabilities.size
        }
}

class InventoryViewModel(
    private val repository: MetasploitInventoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.loading || _uiState.value.workspaceMutationLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, workspaceMutationError = null) }
            when (val workspaces = repository.workspaces()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = workspaces.error.userMessage)
                }
                is AppResult.Success -> {
                    val activeResult = repository.currentWorkspace()
                    val active = (activeResult as? AppResult.Success)?.value
                    val selected = _uiState.value.selectedWorkspace
                        ?.takeIf { current -> workspaces.value.any { it.name == current } }
                        ?: active?.name?.takeIf { current -> workspaces.value.any { it.name == current } }
                        ?: workspaces.value.firstOrNull { it.name == DEFAULT_WORKSPACE }?.name
                        ?: workspaces.value.firstOrNull()?.name
                    _uiState.update {
                        it.copy(
                            workspaces = workspaces.value,
                            selectedWorkspace = selected,
                            activeWorkspace = active,
                            loading = false,
                            errorMessage = null,
                            workspaceMutationError = (activeResult as? AppResult.Failure)?.error?.userMessage,
                        )
                    }
                    if (selected != null) loadSelectedTab(selected)
                }
            }
        }
    }

    fun selectWorkspace(name: String) {
        if (name == _uiState.value.selectedWorkspace || busy()) return
        if (_uiState.value.workspaces.none { it.name == name }) return
        _uiState.update {
            it.copy(
                selectedWorkspace = name,
                hosts = emptyList(),
                services = emptyList(),
                vulnerabilities = emptyList(),
                errorMessage = null,
                workspaceMutationError = null,
            )
        }
        viewModelScope.launch { loadSelectedTab(name) }
    }

    fun selectTab(tab: InventoryTab) {
        if (tab == _uiState.value.selectedTab || busy()) return
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
        val workspace = _uiState.value.selectedWorkspace ?: return
        viewModelScope.launch { loadSelectedTab(workspace) }
    }

    fun showCreateWorkspace() {
        if (busy()) return
        _uiState.update {
            it.copy(
                createWorkspaceDialogVisible = true,
                workspaceDraft = "",
                workspaceValidationError = null,
                workspaceMutationError = null,
            )
        }
    }

    fun dismissCreateWorkspace() {
        if (_uiState.value.workspaceMutationLoading) return
        _uiState.update {
            it.copy(
                createWorkspaceDialogVisible = false,
                workspaceDraft = "",
                workspaceValidationError = null,
            )
        }
    }

    fun setWorkspaceDraft(value: String) {
        if (_uiState.value.workspaceMutationLoading) return
        _uiState.update { current ->
            current.copy(
                workspaceDraft = value,
                workspaceValidationError = validateWorkspaceName(value, current.workspaces),
                workspaceMutationError = null,
            )
        }
    }

    fun submitCreateWorkspace() {
        val current = _uiState.value
        if (!current.createWorkspaceDialogVisible || busy()) return
        val name = current.workspaceDraft.trim()
        val validationError = validateWorkspaceName(name, current.workspaces)
        if (validationError != null) {
            _uiState.update { it.copy(workspaceValidationError = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceMutationLoading = true, workspaceMutationError = null) }
            when (val result = repository.addWorkspace(name)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        workspaceMutationLoading = false,
                        workspaceMutationError = result.error.userMessage,
                    )
                }
                is AppResult.Success -> refreshWorkspaceListAfterCreate(name)
            }
        }
    }

    fun setSelectedWorkspaceActive() {
        val selected = _uiState.value.selectedWorkspace ?: return
        if (busy() || selected == _uiState.value.activeWorkspace?.name) return
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceMutationLoading = true, workspaceMutationError = null) }
            when (val result = repository.setWorkspace(selected)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        workspaceMutationLoading = false,
                        workspaceMutationError = result.error.userMessage,
                    )
                }
                is AppResult.Success -> {
                    val verified = repository.currentWorkspace()
                    val fallback = _uiState.value.workspaces.firstOrNull { it.name == selected }
                    _uiState.update {
                        it.copy(
                            activeWorkspace = (verified as? AppResult.Success)?.value ?: fallback,
                            workspaceMutationLoading = false,
                            workspaceMutationError = (verified as? AppResult.Failure)?.let {
                                "Workspace 已切換，但無法重新確認：${it.error.userMessage}"
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshWorkspaceListAfterCreate(name: String) {
        when (val refreshed = repository.workspaces()) {
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    createWorkspaceDialogVisible = false,
                    workspaceDraft = "",
                    workspaceValidationError = null,
                    workspaceMutationLoading = false,
                    workspaceMutationError = "Workspace 已建立，但清單重新整理失敗：${refreshed.error.userMessage}",
                )
            }
            is AppResult.Success -> {
                _uiState.update {
                    it.copy(
                        workspaces = refreshed.value,
                        selectedWorkspace = name,
                        createWorkspaceDialogVisible = false,
                        workspaceDraft = "",
                        workspaceValidationError = null,
                        workspaceMutationLoading = false,
                        workspaceMutationError = null,
                        hosts = emptyList(),
                        services = emptyList(),
                        vulnerabilities = emptyList(),
                    )
                }
                loadSelectedTab(name)
            }
        }
    }

    private suspend fun loadSelectedTab(workspace: String) {
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        when (_uiState.value.selectedTab) {
            InventoryTab.HOSTS -> when (val result = repository.hosts(workspace, PAGE_LIMIT, 0)) {
                is AppResult.Failure -> fail(result.error.userMessage)
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, hosts = result.value, errorMessage = null)
                }
            }
            InventoryTab.SERVICES -> when (val result = repository.services(workspace, PAGE_LIMIT, 0)) {
                is AppResult.Failure -> fail(result.error.userMessage)
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, services = result.value, errorMessage = null)
                }
            }
            InventoryTab.VULNERABILITIES -> when (
                val result = repository.vulnerabilities(workspace, PAGE_LIMIT, 0)
            ) {
                is AppResult.Failure -> fail(result.error.userMessage)
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, vulnerabilities = result.value, errorMessage = null)
                }
            }
        }
    }

    private fun busy(): Boolean = _uiState.value.loading || _uiState.value.workspaceMutationLoading

    private fun validateWorkspaceName(
        raw: String,
        workspaces: List<MetasploitWorkspaceSummary>,
    ): String? {
        val name = raw.trim()
        if (name.isEmpty()) return "請輸入 Workspace 名稱"
        if (!WORKSPACE_NAME_PATTERN.matches(name)) {
            return "限 1–64 個英數字、句點、底線或連字號，且必須以英數字開頭"
        }
        if (workspaces.any { it.name.equals(name, ignoreCase = true) }) return "Workspace 已存在"
        return null
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(loading = false, errorMessage = message) }
    }

    companion object {
        const val PAGE_LIMIT = 100
        private const val DEFAULT_WORKSPACE = "default"
        private val WORKSPACE_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}${'$'}")

        fun factory(repository: MetasploitInventoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    InventoryViewModel(repository) as T
            }
    }
}
