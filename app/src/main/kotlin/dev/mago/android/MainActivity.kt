package dev.mago.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mago.android.dashboard.DashboardViewModel
import dev.mago.android.onboarding.OnboardingViewModel

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as MagoApplication).container

    private val onboardingViewModel by viewModels<OnboardingViewModel> {
        OnboardingViewModel.factory(container.bootstrapCoordinator)
    }
    private val dashboardViewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.factory(container.bootstrapCoordinator)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { onboardingViewModel.retry() }
            val installationState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
            val diagnostics by container.bootstrapCoordinator.diagnostics.collectAsStateWithLifecycle()
            MagoApp(
                installationState = installationState,
                dashboardState = dashboardState,
                diagnostics = diagnostics,
                onRetry = onboardingViewModel::retry,
                onOpenTermux = onboardingViewModel::openTermux,
                onRequestTermuxPermission = {
                    permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
                },
            )
        }
    }
}
