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
import dev.mago.android.modules.ModulesViewModel
import dev.mago.android.onboarding.OnboardingViewModel
import dev.mago.android.terminal.TerminalViewModel

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as MagoApplication).container

    private val onboardingViewModel by viewModels<OnboardingViewModel> {
        OnboardingViewModel.factory(container.bootstrapCoordinator)
    }
    private val dashboardViewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.factory(container.bootstrapCoordinator)
    }
    private val modulesViewModel by viewModels<ModulesViewModel> {
        ModulesViewModel.factory(
            repository = container.metasploitModuleRepository,
            localStore = container.moduleLocalStore,
        )
    }
    private val terminalViewModel by viewModels<TerminalViewModel> {
        TerminalViewModel.factory(container.metasploitConsoleRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { onboardingViewModel.retry() }
            val installationState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
            val modulesState by modulesViewModel.uiState.collectAsStateWithLifecycle()
            val terminalState by terminalViewModel.uiState.collectAsStateWithLifecycle()
            val diagnostics by container.bootstrapCoordinator.diagnostics.collectAsStateWithLifecycle()
            MagoApp(
                installationState = installationState,
                dashboardState = dashboardState,
                modulesState = modulesState,
                terminalState = terminalState,
                diagnostics = diagnostics,
                onRetry = onboardingViewModel::retry,
                onOpenTermux = onboardingViewModel::openTermux,
                onRequestTermuxPermission = {
                    permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
                },
                onModuleTypeSelected = modulesViewModel::selectType,
                onModuleQueryChanged = modulesViewModel::setQuery,
                onModuleSelected = modulesViewModel::selectModule,
                onModuleBack = modulesViewModel::clearSelection,
                onModuleRetry = modulesViewModel::retry,
                onModuleOptionChanged = modulesViewModel::setOption,
                onModuleRequestCheck = modulesViewModel::requestCheck,
                onModuleRequestExecute = modulesViewModel::requestExecute,
                onModuleAuthorizationChanged = modulesViewModel::setAuthorizationConfirmed,
                onModuleConfirmRun = modulesViewModel::confirmRun,
                onModuleCancelRun = modulesViewModel::cancelRun,
                onModuleRefreshResult = modulesViewModel::refreshResult,
                onTerminalStart = terminalViewModel::start,
                onTerminalStop = terminalViewModel::stop,
                onTerminalInputChanged = terminalViewModel::setInput,
                onTerminalSend = terminalViewModel::send,
                onTerminalRefresh = terminalViewModel::refresh,
                onTerminalClear = terminalViewModel::clearOutput,
            )
        }
    }
}
