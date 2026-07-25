package dev.mago.android.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.InstallationFailureKind
import dev.mago.android.installation.InstallationStage
import dev.mago.android.model.ServiceStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    coordinator: BootstrapCoordinator,
) : ViewModel() {
    val uiState = combine(
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    companion object {
        fun factory(coordinator: BootstrapCoordinator): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(coordinator) as T
            }
    }
}
