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
            "Metasploit 將在 Termux 的本機環境執行。安裝程式會依序部署 Bridge、相依套件、PostgreSQL 與本機 RPC；Android 仍會保留必要的使用者確認畫面。",
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
