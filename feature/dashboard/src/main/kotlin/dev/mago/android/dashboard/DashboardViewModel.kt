package dev.mago.android.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.InstallationFailureKind
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.TermuxGateway
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.ServiceStatus
import dev.mago.android.model.bridge.BridgeAction
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MaintenanceAction(
    val bridgeAction: BridgeAction,
    val label: String,
    val confirmationTitle: String,
    val confirmationMessage: String,
) {
    UPDATE_METASPLOIT(
        bridgeAction = BridgeAction.UPDATE_METASPLOIT,
        label = "更新 Metasploit",
        confirmationTitle = "確認更新 Metasploit",
        confirmationMessage = "更新可能需要較長時間。請保持 Termux 可用並避免關閉 App；此操作不會在背景自動重試。",
    ),
    CLEAN_CACHE(
        bridgeAction = BridgeAction.CLEAN_CACHE,
        label = "清理快取",
        confirmationTitle = "確認清理快取",
        confirmationMessage = "這會清除可重新建立的 Termux／Metasploit 快取，不會刪除 Workspace、資產或執行歷史。",
    ),
}

private data class OperationsSnapshot(
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val sessions: List<MetasploitSessionSummary> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stopConfirmation: OperationStopTarget? = null,
    val stoppingTarget: OperationStopTarget? = null,
    val stopMessage: String? = null,
    val stopError: OperationStopError? = null,
)

private sealed interface OperationsLoadResult {
    data class Success(
        val jobs: List<MetasploitJobSummary>,
        val sessions: List<MetasploitSessionSummary>,
    ) : OperationsLoadResult

    data class Failure(val userMessage: String) : OperationsLoadResult
}

private data class MaintenanceSnapshot(
    val confirmation: MaintenanceAction? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val healthSummary: String? = null,
)

