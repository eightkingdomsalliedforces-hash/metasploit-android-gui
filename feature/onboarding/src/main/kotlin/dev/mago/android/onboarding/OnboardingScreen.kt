package dev.mago.android.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.installation.InstallationState
import dev.mago.android.ui.components.InstallationStepper

@Composable
fun OnboardingScreen(
    state: InstallationState,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onShowDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("MAGO 初始設定", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Metasploit、PostgreSQL 與 RPC 將安裝在同一台裝置的 Termux 私有環境。首次安裝可能需要較長時間，Android 與 Termux 的確認畫面不會被隱藏。",
            modifier = Modifier.padding(vertical = 12.dp),
        )
        InstallationStepper(
            state = state,
            onRetry = onRetry,
            onOpenTermux = onOpenTermux,
            onRequestTermuxPermission = onRequestTermuxPermission,
            onShowDetails = onShowDetails,
        )
    }
}
