package dev.mago.android

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mago.android.dashboard.DashboardScreen
import dev.mago.android.dashboard.DashboardUiState
import dev.mago.android.diagnostics.DiagnosticsScreen
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.InstallationState
import dev.mago.android.inventory.InventoryScreen
import dev.mago.android.inventory.InventoryTab
import dev.mago.android.inventory.InventoryUiState
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.modules.ModuleListMode
import dev.mago.android.modules.ModulesScreen
import dev.mago.android.modules.ModulesUiState
import dev.mago.android.navigation.MagoDestination
import dev.mago.android.onboarding.OnboardingRoute
import dev.mago.android.terminal.TerminalScreen
import dev.mago.android.terminal.TerminalUiState
import dev.mago.android.ui.theme.MagoTheme

@Composable
fun MagoApp(
    installationState: InstallationState,
    dashboardState: DashboardUiState,
    inventoryState: InventoryUiState,
    modulesState: ModulesUiState,
    terminalState: TerminalUiState,
    diagnostics: List<DiagnosticEntry>,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onInventoryRefresh: () -> Unit,
    onInventoryWorkspaceSelected: (String) -> Unit,
    onInventoryTabSelected: (InventoryTab) -> Unit,
    onInventoryShowCreateWorkspace: () -> Unit,
    onInventoryWorkspaceDraftChanged: (String) -> Unit,
    onInventorySubmitCreateWorkspace: () -> Unit,
    onInventoryDismissCreateWorkspace: () -> Unit,
    onInventorySetActiveWorkspace: () -> Unit,
    onModuleTypeSelected: (MetasploitModuleType) -> Unit,
    onModuleQueryChanged: (String) -> Unit,
    onModuleListModeSelected: (ModuleListMode) -> Unit,
    onModuleToggleFavorite: (MetasploitModuleSummary) -> Unit,
    onModuleSelected: (MetasploitModuleSummary) -> Unit,
    onModuleBack: () -> Unit,
    onModuleRetry: () -> Unit,
    onModuleOptionChanged: (String, String) -> Unit,
    onModuleRequestCheck: () -> Unit,
    onModuleRequestExecute: () -> Unit,
    onModuleAuthorizationChanged: (Boolean) -> Unit,
    onModuleConfirmRun: () -> Unit,
    onModuleCancelRun: () -> Unit,
    onModuleRefreshResult: () -> Unit,
    onTerminalStart: () -> Unit,
    onTerminalStop: () -> Unit,
    onTerminalInputChanged: (String) -> Unit,
    onTerminalSend: () -> Unit,
    onTerminalRefresh: () -> Unit,
    onTerminalClear: () -> Unit,
) {
    MagoTheme {
        val navController = rememberNavController()
        val ready = installationState.stage == InstallationStage.READY
        LaunchedEffect(ready) {
            val target = if (ready) MagoDestination.Dashboard.route else MagoDestination.Onboarding.route
            if (navController.currentDestination?.route != target) {
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val wide = availableWidth >= 600.dp
            val navEntry by navController.currentBackStackEntryAsState()
            val current = navEntry?.destination
            val destinations = listOf(
                Triple(MagoDestination.Dashboard, Icons.Default.Home, "首頁"),
                Triple(MagoDestination.Modules, Icons.Default.Apps, "模組"),
                Triple(MagoDestination.Inventory, Icons.Default.Storage, "資產"),
                Triple(MagoDestination.Terminal, Icons.Default.Code, "Console"),
                Triple(MagoDestination.Diagnostics, Icons.Default.MonitorHeart, "診斷"),
            )
            val showNavigation = current?.route != MagoDestination.Onboarding.route
            val content: @Composable (Modifier) -> Unit = { modifier ->
                AppNavHost(
                    navController = navController,
                    modifier = modifier,
                    installationState = installationState,
                    dashboardState = dashboardState,
                    inventoryState = inventoryState,
                    modulesState = modulesState,
                    terminalState = terminalState,
                    diagnostics = diagnostics,
                    onRetry = onRetry,
                    onOpenTermux = onOpenTermux,
                    onRequestTermuxPermission = onRequestTermuxPermission,
                    onShowDiagnostics = { navController.navigate(MagoDestination.Diagnostics.route) },
                    onInventoryRefresh = onInventoryRefresh,
                    onInventoryWorkspaceSelected = onInventoryWorkspaceSelected,
                    onInventoryTabSelected = onInventoryTabSelected,
                    onInventoryShowCreateWorkspace = onInventoryShowCreateWorkspace,
                    onInventoryWorkspaceDraftChanged = onInventoryWorkspaceDraftChanged,
                    onInventorySubmitCreateWorkspace = onInventorySubmitCreateWorkspace,
                    onInventoryDismissCreateWorkspace = onInventoryDismissCreateWorkspace,
                    onInventorySetActiveWorkspace = onInventorySetActiveWorkspace,
                    onModuleTypeSelected = onModuleTypeSelected,
                    onModuleQueryChanged = onModuleQueryChanged,
                    onModuleListModeSelected = onModuleListModeSelected,
                    onModuleToggleFavorite = onModuleToggleFavorite,
                    onModuleSelected = onModuleSelected,
                    onModuleBack = onModuleBack,
                    onModuleRetry = onModuleRetry,
                    onModuleOptionChanged = onModuleOptionChanged,
                    onModuleRequestCheck = onModuleRequestCheck,
                    onModuleRequestExecute = onModuleRequestExecute,
                    onModuleAuthorizationChanged = onModuleAuthorizationChanged,
                    onModuleConfirmRun = onModuleConfirmRun,
                    onModuleCancelRun = onModuleCancelRun,
                    onModuleRefreshResult = onModuleRefreshResult,
                    onTerminalStart = onTerminalStart,
                    onTerminalStop = onTerminalStop,
                    onTerminalInputChanged = onTerminalInputChanged,
                    onTerminalSend = onTerminalSend,
                    onTerminalRefresh = onTerminalRefresh,
                    onTerminalClear = onTerminalClear,
                )
            }
            if (wide && showNavigation) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        destinations.forEach { (destination, icon, description) ->
                            NavigationRailItem(
                                selected = current?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = { navController.navigate(destination.route) { launchSingleTop = true } },
                                icon = { Icon(icon, contentDescription = description) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                    content(Modifier.width(availableWidth - NAVIGATION_RAIL_WIDTH).fillMaxHeight())
                }
            } else {
                Scaffold(
                    bottomBar = {
                        if (showNavigation) {
                            NavigationBar {
                                destinations.forEach { (destination, icon, description) ->
                                    NavigationBarItem(
                                        selected = current?.hierarchy?.any { it.route == destination.route } == true,
                                        onClick = { navController.navigate(destination.route) { launchSingleTop = true } },
                                        icon = { Icon(icon, contentDescription = description) },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    content(Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier,
    installationState: InstallationState,
    dashboardState: DashboardUiState,
    inventoryState: InventoryUiState,
    modulesState: ModulesUiState,
    terminalState: TerminalUiState,
    diagnostics: List<DiagnosticEntry>,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onShowDiagnostics: () -> Unit,
    onInventoryRefresh: () -> Unit,
    onInventoryWorkspaceSelected: (String) -> Unit,
    onInventoryTabSelected: (InventoryTab) -> Unit,
    onInventoryShowCreateWorkspace: () -> Unit,
    onInventoryWorkspaceDraftChanged: (String) -> Unit,
    onInventorySubmitCreateWorkspace: () -> Unit,
    onInventoryDismissCreateWorkspace: () -> Unit,
    onInventorySetActiveWorkspace: () -> Unit,
    onModuleTypeSelected: (MetasploitModuleType) -> Unit,
    onModuleQueryChanged: (String) -> Unit,
    onModuleListModeSelected: (ModuleListMode) -> Unit,
    onModuleToggleFavorite: (MetasploitModuleSummary) -> Unit,
    onModuleSelected: (MetasploitModuleSummary) -> Unit,
    onModuleBack: () -> Unit,
    onModuleRetry: () -> Unit,
    onModuleOptionChanged: (String, String) -> Unit,
    onModuleRequestCheck: () -> Unit,
    onModuleRequestExecute: () -> Unit,
    onModuleAuthorizationChanged: (Boolean) -> Unit,
    onModuleConfirmRun: () -> Unit,
    onModuleCancelRun: () -> Unit,
    onModuleRefreshResult: () -> Unit,
    onTerminalStart: () -> Unit,
    onTerminalStop: () -> Unit,
    onTerminalInputChanged: (String) -> Unit,
    onTerminalSend: () -> Unit,
    onTerminalRefresh: () -> Unit,
    onTerminalClear: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = if (installationState.stage == InstallationStage.READY) {
            MagoDestination.Dashboard.route
        } else {
            MagoDestination.Onboarding.route
        },
        modifier = modifier,
    ) {
        composable(MagoDestination.Onboarding.route) {
            OnboardingRoute(
                state = installationState,
                onRetry = onRetry,
                onOpenTermux = onOpenTermux,
                onRequestTermuxPermission = onRequestTermuxPermission,
                onShowDetails = onShowDiagnostics,
            )
        }
        composable(MagoDestination.Dashboard.route) {
            DashboardScreen(dashboardState, onOpenTermux, onShowDiagnostics)
        }
        composable(MagoDestination.Modules.route) {
            ModulesScreen(
                state = modulesState,
                onTypeSelected = onModuleTypeSelected,
                onQueryChanged = onModuleQueryChanged,
                onListModeSelected = onModuleListModeSelected,
                onToggleFavorite = onModuleToggleFavorite,
                onModuleSelected = onModuleSelected,
                onBackToList = onModuleBack,
                onRetry = onModuleRetry,
                onOptionChanged = onModuleOptionChanged,
                onRequestCheck = onModuleRequestCheck,
                onRequestExecute = onModuleRequestExecute,
                onAuthorizationChanged = onModuleAuthorizationChanged,
                onConfirmRun = onModuleConfirmRun,
                onCancelRun = onModuleCancelRun,
                onRefreshResult = onModuleRefreshResult,
            )
        }
        composable(MagoDestination.Inventory.route) {
            InventoryScreen(
                state = inventoryState,
                onRefresh = onInventoryRefresh,
                onWorkspaceSelected = onInventoryWorkspaceSelected,
                onTabSelected = onInventoryTabSelected,
                onShowCreateWorkspace = onInventoryShowCreateWorkspace,
                onWorkspaceDraftChanged = onInventoryWorkspaceDraftChanged,
                onSubmitCreateWorkspace = onInventorySubmitCreateWorkspace,
                onDismissCreateWorkspace = onInventoryDismissCreateWorkspace,
                onSetActiveWorkspace = onInventorySetActiveWorkspace,
            )
        }
        composable(MagoDestination.Terminal.route) {
            TerminalScreen(
                state = terminalState,
                onStart = onTerminalStart,
                onStop = onTerminalStop,
                onInputChanged = onTerminalInputChanged,
                onSend = onTerminalSend,
                onRefresh = onTerminalRefresh,
                onClearOutput = onTerminalClear,
            )
        }
        composable(MagoDestination.Diagnostics.route) {
            DiagnosticsScreen(diagnostics)
        }
    }
}

private val NAVIGATION_RAIL_WIDTH = 80.dp
