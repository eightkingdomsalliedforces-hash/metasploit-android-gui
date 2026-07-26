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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.mago.android.model.MetasploitModuleOption
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue

@Composable
fun ModulesScreen(
    state: ModulesUiState,
    onTypeSelected: (MetasploitModuleType) -> Unit,
    onQueryChanged: (String) -> Unit,
    onModuleSelected: (MetasploitModuleSummary) -> Unit,
    onBackToList: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOptionChanged: (String, String) -> Unit,
    onRequestCheck: () -> Unit,
    onRequestExecute: () -> Unit,
    onAuthorizationChanged: (Boolean) -> Unit,
    onConfirmRun: () -> Unit,
    onCancelRun: () -> Unit,
    onRefreshResult: () -> Unit,
) {
    LaunchedEffect(state.type, state.modules.isEmpty(), state.errorMessage) {
        if (state.modules.isEmpty() && !state.loading && state.errorMessage == null) onRetry()
    }
    state.confirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = onCancelRun,
            title = {
                Text(
                    if (confirmation.action == MetasploitModuleRunAction.CHECK) {
                        "確認執行檢查"
                    } else {
                        "確認執行模組"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模組：${confirmation.request.type.rpcName}/${confirmation.request.name}")
                    Text("參數摘要中的敏感值已遮罩；此確認不會被記住。")
                    if (confirmation.redactedOptions.isEmpty()) {
                        Text("沒有非空白參數")
                    } else {
                        confirmation.redactedOptions.forEach { (name, value) ->
                            Text("$name：$value")
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAuthorizationChanged(!state.authorizationConfirmed) },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = state.authorizationConfirmed,
                            onCheckedChange = onAuthorizationChanged,
                        )
                        Text("我確認僅在本人擁有或已獲明確授權的環境執行")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRun,
                    enabled = state.authorizationConfirmed && !state.runLoading,
                ) {
                    Text(if (confirmation.action == MetasploitModuleRunAction.CHECK) "確認檢查" else "確認執行")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRun) { Text("取消") }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val wide = availableWidth >= 700.dp
        if (!wide && state.selected != null) {
            ModuleDetail(
                state = state,
                onBack = onBackToList,
                onToggleFavorite = onToggleFavorite,
                onOptionChanged = onOptionChanged,
                onRequestCheck = onRequestCheck,
                onRequestExecute = onRequestExecute,
                onRefreshResult = onRefreshResult,
                modifier = Modifier.fillMaxSize(),
            )
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
                        Text("模組執行前會顯示參數摘要並要求明確確認。")
                    }
                } else {
                    ModuleDetail(
                        state = state,
                        onBack = null,
                        onToggleFavorite = onToggleFavorite,
                        onOptionChanged = onOptionChanged,
                        onRequestCheck = onRequestCheck,
                        onRequestExecute = onRequestExecute,
                        onRefreshResult = onRefreshResult,
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
        if (state.offline) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("離線快取", style = MaterialTheme.typography.titleMedium)
                    Text("目前顯示本機快取；Check、Execute 與結果查詢已停用。")
                }
            }
        }
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
        if (state.recent.isNotEmpty() && state.query.isBlank()) {
            Text("最近使用", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.recent.take(10).forEach { module ->
                    FilterChip(
                        selected = false,
                        onClick = { onModuleSelected(module) },
                        label = { Text(module.name) },
                    )
                }
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleModules, key = { it.fullName }) { module ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModuleSelected(module) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            (if (module in state.favorites) "★ " else "") + module.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(module.type.displayName, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleDetail(
    state: ModulesUiState,
    onBack: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onOptionChanged: (String, String) -> Unit,
    onRequestCheck: () -> Unit,
    onRequestExecute: () -> Unit,
    onRefreshResult: () -> Unit,
    modifier: Modifier,
) {
    val info = state.selected ?: return
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
        OutlinedButton(onClick = onToggleFavorite) {
            Text(if (state.selectedIsFavorite) "取消收藏" else "加入收藏")
        }
        if (state.offline) {
            Text("離線詳細資料可能不完整，且不能執行模組。", color = MaterialTheme.colorScheme.error)
        }
        info.rank?.let { Text("Rank：$it") }
        Text(info.description.ifBlank { "沒有描述" })
        if (info.platforms.isNotEmpty()) Text("平台：${info.platforms.joinToString()}")
        if (info.architectures.isNotEmpty()) Text("架構：${info.architectures.joinToString()}")
        if (info.authors.isNotEmpty()) Text("作者：${info.authors.joinToString()}")
        Text("Check：${if (info.hasCheck) "支援" else "不支援"}")
        info.stance?.let { Text("Stance：$it") }

        HorizontalDivider()
        Text("參數", style = MaterialTheme.typography.titleLarge)
        val basic = info.options.filterNot { it.advanced }
        val advanced = info.options.filter { it.advanced }
        if (basic.isEmpty() && advanced.isEmpty()) Text("此模組沒有可顯示的參數")
        OptionFields(basic, state, onOptionChanged)
        if (advanced.isNotEmpty()) {
            Text("進階參數", style = MaterialTheme.typography.titleMedium)
            OptionFields(advanced, state, onOptionChanged)
        }

        if (state.compatiblePayloads.isNotEmpty()) {
            Text("相容 Payload", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.compatiblePayloads.forEach { payload ->
                    FilterChip(
                        selected = state.optionValues["PAYLOAD"] == payload,
                        onClick = { onOptionChanged("PAYLOAD", payload) },
                        label = { Text(payload) },
                    )
                }
            }
        }

        state.runErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.runLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.canCheck) {
                OutlinedButton(onClick = onRequestCheck, enabled = !state.runLoading) {
                    Text("執行檢查")
                }
            }
            if (state.canExecute) {
                Button(onClick = onRequestExecute, enabled = !state.runLoading) {
                    Text("執行模組")
                }
            }
        }

        state.launch?.let { launch ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("模組已送出", style = MaterialTheme.typography.titleMedium)
                    Text("UUID：${launch.uuid}")
                    launch.jobId?.let { Text("Job ID：$it") }
                    OutlinedButton(
                        onClick = onRefreshResult,
                        enabled = !state.runLoading && !state.offline,
                    ) {
                        Text("重新整理結果")
                    }
                    state.runResult?.let { result ->
                        Text("狀態：${result.status.name}")
                        result.error?.let { Text("錯誤：$it", color = MaterialTheme.colorScheme.error) }
                        result.result?.let { Text("結果：${it.displayText()}") }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("執行紀錄", style = MaterialTheme.typography.titleLarge)
        if (state.selectedHistory.isEmpty()) {
            Text("尚無執行紀錄")
        } else {
            state.selectedHistory.take(20).forEach { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${record.action.name} · ${record.status.name}")
                        Text("Correlation：${record.correlationId}")
                        record.uuid?.let { Text("UUID：$it") }
                        record.jobId?.let { Text("Job ID：$it") }
                        if (record.redactedParameters.isNotEmpty()) {
                            Text(
                                record.redactedParameters.entries.joinToString(" · ") { (key, value) ->
                                    "$key=$value"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        if (info.references.isNotEmpty()) {
            HorizontalDivider()
            Text("參考資料", style = MaterialTheme.typography.titleLarge)
            info.references.forEach { Text("${it.type}：${it.value}") }
        }
    }
}

@Composable
private fun OptionFields(
    options: List<MetasploitModuleOption>,
    state: ModulesUiState,
    onOptionChanged: (String, String) -> Unit,
) {
    options.forEach { option ->
        val error = state.validationErrors[option.name]
        val numeric = option.type.lowercase() in setOf("int", "integer", "port")
        val sensitive = MODULE_RUN_VALIDATOR.isSensitive(option.name)
        OutlinedTextField(
            value = state.optionValues[option.name].orEmpty(),
            onValueChange = { onOptionChanged(option.name, it) },
            label = { Text(option.name + if (option.required) " *" else "") },
            supportingText = {
                Text(error ?: option.description.ifBlank { option.type })
            },
            isError = error != null,
            singleLine = option.type.lowercase() !in setOf("text", "string") ||
                !option.description.contains("command", ignoreCase = true),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            visualTransformation = if (sensitive) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (option.enums.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                option.enums.forEach { value ->
                    FilterChip(
                        selected = state.optionValues[option.name] == value,
                        onClick = { onOptionChanged(option.name, value) },
                        label = { Text(value) },
                    )
                }
            }
        }
    }
}

private fun RpcValue.displayText(): String = when (this) {
    RpcValue.Nil -> "無"
    is RpcValue.Bool -> value.toString()
    is RpcValue.IntValue -> value.toString()
    is RpcValue.FloatValue -> value.toString()
    is RpcValue.StringValue -> value
    is RpcValue.BinaryValue -> "二進位資料（${value.size} bytes）"
    is RpcValue.ArrayValue -> value.joinToString(", ") { it.displayText() }
    is RpcValue.MapValue -> value.entries.joinToString("\n") { (key, item) -> "$key：${item.displayText()}" }
}

private val MODULE_RUN_VALIDATOR = ModuleRunValidator()
