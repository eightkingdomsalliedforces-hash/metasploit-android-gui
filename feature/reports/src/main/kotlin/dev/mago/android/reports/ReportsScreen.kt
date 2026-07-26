package dev.mago.android.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.reporting.ReportFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onEnsurePreviewLoaded: () -> Unit,
    onRefreshPreview: () -> Unit,
    onPreviewTabSelected: (ReportPreviewTab) -> Unit,
    onFormatSelected: (ReportFormat) -> Unit,
    onExport: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnsurePreviewLoaded()
    }

    val preview = state.preview
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("報告預覽", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "App 內可檢視原始欄位；匯出文件仍使用安全白名單與遮罩規則。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = onRefreshPreview,
                    enabled = !state.loading,
                ) {
                    Text("重新整理")
                }
            }
        }

        if (state.initialLoading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在建立報告預覽…")
                }
            }
        } else if (state.refreshing) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在重新整理預覽…")
                }
            }
        }

        state.refreshErrorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.exportErrorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.saveMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.primary) }
        }

        if (preview != null) {
            item { PreviewSummaryCard(preview) }
        }

        item { RawPreviewNoticeCard() }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("匯出格式", style = MaterialTheme.typography.titleMedium)
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
            }
        }

        if (preview != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("預覽分類", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReportPreviewTab.entries.forEach { tab ->
                            FilterChip(
                                selected = tab == state.selectedPreviewTab,
                                onClick = { onPreviewTabSelected(tab) },
                                enabled = !state.exporting,
                                label = { Text("${tab.label} ${preview.countFor(tab)}") },
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }

            when (state.selectedPreviewTab) {
                ReportPreviewTab.HOSTS -> {
                    if (preview.hosts.isEmpty()) {
                        item { EmptyPreviewMessage("此 Workspace 目前沒有 Hosts。") }
                    } else {
                        items(preview.hosts, key = { it.address }) { host ->
                            HostPreviewCard(host)
                        }
                    }
                }

                ReportPreviewTab.SERVICES -> {
                    if (preview.services.isEmpty()) {
                        item { EmptyPreviewMessage("此 Workspace 目前沒有 Services。") }
                    } else {
                        items(
                            items = preview.services,
                            key = { "${it.host}:${it.port}/${it.protocol}" },
                        ) { service ->
                            ServicePreviewCard(service)
                        }
                    }
                }

                ReportPreviewTab.VULNERABILITIES -> {
                    if (preview.vulnerabilities.isEmpty()) {
                        item { EmptyPreviewMessage("此 Workspace 目前沒有弱點紀錄。") }
                    } else {
                        items(
                            items = preview.vulnerabilities,
                            key = { "${it.host}:${it.port}:${it.name}" },
                        ) { vulnerability ->
                            VulnerabilityPreviewCard(vulnerability)
                        }
                    }
                }

                ReportPreviewTab.EXECUTIONS -> {
                    if (preview.executions.isEmpty()) {
                        item { EmptyPreviewMessage("目前沒有本機模組執行紀錄。") }
                    } else {
                        items(preview.executions, key = { it.correlationId }) { execution ->
                            ExecutionPreviewCard(execution)
                        }
                    }
                }
            }
        } else if (!state.initialLoading && state.refreshErrorMessage == null) {
            item { EmptyPreviewMessage("尚未建立報告預覽。") }
        }

        item {
            Button(
                onClick = onExport,
                enabled = preview != null && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("產生安全版報告並選擇儲存位置")
            }
        }
    }
}

@Composable
private fun PreviewSummaryCard(preview: ReportPreviewUiModel) {
    var expanded by rememberSaveable(preview.generatedAtEpochMillis) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("快照摘要", style = MaterialTheme.typography.titleMedium)
            Text("Workspace：${preview.workspaceName}")
            Text("建立時間：${formatTimestamp(preview.generatedAtEpochMillis)}")
            Text(
                "Hosts ${preview.hosts.size}・Services ${preview.services.size}・" +
                    "弱點 ${preview.vulnerabilities.size}・執行紀錄 ${preview.executions.size}",
            )
            Text(
                "每個分類最多載入 ${ReportsViewModel.RECORD_LIMIT} 筆。",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收合 Workspace 原始欄位" else "顯示 Workspace 原始欄位")
            }
            if (expanded) {
                HorizontalDivider()
                PreviewFields(preview.workspaceFields)
            }
        }
    }
}

