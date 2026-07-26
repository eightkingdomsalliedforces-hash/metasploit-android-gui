package dev.mago.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mago.android.ui.theme.MagoTheme

@Composable
fun AppLockScreen(
    initializing: Boolean,
    authenticationInProgress: Boolean,
    errorMessage: String?,
    onUnlock: () -> Unit,
) {
    MagoTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (initializing) {
                    CircularProgressIndicator()
                    Text(
                        "正在載入安全設定",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("功能畫面會在安全設定確認後顯示。")
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Text(
                        "MAGO 已鎖定",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("使用生物辨識或裝置 PIN／圖形解鎖。")
                    errorMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onUnlock,
                        enabled = !authenticationInProgress,
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(if (authenticationInProgress) "等待系統驗證" else "解鎖 MAGO")
                    }
                }
            }
        }
    }
}
