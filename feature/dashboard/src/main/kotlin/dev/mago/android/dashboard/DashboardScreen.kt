package dev.mago.android.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.ServiceStatus
import dev.mago.android.ui.components.ServiceStatusCard
import java.text.DateFormat
import java.util.Date

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
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val sessions: List<MetasploitSessionSummary> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val operationsLoading: Boolean = false,
    val operationsError: String? = null,
    val maintenanceConfirmation: MaintenanceAction? = null,
    val maintenanceLoading: Boolean = false,
    val maintenanceMessage: String? = null,
    val maintenanceError: String? = null,
    val maintenanceHealthSummary: String? = null,
    val appLockEnabled: Boolean = false,
    val appLockSettingBusy: Boolean = false,
    val appLockError: String? = null,
    val onRefreshOperations: () -> Unit = {},
    val onSelectJob: (String) -> Unit = {},
    val onRequestMaintenance: (MaintenanceAction) -> Unit = {},
    val onConfirmMaintenance: () -> Unit = {},
    val onCancelMaintenance: () -> Unit = {},
    val onRequestAppLockChange: (Boolean) -> Unit = {},
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenTermux: () -> Unit,
    onRunDiagnostics: () -> Unit,
) {
    state.maintenanceConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = state.onCancelMaintenance,
            title = { Text(action.confirmationTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(action.confirmationMessage)
                    Text("操作只會執行一次，完成後會自動做一次健康檢查。")
                }
            },
            confirmButton = {
                TextButton(onClick = state.onConfirmMaintenance) { Text("確認執行") }
            },
            dismissButton = {
                TextButton(onClick = state.onCancelMaintenance) { Text("取消") }
            },
        )
    }

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

        HorizontalDivider()
        Text("安全性", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("App 鎖與敏感畫面保護", style = MaterialTheme.typography.titleMedium)
                Text(if (state.appLockEnabled) "App 鎖已啟用" else "App 鎖目前關閉")
                Text("啟用後，App 離開前景會要求生物辨識或裝置 PIN／圖形重新解鎖。MAGO 不會保存生物特徵或裝置密碼。")
                Text("敏感畫面截圖與最近使用畫面預覽會持續受到系統保護。")
                state.appLockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { state.onRequestAppLockChange(!state.appLockEnabled) },
                    enabled = !state.appLockSettingBusy,
                ) {
                    Text(
                        when {
                            state.appLockSettingBusy -> "等待系統驗證"
                            state.appLockEnabled -> "停用 App 鎖"
                            else -> "啟用 App 鎖"
                        },
                    )
                }
            }
        }

        HorizontalDivider()
        Text("維護", style = MaterialTheme.typography.titleLarge)
        Text("所有操作都使用固定 Bridge 白名單，不會執行使用者輸入的 Shell，也不會自動排程。")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { state.onRequestMaintenance(MaintenanceAction.UPDATE_METASPLOIT) },
                enabled = !state.maintenanceLoading,
            ) {
                Text("更新 Metasploit")
            }
            OutlinedButton(
                onClick = { state.onRequestMaintenance(MaintenanceAction.CLEAN_CACHE) },
                enabled = !state.maintenanceLoading,
            ) {
                Text("清理快取")
            }
        }
        if (state.maintenanceLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("維護操作執行中，請保持 App 與 Termux 可用。")
        }
        state.maintenanceMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.maintenanceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.maintenanceHealthSummary?.let {
            Text("健康狀態：$it", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Jobs 與 Sessions", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = state.onRefreshOperations,
                enabled = !state.operationsLoading,
            ) {
                Text("重新整理")
            }
        }
        Text("唯讀檢視；不提供 Session 命令、停止或批量操作。")
        if (state.operationsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.operationsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Jobs（${state.jobs.size}）", style = MaterialTheme.typography.titleMedium)
        if (!state.operationsLoading && state.jobs.isEmpty()) {
            Text("目前沒有執行中的 Job")
        }
        state.jobs.forEach { job -> JobCard(job, state.onSelectJob) }
        state.selectedJob?.let { job -> JobDetailCard(job) }

        Text("Sessions（${state.sessions.size}）", style = MaterialTheme.typography.titleMedium)
        if (!state.operationsLoading && state.sessions.isEmpty()) {
            Text("目前沒有 Session")
        }
        state.sessions.forEach { session -> SessionCard(session) }
    }
}

@Composable
private fun JobCard(job: MetasploitJobSummary, onSelect: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Job #${job.id}", style = MaterialTheme.typography.titleMedium)
            Text(job.name)
            OutlinedButton(onClick = { onSelect(job.id) }) { Text("查看詳情") }
        }
    }
}

@Composable
private fun JobDetailCard(job: MetasploitJobInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Job #${job.id} 詳情", style = MaterialTheme.typography.titleMedium)
            Text(job.name)
            job.startTimeEpochSeconds?.let {
                Text("開始時間：${DateFormat.getDateTimeInstance().format(Date(it * 1_000))}")
            }
            job.uriPath?.let { Text("URI：$it") }
            if (job.datastore.isNotEmpty()) {
                Text("Datastore", style = MaterialTheme.typography.labelLarge)
                job.datastore.toSortedMap().forEach { (name, value) ->
                    Text("$name：$value")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: MetasploitSessionSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Session #${session.id}", style = MaterialTheme.typography.titleMedium)
            Text(listOf(session.type, session.platform, session.architecture).filterNotNull().joinToString(" · "))
            if (session.description.isNotBlank()) Text(session.description)
            if (session.info.isNotBlank()) Text(session.info)
            session.sessionHost?.let { host ->
                Text("主機：$host${session.sessionPort?.let { ":$it" }.orEmpty()}")
            }
            session.username?.let { Text("使用者：$it") }
            if (session.workspace.isNotBlank()) Text("Workspace：${session.workspace}")
            session.viaExploit?.let { Text("來源模組：$it") }
            session.viaPayload?.let { Text("Payload：$it") }
            if (session.routes.isNotEmpty()) Text("Routes：${session.routes.joinToString()}")
        }
    }
}