@Composable
private fun RawPreviewNoticeCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("原始預覽與安全匯出", style = MaterialTheme.typography.titleMedium)
            Text(
                "展開後可能顯示 MAC、資產備註、服務資訊、時間欄位、模組結果／錯誤，" +
                    "以及 Metasploit 回傳的未知 extraFields。",
            )
            Text(
                "這些原始預覽資料只存在目前 App 記憶體，不會寫入 Room、DataStore、" +
                    "Logcat、診斷資訊或報告文件。",
            )
            Text(
                "JSON、CSV、HTML 與 ZIP 仍排除 RPC 密碼、Token、Credentials、Keystore、" +
                    "Console、完整路徑、資產自由文字、extraFields、原始結果與原始錯誤。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HostPreviewCard(host: ReportHostPreviewItem) {
    ExpandablePreviewCard(
        stateKey = host.address,
        title = host.address,
        summary = {
            host.name.nonBlank()?.let { Text("名稱：$it") }
            host.state.nonBlank()?.let { Text("狀態：$it") }
            val operatingSystem = listOfNotNull(
                host.operatingSystem.nonBlank(),
                host.operatingSystemFlavor.nonBlank(),
            ).joinToString(" ")
            if (operatingSystem.isNotBlank()) Text("作業系統：$operatingSystem")
            host.purpose.nonBlank()?.let { Text("用途：$it") }
        },
        fields = host.fields,
    )
}

@Composable
private fun ServicePreviewCard(service: ReportServicePreviewItem) {
    ExpandablePreviewCard(
        stateKey = "${service.host}:${service.port}/${service.protocol}",
        title = "${service.host}:${service.port}/${service.protocol}",
        summary = {
            service.name.nonBlank()?.let { Text("服務：$it") }
            service.state.nonBlank()?.let { Text("狀態：$it") }
        },
        fields = service.fields,
    )
}

@Composable
private fun VulnerabilityPreviewCard(vulnerability: ReportVulnerabilityPreviewItem) {
    ExpandablePreviewCard(
        stateKey = "${vulnerability.host}:${vulnerability.port}:${vulnerability.name}",
        title = vulnerability.name,
        summary = {
            Text(vulnerability.endpoint())
            if (vulnerability.references.isNotEmpty()) {
                Text("參考：${vulnerability.references.joinToString()}")
            }
        },
        fields = vulnerability.fields,
    )
}

@Composable
private fun ExecutionPreviewCard(execution: ReportExecutionPreviewItem) {
    ExpandablePreviewCard(
        stateKey = execution.correlationId,
        title = "${execution.type.displayName}/${execution.name}",
        summary = {
            Text("動作：${execution.action.name}・狀態：${execution.status.name}")
            execution.jobId?.let { Text("Job ID：$it") }
            execution.uuid.nonBlank()?.let { Text("UUID：$it") }
            Text("建立：${formatTimestamp(execution.createdAtEpochMillis)}")
            Text("更新：${formatTimestamp(execution.updatedAtEpochMillis)}")
        },
        fields = execution.fields,
    )
}

@Composable
private fun ExpandablePreviewCard(
    stateKey: String,
    title: String,
    summary: @Composable () -> Unit,
    fields: List<ReportPreviewField>,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    summary()
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收合" else "完整欄位")
                }
            }
            if (expanded) {
                HorizontalDivider()
                PreviewFields(fields)
            }
        }
    }
}

@Composable
private fun PreviewFields(fields: List<ReportPreviewField>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(field.label, style = MaterialTheme.typography.labelLarge)
                PreviewValue(field.value, depth = 0)
            }
        }
    }
}

@Composable
private fun PreviewValue(value: ReportPreviewValue, depth: Int) {
    val indentation = (depth.coerceAtMost(8) * 12).dp
    when (value) {
        is ReportPreviewValue.Scalar -> {
            Text(
                text = value.text + if (value.truncated) "\n[字串已截斷]" else "",
                modifier = Modifier.padding(start = indentation),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is ReportPreviewValue.Binary -> {
            Text(
                text = buildString {
                    append(value.hex)
                    append("\n共 ${value.totalBytes} bytes")
                    if (value.truncated) append("；只顯示前 4096 bytes")
                },
                modifier = Modifier.padding(start = indentation),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is ReportPreviewValue.Array -> {
            Column(
                modifier = Modifier.padding(start = indentation),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (value.values.isEmpty()) Text("[]", style = MaterialTheme.typography.bodySmall)
                value.values.forEachIndexed { index, child ->
                    Text("[$index]", style = MaterialTheme.typography.labelSmall)
                    PreviewValue(child, depth + 1)
                }
                if (value.truncated) {
                    Text("[陣列超過 500 項，後續內容已截斷]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        is ReportPreviewValue.Object -> {
            Column(
                modifier = Modifier.padding(start = indentation),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (value.entries.isEmpty()) Text("{}", style = MaterialTheme.typography.bodySmall)
                value.entries.forEach { entry ->
                    Text(entry.key, style = MaterialTheme.typography.labelSmall)
                    PreviewValue(entry.value, depth + 1)
                }
                if (value.truncated) {
                    Text("[物件超過 500 項，後續內容已截斷]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium)
}

private fun ReportPreviewUiModel.countFor(tab: ReportPreviewTab): Int = when (tab) {
    ReportPreviewTab.HOSTS -> hosts.size
    ReportPreviewTab.SERVICES -> services.size
    ReportPreviewTab.VULNERABILITIES -> vulnerabilities.size
    ReportPreviewTab.EXECUTIONS -> executions.size
}

private fun ReportVulnerabilityPreviewItem.endpoint(): String = buildString {
    append(host)
    port?.let { append(":$it") }
    protocol.nonBlank()?.let { append("/$it") }
}

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun formatTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
