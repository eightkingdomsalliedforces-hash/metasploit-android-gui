package dev.mago.android

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
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
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.navigation.MagoDestination
import dev.mago.android.onboarding.OnboardingRoute
import dev.mago.android.ui.theme.MagoTheme

@Composable
fun MagoApp(
    installationState: InstallationState,
    dashboardState: DashboardUiState,
    diagnostics: List<DiagnosticEntry>,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
) {
    MagoTheme {
        val navController = rememberNavController()
        val ready = installationState.stage == InstallationStage.READY
        LaunchedEffect(ready) {
            val target = if (ready) {
                MagoDestination.Dashboard.route
            } else {
                MagoDestination.Onboarding.route
            }
            if (navController.currentDestination?.route != target) {
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val wide = availableWidth >= 600.dp
            val navEntry by navController.currentBackStackEntryAsState()
            val current = navEntry?.destination
            val permanentDestinations = listOf(
                MagoDestination.Dashboard to Icons.Default.Home,
                MagoDestination.Diagnostics to Icons.Default.MonitorHeart,
            )
            val showNavigation = current?.route != MagoDestination.Onboarding.route
            if (wide && showNavigation) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        permanentDestinations.forEach { (destination, icon) ->
                            NavigationRailItem(
                                selected = current?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = { navController.navigate(destination.route) { launchSingleTop = true } },
                                icon = { Icon(icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier
                            .width(availableWidth - NAVIGATION_RAIL_WIDTH)
                            .fillMaxHeight(),
                        installationState = installationState,
                        dashboardState = dashboardState,
                        diagnostics = diagnostics,
                        onRetry = onRetry,
                        onOpenTermux = onOpenTermux,
                        onRequestTermuxPermission = onRequestTermuxPermission,
                        onShowDiagnostics = { navController.navigate(MagoDestination.Diagnostics.route) },
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        if (showNavigation) {
                            NavigationBar {
                                permanentDestinations.forEach { (destination, icon) ->
                                    NavigationBarItem(
                                        selected = current?.hierarchy?.any { it.route == destination.route } == true,
                                        onClick = { navController.navigate(destination.route) { launchSingleTop = true } },
                                        icon = { Icon(icon, contentDescription = destination.label) },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        installationState = installationState,
                        dashboardState = dashboardState,
                        diagnostics = diagnostics,
                        onRetry = onRetry,
                        onOpenTermux = onOpenTermux,
                        onRequestTermuxPermission = onRequestTermuxPermission,
                        onShowDiagnostics = { navController.navigate(MagoDestination.Diagnostics.route) },
                    )
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
    diagnostics: List<DiagnosticEntry>,
    onRetry: () -> Unit,
    onOpenTermux: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onShowDiagnostics: () -> Unit,
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
        composable(MagoDestination.Diagnostics.route) {
            DiagnosticsScreen(diagnostics)
        }
    }
}

private val NAVIGATION_RAIL_WIDTH = 80.dp
