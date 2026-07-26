package dev.mago.android.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.reporting.ReportFormat

@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEnsurePreviewLoaded: () -> Unit,
    onFormatSelected: (ReportFormat) -> Unit,
    onExport: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnsurePreviewLoaded()
    }

    val preview = state.preview
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("報告", style = MaterialTheme.typography.headlineSmall)
        Text(
            "透過 Android 系統檔案選擇器匯出；MAGO 不會要求廣泛儲存權限。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("格式", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportFormat.entries.forEach { format ->
                FilterChip(
                    selected = state.format == format,
                    onClick = { onFormatSelected(format) },
                    enabled = !state.exporting,
                    label = { Text(format.displayName) },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("包含", style = MaterialTheme.typography.titleMedium)
                Text("• 作用中 Workspace 的 Hosts、Services、Vulnerabilities，各最多 100 筆")
                Text("• 本機已遮罩的模組執行紀錄，最多 100 筆")
                Text("不包含", style = MaterialTheme.typography.titleMedium)
                Text("• RPC 密碼、Token、Credentials、Keystore、Console、完整路徑")
                Text("• 資產自由文字、模組結果內容與錯誤內容")
            }
        }

        Text(
            "作用中 Workspace：${preview?.workspaceName ?: "尚未載入"}",
            style = MaterialTheme.typography.labelLarge,
        )
        if (preview != null) {
            Text(
                "Hosts ${preview.hosts.size}・Services ${preview.services.size}・" +
                    "Vulnerabilities ${preview.vulnerabilities.size}・執行紀錄 ${preview.executions.size}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.refreshErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.exportErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Button(
            onClick = onExport,
            enabled = preview != null && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("產生安全版報告並選擇儲存位置")
        }
    }
}