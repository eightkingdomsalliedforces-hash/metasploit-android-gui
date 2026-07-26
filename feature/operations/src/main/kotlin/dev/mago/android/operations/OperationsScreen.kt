package dev.mago.android.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary

@Composable
fun OperationsScreen(
    state: OperationsUiState,
    onTabSelected: (OperationsTab) -> Unit,
    onRefreshJobs: () -> Unit,
    onRefreshSessions: () -> Unit,
    onJobSelected: (MetasploitJobSummary) -> Unit,
    onSessionSelected: (MetasploitSessionSummary) -> Unit,
    onRequestStopJob: (MetasploitJobSummary) -> Unit,
    onRequestStopSession: (MetasploitSessionSummary) -> Unit,
    onCancelStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onRequestInteraction: (MetasploitSessionSummary) -> Unit,
    onInteractionAuthorizationChanged: (Boolean) -> Unit,
    onCancelInteractionRequest: () -> Unit,
    onOpenInteraction: () -> Unit,
    onSessionInputChanged: (String) -> Unit,
    onSendSessionInput: () -> Unit,
    onReadSessionOutput: () -> Unit,
    onClearSessionOutput: () -> Unit,
    onCloseInteraction: () -> Unit,
) {
    state.stopConfirmation?.let { confirmation ->
        val label = when (confirmation) {
            is OperationStopConfirmation.Job -> "Job ${confirmation.job.id}：${confirmation.job.name}"
            is OperationStopConfirmation.Session ->
                "Session ${confirmation.session.id}：${confirmation.session.type}"
        }
        AlertDialog(
            onDismissRequest = onCancelStop,
            title = { Text("確認停止") },
            text = { Text("將停止單一項目：\n$label\n\n此動作不會批次套用到其他項目。") },
            confirmButton = {
                Button(onClick = onConfirmStop, enabled = !state.actionLoading) { Text("確認停止") }
            },
            dismissButton = { TextButton(onClick = onCancelStop) { Text("取消") } },
        )
    }

    state.interactionCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = onCancelInteractionRequest,
            title = { Text("開啟 Session ${session.id} 互動") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session 類型：${session.type}")
                    Text("互動內容只保存在目前記憶體，不寫入資料庫或診斷紀錄。")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInteractionAuthorizationChanged(!state.interactionAuthorizationConfirmed)
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = state.interactionAuthorizationConfirmed,
                            onCheckedChange = onInteractionAuthorizationChanged,
                        )
                        Text("我確認此 Session 屬於本人擁有或已獲明確授權的環境")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onOpenInteraction,
                    enabled = state.interactionAuthorizationConfirmed,
                ) { Text("開啟互動") }
            },
            dismissButton = {
                TextButton(onClick = onCancelInteractionRequest) { Text("取消") }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Jobs 與 Sessions", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.tab == OperationsTab.JOBS,
                onClick = { onTabSelected(OperationsTab.JOBS) },
                label = { Text("Jobs (${state.jobs.size})") },
            )
            FilterChip(
                selected = state.tab == OperationsTab.SESSIONS,
                onClick = { onTabSelected(OperationsTab.SESSIONS) },
                label = { Text("Sessions (${state.sessions.size})") },
            )
            OutlinedButton(
                onClick = if (state.tab == OperationsTab.JOBS) onRefreshJobs else onRefreshSessions,
                enabled = !state.jobsLoading && !state.sessionsLoading,
            ) { Text("手動重新整理") }
        }
        if (state.jobsLoading || state.sessionsLoading || state.actionLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    OperationsListPane(
                        state = state,
                        onJobSelected = onJobSelected,
                        onSessionSelected = onSessionSelected,
                        modifier = Modifier.width(maxWidth * 0.42f).fillMaxHeight(),
                    )
                    OperationsDetailPane(
                        state = state,
                        onRequestStopJob = onRequestStopJob,
                        onRequestStopSession = onRequestStopSession,
                        onRequestInteraction = onRequestInteraction,
                        onSessionInputChanged = onSessionInputChanged,
                        onSendSessionInput = onSendSessionInput,
                        onReadSessionOutput = onReadSessionOutput,
                        onClearSessionOutput = onClearSessionOutput,
                        onCloseInteraction = onCloseInteraction,
                        modifier = Modifier.width(maxWidth * 0.58f).fillMaxHeight(),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OperationsDetailCard(
                            state = state,
                            onRequestStopJob = onRequestStopJob,
                            onRequestStopSession = onRequestStopSession,
                            onRequestInteraction = onRequestInteraction,
                            onSessionInputChanged = onSessionInputChanged,
                            onSendSessionInput = onSendSessionInput,
                            onReadSessionOutput = onReadSessionOutput,
                            onClearSessionOutput = onClearSessionOutput,
                            onCloseInteraction = onCloseInteraction,
                        )
                    }
                    when (state.tab) {
                        OperationsTab.JOBS -> items(state.jobs, key = { it.id }) { job ->
                            JobCard(job, onJobSelected)
                        }
                        OperationsTab.SESSIONS -> items(state.sessions, key = { it.id }) { session ->
                            SessionCard(session, onSessionSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationsListPane(
    state: OperationsUiState,
    onJobSelected: (MetasploitJobSummary) -> Unit,
    onSessionSelected: (MetasploitSessionSummary) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.tab) {
            OperationsTab.JOBS -> items(state.jobs, key = { it.id }) { JobCard(it, onJobSelected) }
            OperationsTab.SESSIONS -> items(state.sessions, key = { it.id }) { SessionCard(it, onSessionSelected) }
        }
    }
}

@Composable
private fun OperationsDetailPane(
    state: OperationsUiState,
    onRequestStopJob: (MetasploitJobSummary) -> Unit,
    onRequestStopSession: (MetasploitSessionSummary) -> Unit,
    onRequestInteraction: (MetasploitSessionSummary) -> Unit,
    onSessionInputChanged: (String) -> Unit,
    onSendSessionInput: () -> Unit,
    onReadSessionOutput: () -> Unit,
    onClearSessionOutput: () -> Unit,
    onCloseInteraction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(start = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OperationsDetailCard(
            state,
            onRequestStopJob,
            onRequestStopSession,
            onRequestInteraction,
            onSessionInputChanged,
            onSendSessionInput,
            onReadSessionOutput,
            onClearSessionOutput,
            onCloseInteraction,
        )
    }
}

@Composable
private fun OperationsDetailCard(
    state: OperationsUiState,
    onRequestStopJob: (MetasploitJobSummary) -> Unit,
    onRequestStopSession: (MetasploitSessionSummary) -> Unit,
    onRequestInteraction: (MetasploitSessionSummary) -> Unit,
    onSessionInputChanged: (String) -> Unit,
    onSendSessionInput: () -> Unit,
    onReadSessionOutput: () -> Unit,
    onClearSessionOutput: () -> Unit,
    onCloseInteraction: () -> Unit,
) {
    state.interaction?.let { interaction ->
        SessionInteractionCard(
            interaction = interaction,
            onInputChanged = onSessionInputChanged,
            onSend = onSendSessionInput,
            onRead = onReadSessionOutput,
            onClear = onClearSessionOutput,
            onClose = onCloseInteraction,
        )
        return
    }

    when (state.tab) {
        OperationsTab.JOBS -> {
            val info = state.selectedJob
            if (info == null) {
                EmptyDetail("選擇一個 Job 查看資訊。清單只會在你按下重新整理時更新。")
            } else {
                JobDetail(info, state.jobs.firstOrNull { it.id == info.id }, onRequestStopJob)
            }
        }
        OperationsTab.SESSIONS -> {
            val session = state.selectedSession
            if (session == null) {
                EmptyDetail("選擇一個 Session 查看資訊。App 不會在背景讀取 Session 輸出。")
            } else {
                SessionDetail(session, onRequestStopSession, onRequestInteraction)
            }
        }
    }
}

@Composable
private fun EmptyDetail(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun JobCard(job: MetasploitJobSummary, onSelected: (MetasploitJobSummary) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onSelected(job) }) {
        Column(Modifier.padding(12.dp)) {
            Text("Job ${job.id}", style = MaterialTheme.typography.titleMedium)
            Text(job.name)
        }
    }
}

@Composable
private fun SessionCard(session: MetasploitSessionSummary, onSelected: (MetasploitSessionSummary) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onSelected(session) }) {
        Column(Modifier.padding(12.dp)) {
            Text("Session ${session.id} · ${session.type}", style = MaterialTheme.typography.titleMedium)
            Text(session.description.ifBlank { session.info.ifBlank { "沒有描述" } })
            if (session.sessionHost.isNotBlank()) Text("Host：${session.sessionHost}")
        }
    }
}

