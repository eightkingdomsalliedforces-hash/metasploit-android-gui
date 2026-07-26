package dev.mago.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.mago.android.common.AppResult
import dev.mago.android.dashboard.DashboardViewModel
import dev.mago.android.inventory.InventoryViewModel
import dev.mago.android.modules.ModulesViewModel
import dev.mago.android.onboarding.OnboardingViewModel
import dev.mago.android.reporting.ReportDocument
import dev.mago.android.reporting.ReportFormat
import dev.mago.android.reports.ReportsViewModel
import dev.mago.android.terminal.TerminalViewModel
import kotlinx.coroutines.launch

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
    private val reportsViewModel by viewModels<ReportsViewModel> {
        ReportsViewModel.factory(
            inventoryRepository = container.metasploitInventoryRepository,
            moduleLocalStore = container.moduleLocalStore,
            documentBuilder = container.reportDocumentBuilder,
        )
    }
    private val terminalViewModel by viewModels<TerminalViewModel> {
        TerminalViewModel.factory(container.metasploitConsoleRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var pendingReport by remember { mutableStateOf<ReportDocument?>(null) }

            fun handleReportDestination(uri: Uri?) {
                val document = pendingReport
                pendingReport = null
                if (uri == null) {
                    reportsViewModel.onPickerCancelled()
                    return
                }
                if (document == null) {
                    reportsViewModel.onSaveFailed("報告儲存狀態已失效，請重新匯出")
                    return
                }
                lifecycleScope.launch {
                    when (val result = container.safReportWriter.write(uri, document)) {
                        is AppResult.Success -> reportsViewModel.onSaveCompleted(document.fileName)
                        is AppResult.Failure -> reportsViewModel.onSaveFailed(result.error.userMessage)
                    }
                }
            }

            val jsonReportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json"),
                onResult = ::handleReportDestination,
            )
            val csvReportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/csv"),
                onResult = ::handleReportDestination,
            )
            val htmlReportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/html"),
                onResult = ::handleReportDestination,
            )
            val zipReportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/zip"),
                onResult = ::handleReportDestination,
            )
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { onboardingViewModel.retry() }

            val installationState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
            val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
            val modulesState by modulesViewModel.uiState.collectAsStateWithLifecycle()
            val reportsState by reportsViewModel.uiState.collectAsStateWithLifecycle()
            val terminalState by terminalViewModel.uiState.collectAsStateWithLifecycle()
            val diagnostics by container.bootstrapCoordinator.diagnostics.collectAsStateWithLifecycle()

            LaunchedEffect(reportsState.pendingDocument?.id) {
                val document = reportsState.pendingDocument ?: return@LaunchedEffect
                pendingReport = document
                reportsViewModel.consumePendingDocument(document.id)
                try {
                    when (document.format) {
                        ReportFormat.JSON -> jsonReportLauncher.launch(document.fileName)
                        ReportFormat.CSV -> csvReportLauncher.launch(document.fileName)
                        ReportFormat.HTML -> htmlReportLauncher.launch(document.fileName)
                        ReportFormat.ZIP -> zipReportLauncher.launch(document.fileName)
                    }
                } catch (exception: Exception) {
                    pendingReport = null
                    reportsViewModel.onSaveFailed(exception.message ?: "無法開啟系統檔案選擇器")
                }
            }

            MagoApp(
                installationState = installationState,
                dashboardState = dashboardState,
                inventoryState = inventoryState,
                modulesState = modulesState,
                reportsState = reportsState,
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
                onReportFormatSelected = reportsViewModel::selectFormat,
                onReportExport = reportsViewModel::requestExport,
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
