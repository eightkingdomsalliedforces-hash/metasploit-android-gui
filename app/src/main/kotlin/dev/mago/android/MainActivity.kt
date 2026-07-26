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
import dev.mago.android.inventory.InventoryViewModel
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
        DashboardViewModel.factory(
            coordinator = container.bootstrapCoordinator,
            operationsRepository = container.metasploitOperationsRepository,
        )
    }
    private val inventoryViewModel by viewModels<InventoryViewModel> {
        InventoryViewModel.factory(container.metasploitInventoryRepository)
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
            val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
            val modulesState by modulesViewModel.uiState.collectAsStateWithLifecycle()
            val terminalState by terminalViewModel.uiState.collectAsStateWithLifecycle()
            val diagnostics by container.bootstrapCoordinator.diagnostics.collectAsStateWithLifecycle()
            MagoApp(
                installationState = installationState,
                dashboardState = dashboardState,
                inventoryState = inventoryState,
                modulesState = modulesState,
                terminalState = terminalState,
                diagnostics = diagnostics,
                onRetry = onboardingViewModel::retry,
                onOpenTermux = onboardingViewModel::openTermux,
                onRequestTermuxPermission = {
                    permissionLauncher.launch("com.termux.permission.RUN_COMMAND")
                },
                onInventoryRefresh = inventoryViewModel::refresh,
                onInventoryWorkspaceSelected = inventoryViewModel::selectWorkspace,
                onInventoryTabSelected = inventoryViewModel::selectTab,
                onInventoryShowCreateWorkspace = inventoryViewModel::showCreateWorkspace,
                onInventoryWorkspaceDraftChanged = inventoryViewModel::setWorkspaceDraft,
                onInventorySubmitCreateWorkspace = inventoryViewModel::submitCreateWorkspace,
                onInventoryDismissCreateWorkspace = inventoryViewModel::dismissCreateWorkspace,
                onInventorySetActiveWorkspace = inventoryViewModel::setSelectedWorkspaceActive,
                onModuleTypeSelected = modulesViewModel::selectType,
                onModuleQueryChanged = modulesViewModel::setQuery,
                onModuleListModeSelected = modulesViewModel::setListMode,
                onModuleToggleFavorite = modulesViewModel::toggleFavorite,
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
