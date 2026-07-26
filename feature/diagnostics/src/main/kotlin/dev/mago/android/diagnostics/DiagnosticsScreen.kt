package dev.mago.android.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsScreen(
    uiModel: DiagnosticsUiModel,
    onCopySummary: (String) -> Boolean,
) {
    var copyStatus by remember { mutableStateOf<DiagnosticsCopyStatus?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("診斷資訊", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "診斷資料只會在你按下複製後寫入系統剪貼簿，不會自動上傳。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { AboutCard(uiModel.about) }
        item { SystemAndInstallationCard(uiModel.system, uiModel.installation) }

        item {
            Text("Bridge 狀態", style = MaterialTheme.typography.titleMedium)
        }

        if (uiModel.bridgeEntries.isEmpty()) {
            item {
                Text("尚未取得 Bridge 診斷狀態。")
            }
        } else {
            items(uiModel.bridgeEntries, key = { it.key }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(entry.label, style = MaterialTheme.typography.labelLarge)
                        Text(entry.displayValue)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    copyStatus = if (onCopySummary(uiModel.copySummary)) {
                        DiagnosticsCopyStatus.SUCCESS
                    } else {
                        DiagnosticsCopyStatus.FAILURE
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("複製已遮罩的診斷摘要")
            }
        }

        copyStatus?.let { status ->
            item {
                Text(
                    text = when (status) {
                        DiagnosticsCopyStatus.SUCCESS -> "已複製診斷摘要"
                        DiagnosticsCopyStatus.FAILURE -> "無法複製診斷摘要"
                    },
                    color = when (status) {
                        DiagnosticsCopyStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        DiagnosticsCopyStatus.FAILURE -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutCard(about: DiagnosticsAboutInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("關於 MAGO", style = MaterialTheme.typography.titleMedium)
            Text("MAGO ${about.appVersionName} (${about.appVersionCode})")
            Text("Bridge bundle：v${about.bridgeVersion}")
            Text("Bridge SHA-256：${about.bridgeSha256.take(12)}…")
            Text("最低支援：Android 12 / API ${about.minimumApi}")
            Text(
                "診斷資料不會自動上傳",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SystemAndInstallationCard(
    system: DiagnosticsSystemInfo,
    installation: DiagnosticsInstallationInfo,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("系統與安裝狀態", style = MaterialTheme.typography.titleMedium)
            Text("Android：${system.androidRelease.orUnknown()} / API ${system.apiLevel}")
            Text("CPU ABI：${system.primaryAbi.orUnknown()}")
            Text("Metasploit：${system.metasploitVersion.orUnknown()}")
            Text("目前階段：${installation.currentStage}")
            Text("最近成功階段：${installation.lastSuccessfulStage.orUnknown()}")
            Text("失敗類型：${installation.failureKind.orNone()}")
            Text("最近錯誤代碼：${installation.errorCode.orNone()}")
        }
    }
}

private fun String?.orUnknown(): String = this?.takeIf(String::isNotBlank) ?: "尚未取得"

private fun String?.orNone(): String = this?.takeIf(String::isNotBlank) ?: "無"

private enum class DiagnosticsCopyStatus {
    SUCCESS,
    FAILURE,
}