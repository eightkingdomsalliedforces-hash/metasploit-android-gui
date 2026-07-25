package dev.mago.android.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(
    state: TerminalUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onClearOutput: () -> Unit,
) {
    DisposableEffect(Unit) {
        onStart()
        onDispose(onStop)
    }
    val outputScroll = rememberScrollState()
    LaunchedEffect(state.output.length) {
        outputScroll.scrollTo(outputScroll.maxValue)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("MSF Console", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        state.connecting -> "正在連線"
                        state.consoleId != null -> "Console ${state.consoleId} · ${if (state.busy) "忙碌" else "就緒"}"
                        else -> "尚未建立 Console"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新讀取 Console")
                }
                IconButton(onClick = onClearOutput) {
                    Icon(Icons.Default.ClearAll, contentDescription = "清除畫面輸出")
                }
            }
        }
        if (state.connecting) CircularProgressIndicator()
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(12.dp),
        ) {
            Text(
                text = state.output.ifEmpty { "等待 Metasploit Console 輸出…" },
                modifier = Modifier.fillMaxSize().verticalScroll(outputScroll),
                color = Color(0xFFE6E6E6),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(state.prompt, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                label = { Text("命令") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Button(onClick = onSend, enabled = state.input.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = null)
                Text("送出")
            }
        }
    }
}
