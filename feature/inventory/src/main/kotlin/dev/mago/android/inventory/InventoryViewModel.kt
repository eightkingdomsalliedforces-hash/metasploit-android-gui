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
    val selectedTab: InventoryTab = InventoryTab.HOSTS,
    val hosts: List<MetasploitHostRecord> = emptyList(),
    val services: List<MetasploitServiceRecord> = emptyList(),
    val vulnerabilities: List<MetasploitVulnerabilityRecord> = emptyList(),
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
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            when (val workspaces = repository.workspaces()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = workspaces.error.userMessage)
                }
                is AppResult.Success -> {
                    val selected = _uiState.value.selectedWorkspace
                        ?.takeIf { current -> workspaces.value.any { it.name == current } }
                        ?: workspaces.value.firstOrNull { it.name == DEFAULT_WORKSPACE }?.name
                        ?: workspaces.value.firstOrNull()?.name
                    _uiState.update {
                        it.copy(
                            workspaces = workspaces.value,
                            selectedWorkspace = selected,
                            loading = false,
                            errorMessage = null,
                        )
                    }
                    if (selected != null) loadSelectedTab(selected)
                }
            }
        }
    }

    fun selectWorkspace(name: String) {
        if (name == _uiState.value.selectedWorkspace || _uiState.value.loading) return
        if (_uiState.value.workspaces.none { it.name == name }) return
        _uiState.update {
            it.copy(
                selectedWorkspace = name,
                hosts = emptyList(),
                services = emptyList(),
                vulnerabilities = emptyList(),
                errorMessage = null,
            )
        }
        viewModelScope.launch { loadSelectedTab(name) }
    }

    fun selectTab(tab: InventoryTab) {
        if (tab == _uiState.value.selectedTab || _uiState.value.loading) return
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
        val workspace = _uiState.value.selectedWorkspace ?: return
        viewModelScope.launch { loadSelectedTab(workspace) }
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

    private fun fail(message: String) {
        _uiState.update { it.copy(loading = false, errorMessage = message) }
    }

    companion object {
        const val PAGE_LIMIT = 100
        private const val DEFAULT_WORKSPACE = "default"

        fun factory(repository: MetasploitInventoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    InventoryViewModel(repository) as T
            }
    }
}
