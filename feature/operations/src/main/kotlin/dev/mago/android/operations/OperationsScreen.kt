package dev.mago.android.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo

@Composable
fun OperationsScreen(
    state: OperationsUiState,
    onTabSelected: (OperationsTab) -> Unit,
    onRefresh: () -> Unit,
    onJobSelected: (MetasploitJobSummary) -> Unit,
    onClearJobSelection: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Operations", style = MaterialTheme.typography.headlineSmall)
                Text("唯讀狀態檢視；不提供命令、停止或批量操作。")
            }
            OutlinedButton(onClick = onRefresh, enabled = !state.loading) {
                Text("重新整理")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
        if (state.loading || state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (state.tab) {
            OperationsTab.JOBS -> JobsContent(
                jobs = state.jobs,
                selected = state.selectedJob,
                onJobSelected = onJobSelected,
                onClearSelection = onClearJobSelection,
                modifier = Modifier.fillMaxSize(),
            )
            OperationsTab.SESSIONS -> SessionsContent(
                sessions = state.sessions,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun JobsContent(
    jobs: List<MetasploitJobSummary>,
    selected: MetasploitJobInfo?,
    onJobSelected: (MetasploitJobSummary) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier,
) {
    if (selected != null) {
        JobDetail(selected, onClearSelection, modifier)
        return
    }
    if (jobs.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            Text("目前沒有執行中的 Job")
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(jobs, key = { it.id }) { job ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onJobSelected(job) },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Job ${job.id}", style = MaterialTheme.typography.titleMedium)
                    Text(job.name)
                }
            }
        }
    }
}

@Composable
private fun JobDetail(
    job: MetasploitJobInfo,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text("返回 Job 列表") }
        }
        item {
            Text("Job ${job.id}", style = MaterialTheme.typography.headlineSmall)
            Text(job.name)
        }
        job.startTimeEpochSeconds?.let { started ->
            item { Text("開始時間（Unix）：$started") }
        }
        job.uriPath?.let { path -> item { Text("URI Path：$path") } }
        item {
            HorizontalDivider()
            Text("Datastore 欄位", style = MaterialTheme.typography.titleMedium)
            Text("為避免意外顯示憑證，此頁只列出欄位名稱，不顯示值。")
        }
        if (job.datastore.isEmpty()) {
            item { Text("沒有 Datastore 欄位") }
        } else {
            items(job.datastore.keys.sorted()) { key -> Text("• $key") }
        }
    }
}

@Composable
private fun SessionsContent(
    sessions: List<MetasploitSessionInfo>,
    modifier: Modifier,
) {
    if (sessions.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            Text("目前沒有作用中的 Session")
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sessions, key = { it.id }) { session ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Session ${session.id} · ${session.type}", style = MaterialTheme.typography.titleMedium)
                    if (session.description.isNotBlank()) Text(session.description)
                    if (session.info.isNotBlank()) Text(session.info)
                    if (session.workspace.isNotBlank()) Text("Workspace：${session.workspace}")
                    if (session.sessionHost.isNotBlank()) {
                        Text("Host：${session.sessionHost}${session.sessionPort?.let { ":$it" }.orEmpty()}")
                    }
                    if (session.platform?.isNotBlank() == true || session.architecture.isNotBlank()) {
                        Text("平台：${listOfNotNull(session.platform, session.architecture.takeIf(String::isNotBlank)).joinToString(" · ")}")
                    }
                    if (session.viaExploit.isNotBlank()) Text("Via exploit：${session.viaExploit}")
                    if (session.viaPayload.isNotBlank()) Text("Via payload：${session.viaPayload}")
                    if (session.routes.isNotEmpty()) Text("Routes：${session.routes.joinToString()}")
                }
            }
        }
    }
}
