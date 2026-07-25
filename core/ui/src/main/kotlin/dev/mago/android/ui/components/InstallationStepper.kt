package dev.mago.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.InstallationState
import dev.mago.android.model.SuggestedAction

@Composable
fun InstallationStepper(
    state: InstallationState,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("環境設定", style = MaterialTheme.typography.titleLarge)
            val stages = listOf(
                InstallationStage.CHECKING_DEVICE,
                InstallationStage.TERMUX_REQUIRED,
                InstallationStage.TERMUX_INITIALIZATION_REQUIRED,
                InstallationStage.PERMISSION_REQUIRED,
                InstallationStage.DEPLOYING_BRIDGE,
                InstallationStage.UPDATING_PACKAGES,
                InstallationStage.INSTALLING_DEPENDENCIES,
                InstallationStage.INSTALLING_METASPLOIT,
                InstallationStage.INITIALIZING_DATABASE,
                InstallationStage.CONFIGURING_RPC,
                InstallationStage.STARTING_SERVICES,
                InstallationStage.VERIFYING,
                InstallationStage.READY,
            )
            stages.forEach { stage ->
                val completed = stage.ordinal < state.stage.ordinal || state.stage == InstallationStage.READY
                val current = stage == state.stage
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = when {
                            completed -> Icons.Default.CheckCircle
                            current -> Icons.Default.Sync
                            else -> Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = when {
                            completed -> "已完成"
                            current -> "目前步驟"
                            else -> "尚未開始"
                        },
                    )
                    Text(stage.label())
                }
            }
            if (state.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.progress}%")
            }
            state.lastError?.let { error ->
                Text(error.userMessage, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.retryable) Button(onClick = onRetry) { Text("重試") }
                    if (state.stage == InstallationStage.TERMUX_REQUIRED ||
                        error.suggestedAction == SuggestedAction.OPEN_TERMUX
                    ) {
                        OutlinedButton(onClick = onOpenTermux) { Text("開啟 Termux") }
                    }
                    if (state.stage == InstallationStage.PERMISSION_REQUIRED ||
                        error.suggestedAction == SuggestedAction.GRANT_PERMISSION
                    ) {
                        OutlinedButton(onClick = onRequestTermuxPermission) { Text("授予權限") }
                    }
                    OutlinedButton(onClick = onShowDetails) { Text("技術細節") }
                }
            }
        }
    }
}

private fun InstallationStage.label(): String = when (this) {
    InstallationStage.NOT_STARTED -> "尚未開始"
    InstallationStage.CHECKING_DEVICE -> "檢查裝置"
    InstallationStage.TERMUX_REQUIRED -> "確認 Termux"
    InstallationStage.TERMUX_INITIALIZATION_REQUIRED -> "初始化 Termux"
    InstallationStage.PERMISSION_REQUIRED -> "取得命令權限"
    InstallationStage.DEPLOYING_BRIDGE -> "部署 Bridge"
    InstallationStage.UPDATING_PACKAGES -> "更新套件"
    InstallationStage.INSTALLING_DEPENDENCIES -> "安裝相依套件"
    InstallationStage.INSTALLING_METASPLOIT -> "安裝 Metasploit"
    InstallationStage.INITIALIZING_DATABASE -> "初始化資料庫"
    InstallationStage.CONFIGURING_RPC -> "設定 RPC"
    InstallationStage.STARTING_SERVICES -> "啟動服務"
    InstallationStage.VERIFYING -> "驗證環境"
    InstallationStage.READY -> "完成"
}