class DashboardViewModel(
    private val coordinator: BootstrapCoordinator,
    private val operationsRepository: MetasploitOperationsRepository,
    private val termuxGateway: TermuxGateway,
) : ViewModel() {
    private val operations = MutableStateFlow(OperationsSnapshot())
    private val maintenance = MutableStateFlow(MaintenanceSnapshot())

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

    val uiState = combine(serviceState, operations, maintenance) { service, operationsSnapshot, maintenanceSnapshot ->
        service.copy(
            jobs = operationsSnapshot.jobs,
            sessions = operationsSnapshot.sessions,
            selectedJob = operationsSnapshot.selectedJob,
            operationsLoading = operationsSnapshot.loading,
            operationsError = operationsSnapshot.error,
            stopConfirmation = operationsSnapshot.stopConfirmation,
            stopMessage = operationsSnapshot.stopMessage,
            stopError = operationsSnapshot.stopError,
            maintenanceConfirmation = maintenanceSnapshot.confirmation,
            maintenanceLoading = maintenanceSnapshot.loading,
            maintenanceMessage = maintenanceSnapshot.message,
            maintenanceError = maintenanceSnapshot.error,
            maintenanceHealthSummary = maintenanceSnapshot.healthSummary,
            onRefreshOperations = ::refreshOperations,
            onSelectJob = ::selectJob,
            onRequestStopJob = ::requestStopJob,
            onRequestStopSession = ::requestStopSession,
            onCancelStop = ::cancelStop,
            onRequestMaintenance = ::requestMaintenance,
            onConfirmMaintenance = ::confirmMaintenance,
            onCancelMaintenance = ::cancelMaintenance,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            onRefreshOperations = ::refreshOperations,
            onSelectJob = ::selectJob,
            onRequestStopJob = ::requestStopJob,
            onRequestStopSession = ::requestStopSession,
            onCancelStop = ::cancelStop,
            onRequestMaintenance = ::requestMaintenance,
            onConfirmMaintenance = ::confirmMaintenance,
            onCancelMaintenance = ::cancelMaintenance,
        ),
    )

    init {
        refreshOperations()
    }

    fun refreshOperations() {
        val snapshot = operations.value
        if (
            snapshot.loading ||
            snapshot.stopConfirmation != null ||
            snapshot.stoppingTarget != null ||
            maintenance.value.loading ||
            maintenance.value.confirmation != null
        ) return

        operations.value = snapshot.copy(
            loading = true,
            error = null,
            stopMessage = null,
            stopError = null,
        )
        viewModelScope.launch {
            when (val result = loadOperationsSnapshot()) {
                is OperationsLoadResult.Failure -> operations.value = operations.value.copy(
                    loading = false,
                    error = result.userMessage,
                )
                is OperationsLoadResult.Success -> operations.value = operations.value.copy(
                    jobs = result.jobs,
                    sessions = result.sessions,
                    selectedJob = null,
                    loading = false,
                    error = null,
                )
            }
        }
    }

    fun selectJob(jobId: String) {
        if (
            operations.value.loading ||
            stopStateActive() ||
            maintenance.value.loading ||
            maintenance.value.confirmation != null
        ) return

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

    fun requestStopJob(jobId: String) {
        val snapshot = operations.value
        if (stopRequestBlocked(snapshot)) return
        val job = snapshot.jobs.firstOrNull { it.id == jobId }
        operations.value = if (job == null) {
            snapshot.copy(
                stopMessage = null,
                stopError = OperationStopError(
                    title = "此 Job 已不在目前列表中，請重新整理。",
                    userMessage = null,
                ),
            )
        } else {
            snapshot.copy(
                stopConfirmation = OperationStopTarget.Job(job.id, job.name),
                stopMessage = null,
                stopError = null,
            )
        }
    }

    fun requestStopSession(sessionId: Int) {
        val snapshot = operations.value
        if (stopRequestBlocked(snapshot)) return
        val session = snapshot.sessions.firstOrNull { it.id == sessionId }
        operations.value = if (session == null) {
            snapshot.copy(
                stopMessage = null,
                stopError = OperationStopError(
                    title = "此 Session 已不在目前列表中，請重新整理。",
                    userMessage = null,
                ),
            )
        } else {
            snapshot.copy(
                stopConfirmation = OperationStopTarget.Session(
                    id = session.id,
                    description = session.description,
                    sourceModule = session.viaExploit,
                ),
                stopMessage = null,
                stopError = null,
            )
        }
    }

    fun cancelStop() {
        if (operations.value.stoppingTarget != null) return
        operations.value = operations.value.copy(stopConfirmation = null)
    }

    fun requestMaintenance(action: MaintenanceAction) {
        if (
            maintenance.value.loading ||
            maintenance.value.confirmation != null ||
            stopStateActive()
        ) return

        maintenance.value = maintenance.value.copy(
            confirmation = action,
            message = null,
            error = null,
            healthSummary = null,
        )
    }

    fun cancelMaintenance() {
        if (maintenance.value.loading) return
        maintenance.value = maintenance.value.copy(confirmation = null)
    }

    fun confirmMaintenance() {
        val action = maintenance.value.confirmation ?: return
        if (maintenance.value.loading || stopStateActive()) return
        if (coordinator.state.value.stage != InstallationStage.READY) {
            maintenance.value = maintenance.value.copy(
                confirmation = null,
                error = "環境尚未就緒，請先完成安裝或執行診斷。",
            )
            return
        }

        maintenance.value = MaintenanceSnapshot(loading = true)
        viewModelScope.launch {
            val actionResult = termuxGateway.execute(
                action = action.bridgeAction,
                operationId = UUID.randomUUID().toString(),
            )
            when (actionResult) {
                is AppResult.Failure -> maintenance.value = MaintenanceSnapshot(
                    error = actionResult.error.userMessage,
                )
                is AppResult.Success -> {
                    if (!actionResult.value.success) {
                        maintenance.value = MaintenanceSnapshot(error = actionResult.value.message)
                        return@launch
                    }
                    runPostMaintenanceHealthCheck(action)
                }
            }
        }
    }

    private suspend fun loadOperationsSnapshot(): OperationsLoadResult {
        val jobsResult = operationsRepository.jobs()
        val sessionsResult = operationsRepository.sessions()
        if (jobsResult is AppResult.Success && sessionsResult is AppResult.Success) {
            return OperationsLoadResult.Success(
                jobs = jobsResult.value,
                sessions = sessionsResult.value,
            )
        }
        val messages = buildList {
            if (jobsResult is AppResult.Failure) add(jobsResult.error.userMessage)
            if (sessionsResult is AppResult.Failure) add(sessionsResult.error.userMessage)
        }
        return OperationsLoadResult.Failure(
            userMessage = messages.distinct()
                .joinToString("\n")
                .ifBlank { "無法取得 Jobs 與 Sessions" },
        )
    }

    private fun stopRequestBlocked(snapshot: OperationsSnapshot): Boolean =
        snapshot.loading ||
            snapshot.stopConfirmation != null ||
            snapshot.stoppingTarget != null ||
            maintenance.value.loading ||
            maintenance.value.confirmation != null

    private fun stopStateActive(): Boolean =
        operations.value.stopConfirmation != null || operations.value.stoppingTarget != null

    private suspend fun runPostMaintenanceHealthCheck(action: MaintenanceAction) {
        when (
            val healthResult = termuxGateway.execute(
                action = BridgeAction.HEALTH_CHECK,
                operationId = UUID.randomUUID().toString(),
            )
        ) {
            is AppResult.Failure -> maintenance.value = MaintenanceSnapshot(
                error = "${action.label}已完成，但健康檢查失敗：${healthResult.error.userMessage}",
            )
            is AppResult.Success -> {
                if (!healthResult.value.success) {
                    maintenance.value = MaintenanceSnapshot(
                        error = "${action.label}已完成，但健康檢查失敗：${healthResult.value.message}",
                    )
                    return
                }
                val summary = healthResult.value.data
                    .toSortedMap()
                    .entries
                    .joinToString(" · ") { (name, value) -> "$name=$value" }
                    .takeIf { it.isNotBlank() }
                maintenance.value = MaintenanceSnapshot(
                    message = "${action.label}完成，健康檢查通過。",
                    healthSummary = summary,
                )
            }
        }
    }

    companion object {
        fun factory(
            coordinator: BootstrapCoordinator,
            operationsRepository: MetasploitOperationsRepository,
            termuxGateway: TermuxGateway,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(coordinator, operationsRepository, termuxGateway) as T
        }
    }
}