@Composable
private fun JobDetail(
    info: MetasploitJobInfo,
    summary: MetasploitJobSummary?,
    onStop: (MetasploitJobSummary) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Job ${info.id}", style = MaterialTheme.typography.titleLarge)
            Text(info.name)
            info.startTimeEpochSeconds?.let { Text("開始時間（Epoch）：$it") }
            info.uriPath?.let { Text("URI：$it") }
            if (info.datastore.isNotEmpty()) {
                HorizontalDivider()
                Text("Datastore", style = MaterialTheme.typography.titleMedium)
                info.datastore.toSortedMap().forEach { (key, value) ->
                    Text("$key：${if (isSensitiveName(key)) MASK else value}")
                }
            }
            if (summary != null) {
                OutlinedButton(onClick = { onStop(summary) }) { Text("停止此 Job") }
            }
        }
    }
}

@Composable
private fun SessionDetail(
    session: MetasploitSessionSummary,
    onStop: (MetasploitSessionSummary) -> Unit,
    onInteract: (MetasploitSessionSummary) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Session ${session.id}", style = MaterialTheme.typography.titleLarge)
            Text("類型：${session.type}")
            if (session.description.isNotBlank()) Text(session.description)
            if (session.info.isNotBlank()) Text("資訊：${session.info}")
            if (session.workspace.isNotBlank()) Text("Workspace：${session.workspace}")
            if (session.sessionHost.isNotBlank()) Text("Session Host：${session.sessionHost}")
            session.sessionPort?.let { Text("Session Port：$it") }
            if (session.targetHost.isNotBlank()) Text("Target Host：${session.targetHost}")
            if (session.username.isNotBlank()) Text("Username：${session.username}")
            if (session.platform.isNotBlank()) Text("平台：${session.platform}")
            if (session.architecture.isNotBlank()) Text("架構：${session.architecture}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onInteract(session) }) { Text("開啟互動") }
                OutlinedButton(onClick = { onStop(session) }) { Text("停止此 Session") }
            }
        }
    }
}

