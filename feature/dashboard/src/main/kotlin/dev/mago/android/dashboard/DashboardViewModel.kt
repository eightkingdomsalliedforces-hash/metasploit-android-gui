package dev.mago.android.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.InstallationFailureKind
import dev.mago.android.installation.InstallationStage
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class OperationsSnapshot(
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val sessions: List<MetasploitSessionSummary> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class DashboardViewModel(
    coordinator: BootstrapCoordinator,
    private val operationsRepository: MetasploitOperationsRepository,
) : ViewModel() {
    private val operations = MutableStateFlow(OperationsSnapshot())

    private val serviceState = combine(
        coordinator.state,
        coordinator.environment,
        coordinator.metasploitVersion,
    ) { installation, environment, version ->
        val termuxStatus = when (environment?.installed) {
            true -> ServiceStatus.RUNNING
            false -> ServiceStatus.STOPPED
            null -> ServiceStatus.UNKNOWN
        }
        val permissionStatus = when {
            environment == null -> ServiceStatus.UNKNOWN
            environment.runCommandPermissionGranted -> ServiceStatus.RUNNING
            else -> ServiceStatus.PERMISSION_REQUIRED
        }
        val bridgeReady = installation.lastSuccessfulStage == InstallationStage.DEPLOYING_BRIDGE ||
            installation.lastSuccessfulStage == InstallationStage.VERIFYING ||
            installation.stage == InstallationStage.READY ||
            installation.stage == InstallationStage.CONFIGURING_RPC
        val rpcError = installation.failureKind == InstallationFailureKind.RPC_ERROR
        DashboardUiState(
            termuxStatus = termuxStatus,
            termuxDetail = when (termuxStatus) {
                ServiceStatus.RUNNING -> "已安裝"
                ServiceStatus.STOPPED -> "尚未安裝"
                else -> "尚未檢查"
            },
            permissionStatus = permissionStatus,
            permissionDetail = when (permissionStatus) {
                ServiceStatus.RUNNING -> "已允許外部命令"
                ServiceStatus.PERMISSION_REQUIRED -> "需要 RUN_COMMAND 權限"
                else -> "尚未檢查"
            },
            bridgeStatus = when {
                bridgeReady -> ServiceStatus.RUNNING
                installation.stage == InstallationStage.DEPLOYING_BRIDGE && installation.lastError != null ->
                    ServiceStatus.ERROR
                else -> ServiceStatus.UNKNOWN
            },
            bridgeDetail = if (bridgeReady) "Bridge v1 可用" else "尚未驗證",
            rpcStatus = when {
                version != null -> ServiceStatus.RUNNING
                rpcError -> ServiceStatus.ERROR
                else -> ServiceStatus.STOPPED
            },
            rpcDetail = version?.let { "已連線 · ${it.frameworkVersion}" }
                ?: installation.lastError?.takeIf { rpcError }?.userMessage
                ?: "RPC 尚未連線",
            metasploitVersion = version?.frameworkVersion,
        )
    }

    val uiState = combine(serviceState, operations) { service, snapshot ->
        service.copy(
            jobs = snapshot.jobs,
            sessions = snapshot.sessions,
            selectedJob = snapshot.selectedJob,
            operationsLoading = snapshot.loading,
            operationsError = snapshot.error,
            onRefreshOperations = ::refreshOperations,
            onSelectJob = ::selectJob,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            onRefreshOperations = ::refreshOperations,
            onSelectJob = ::selectJob,
        ),
    )

    init {
        refreshOperations()
    }

    fun refreshOperations() {
        if (operations.value.loading) return
        operations.value = operations.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val jobsResult = operationsRepository.jobs()
            val sessionsResult = operationsRepository.sessions()
            val errors = buildList {
                if (jobsResult is AppResult.Failure) add(jobsResult.error.userMessage)
                if (sessionsResult is AppResult.Failure) add(sessionsResult.error.userMessage)
            }
            val jobs = when (jobsResult) {
                is AppResult.Success -> jobsResult.value
                is AppResult.Failure -> emptyList()
            }
            val sessions = when (sessionsResult) {
                is AppResult.Success -> sessionsResult.value
                is AppResult.Failure -> emptyList()
            }
            operations.value = operations.value.copy(
                jobs = jobs,
                sessions = sessions,
                selectedJob = null,
                loading = false,
                error = errors.distinct().joinToString("\n").takeIf { it.isNotBlank() },
            )
        }
    }

    fun selectJob(jobId: String) {
        if (operations.value.loading) return
        operations.value = operations.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = operationsRepository.jobInfo(jobId)) {
                is AppResult.Failure -> operations.value = operations.value.copy(
                    loading = false,
                    error = result.error.userMessage,
                )
                is AppResult.Success -> operations.value = operations.value.copy(
                    loading = false,
                    selectedJob = result.value,
                )
            }
        }
    }

    companion object {
        fun factory(
            coordinator: BootstrapCoordinator,
            operationsRepository: MetasploitOperationsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(coordinator, operationsRepository) as T
        }
    }
}
