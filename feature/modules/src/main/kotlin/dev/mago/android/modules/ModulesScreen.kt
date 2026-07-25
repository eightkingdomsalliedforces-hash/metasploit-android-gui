package dev.mago.android.modules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

@Composable
fun ModulesScreen(
    state: ModulesUiState,
    onTypeSelected: (MetasploitModuleType) -> Unit,
    onQueryChanged: (String) -> Unit,
    onModuleSelected: (MetasploitModuleSummary) -> Unit,
    onBackToList: () -> Unit,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val wide = availableWidth >= 700.dp
        if (!wide && state.selected != null) {
            ModuleDetail(state.selected, onBackToList, Modifier.fillMaxSize())
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                ModuleList(
                    state = state,
                    onTypeSelected = onTypeSelected,
                    onQueryChanged = onQueryChanged,
                    onModuleSelected = onModuleSelected,
                    onRetry = onRetry,
                    modifier = Modifier.width(availableWidth * 0.42f).fillMaxHeight(),
                )
                if (state.selected == null) {
                    Column(
                        modifier = Modifier
                            .width(availableWidth * 0.58f)
                            .fillMaxHeight()
                            .padding(24.dp),
                    ) {
                        Text("選擇模組以查看詳細資料", style = MaterialTheme.typography.titleLarge)
                        Text("本階段僅讀取模組資訊，不會執行模組。")
                    }
                } else {
                    ModuleDetail(
                        state.selected,
                        onBack = null,
                        modifier = Modifier.width(availableWidth * 0.58f).fillMaxHeight(),
                    )
                }
            }
        } else {
            ModuleList(
                state = state,
                onTypeSelected = onTypeSelected,
                onQueryChanged = onQueryChanged,
                onModuleSelected = onModuleSelected,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ModuleList(
    state: ModulesUiState,
    onTypeSelected: (MetasploitModuleType) -> Unit,
    onQueryChanged: (String) -> Unit,
    onModuleSelected: (MetasploitModuleSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("模組", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetasploitModuleType.entries.forEach { type ->
                FilterChip(
                    selected = type == state.type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.displayName) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text("依名稱搜尋") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text("重試") }
        }
        Text("${state.visibleModules.size} 個模組", style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.visibleModules, key = { it.fullName }) { module ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModuleSelected(module) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(module.name, style = MaterialTheme.typography.bodyLarge)
                        Text(module.type.displayName, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleDetail(
    info: MetasploitModuleInfo,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回模組列表")
            }
        }
        Text(info.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(info.type.rpcName + "/" + info.name, style = MaterialTheme.typography.labelLarge)
        info.rank?.let { Text("Rank：$it") }
        Text(info.description.ifBlank { "沒有描述" })
        if (info.platforms.isNotEmpty()) Text("平台：${info.platforms.joinToString()}")
        if (info.architectures.isNotEmpty()) Text("架構：${info.architectures.joinToString()}")
        if (info.authors.isNotEmpty()) Text("作者：${info.authors.joinToString()}")
        Text("Check：${if (info.hasCheck) "支援" else "不支援"}")
        info.stance?.let { Text("Stance：$it") }
        Text("參數", style = MaterialTheme.typography.titleLarge)
        if (info.options.isEmpty()) Text("此模組沒有可顯示的參數")
        info.options.forEach { option ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(option.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            option.type,
                            if (option.required) "必填" else null,
                            if (option.advanced) "進階" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (option.description.isNotBlank()) Text(option.description)
                    option.defaultValue?.let { Text("預設：$it") }
                    if (option.enums.isNotEmpty()) Text("可選：${option.enums.joinToString()}")
                }
            }
        }
        if (info.references.isNotEmpty()) {
            Text("參考資料", style = MaterialTheme.typography.titleLarge)
            info.references.forEach { Text("${it.type}：${it.value}") }
        }
    }
}
