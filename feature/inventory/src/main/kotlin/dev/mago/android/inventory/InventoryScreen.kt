package dev.mago.android.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onRefresh: () -> Unit,
    onWorkspaceSelected: (String) -> Unit,
    onTabSelected: (InventoryTab) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("資產庫", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "唯讀顯示 Metasploit Database；不會觸發掃描或修改資料。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onRefresh, enabled = !state.loading) { Text("重新整理") }
            }
        }

        if (state.workspaces.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.workspaces.forEach { workspace ->
                        FilterChip(
                            selected = workspace.name == state.selectedWorkspace,
                            onClick = { onWorkspaceSelected(workspace.name) },
                            label = { Text(workspace.name) },
                            enabled = !state.loading,
                        )
                    }
                }
            }
        }

        item {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                InventoryTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.label) },
                        enabled = !state.loading,
                    )
                }
            }
        }

        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(
                "目前顯示 ${state.visibleCount} 筆；每類最多載入 ${InventoryViewModel.PAGE_LIMIT} 筆。",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        item { HorizontalDivider() }

        when (state.selectedTab) {
            InventoryTab.HOSTS -> items(state.hosts, key = { it.address }) { HostCard(it) }
            InventoryTab.SERVICES -> items(
                state.services,
                key = { "${it.host}:${it.port}/${it.protocol}" },
            ) { ServiceCard(it) }
            InventoryTab.VULNERABILITIES -> items(
                state.vulnerabilities,
                key = { "${it.host}:${it.port}:${it.name}" },
            ) { VulnerabilityCard(it) }
        }
        if (!state.loading && state.errorMessage == null && state.visibleCount == 0) {
            item { Text("此 Workspace 目前沒有可顯示的資料。") }
        }
    }
}

@Composable
private fun HostCard(host: MetasploitHostRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(host.address, style = MaterialTheme.typography.titleMedium)
            host.name?.let { Text("名稱：$it") }
            host.state?.let { Text("狀態：$it") }
            val os = listOfNotNull(host.operatingSystem, host.operatingSystemFlavor, host.servicePack)
                .joinToString(" ")
            if (os.isNotBlank()) Text("作業系統：$os")
            host.purpose?.let { Text("用途：$it") }
            host.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ServiceCard(service: MetasploitServiceRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${service.host}:${service.port}/${service.protocol}",
                style = MaterialTheme.typography.titleMedium,
            )
            service.name?.let { Text("服務：$it") }
            service.state?.let { Text("狀態：$it") }
            service.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun VulnerabilityCard(vulnerability: MetasploitVulnerabilityRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(vulnerability.name, style = MaterialTheme.typography.titleMedium)
            val endpoint = buildString {
                append(vulnerability.host)
                vulnerability.port?.let { append(":$it") }
                vulnerability.protocol?.let { append("/$it") }
            }
            Text(endpoint)
            if (vulnerability.references.isNotEmpty()) {
                Text("參考：${vulnerability.references.joinToString()}")
            }
            vulnerability.resource?.let { Text("Resource：$it") }
        }
    }
}