@Composable
private fun SessionInteractionCard(
    interaction: SessionInteractionState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRead: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Session ${interaction.session.id} 互動", style = MaterialTheme.typography.titleLarge)
            Text("不會自動讀取；請按「讀取輸出」。關閉後輸入與輸出會立即清除。")
            if (interaction.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            interaction.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = interaction.output.takeLast(MAX_VISIBLE_OUTPUT).ifBlank { "尚未讀取輸出" },
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                )
            }
            OutlinedTextField(
                value = interaction.input,
                onValueChange = onInputChanged,
                label = { Text("Session 輸入") },
                supportingText = { Text("上限 8 KiB UTF-8；不接受換行或其他控制字元") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSend, enabled = !interaction.busy && interaction.input.isNotBlank()) {
                    Text("送出")
                }
                OutlinedButton(onClick = onRead, enabled = !interaction.busy) { Text("讀取輸出") }
                OutlinedButton(onClick = onClear, enabled = !interaction.busy) { Text("清除畫面") }
                TextButton(onClick = onClose, enabled = !interaction.busy) { Text("關閉互動") }
            }
        }
    }
}

private fun isSensitiveName(name: String): Boolean {
    val upper = name.uppercase()
    val tokens = upper.split(Regex("[^A-Z0-9]+")).filter(String::isNotEmpty)
    return tokens.any { it in SENSITIVE_TOKENS } || upper.endsWith("PASS") || upper.endsWith("PASSWORD")
}

private const val MASK = "••••••••"
private const val MAX_VISIBLE_OUTPUT = 50_000
private val SENSITIVE_TOKENS = setOf("PASS", "PASSWORD", "TOKEN", "KEY", "SECRET", "CREDENTIAL")
