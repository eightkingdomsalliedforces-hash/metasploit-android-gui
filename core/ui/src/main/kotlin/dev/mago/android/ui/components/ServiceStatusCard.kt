package dev.mago.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.mago.android.model.ServiceStatus

@Composable
fun ServiceStatusCard(
    title: String,
    status: ServiceStatus,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (icon, statusText) = when (status) {
        ServiceStatus.RUNNING -> Icons.Default.CheckCircle to "正常"
        ServiceStatus.STARTING -> Icons.Default.HourglassTop to "啟動中"
        ServiceStatus.STOPPING -> Icons.Default.HourglassTop to "停止中"
        ServiceStatus.STOPPED -> Icons.Default.PauseCircle to "未執行"
        ServiceStatus.ERROR -> Icons.Default.Error to "需要修復"
        ServiceStatus.PERMISSION_REQUIRED -> Icons.Default.Warning to "需要權限"
        ServiceStatus.UNKNOWN -> Icons.Default.Warning to "未知"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = statusText
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(icon, contentDescription = null)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Text(statusText, style = MaterialTheme.typography.labelLarge)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
