package dev.mago.android.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.model.ServiceStatus
import dev.mago.android.ui.components.ServiceStatusCard

data class DashboardUiState(
    val termuxStatus: ServiceStatus = ServiceStatus.UNKNOWN,
    val termuxDetail: String = "尚未檢查",
    val permissionStatus: ServiceStatus = ServiceStatus.UNKNOWN,
    val permissionDetail: String = "尚未檢查",
    val bridgeStatus: ServiceStatus = ServiceStatus.UNKNOWN,
    val bridgeDetail: String = "尚未檢查",
    val rpcStatus: ServiceStatus = ServiceStatus.STOPPED,
    val rpcDetail: String = "RPC 尚未連線",
    val metasploitVersion: String? = null,
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenTermux: () -> Unit,
    onRunDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MAGO", style = MaterialTheme.typography.headlineMedium)
        ServiceStatusCard(
            title = "Termux",
            status = state.termuxStatus,
            detail = state.termuxDetail,
            actionLabel = if (state.termuxStatus == ServiceStatus.RUNNING) null else "開啟 Termux",
            onAction = if (state.termuxStatus == ServiceStatus.RUNNING) null else onOpenTermux,
        )
        ServiceStatusCard(
            title = "RUN_COMMAND 權限",
            status = state.permissionStatus,
            detail = state.permissionDetail,
            actionLabel = if (state.permissionStatus == ServiceStatus.RUNNING) null else "查看設定",
            onAction = if (state.permissionStatus == ServiceStatus.RUNNING) null else onOpenTermux,
        )
        ServiceStatusCard("MAGO Bridge", state.bridgeStatus, state.bridgeDetail)
        ServiceStatusCard(
            title = "Metasploit RPC",
            status = state.rpcStatus,
            detail = state.rpcDetail,
            actionLabel = if (state.rpcStatus == ServiceStatus.RUNNING) null else "執行診斷",
            onAction = if (state.rpcStatus == ServiceStatus.RUNNING) null else onRunDiagnostics,
        )
        ServiceStatusCard(
            title = "Metasploit 版本",
            status = if (state.metasploitVersion != null) ServiceStatus.RUNNING else ServiceStatus.UNKNOWN,
            detail = state.metasploitVersion ?: "尚未取得",
        )
    }
}
