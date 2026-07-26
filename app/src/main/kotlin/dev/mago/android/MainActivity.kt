package dev.mago.android

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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

class MainActivity : FragmentActivity() {
    private val container: AppContainer
        get() = (application as MagoApplication).container

    private val appLockViewModel by viewModels<AppLockViewModel> {
        AppLockViewModel.factory(container.appLockSettingsStore)
    }
    private val onboardingViewModel by viewModels<OnboardingViewModel> {
        OnboardingViewModel.factory(container.bootstrapCoordinator)
    }
    private val dashboardViewModel by viewModels<DashboardViewModel> {
        DashboardViewModel.factory(
            coordinator = container.bootstrapCoordinator,
            operationsRepository = container.metasploitOperationsRepository,
            termuxGateway = container.termuxGateway,
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

    private lateinit var biometricPrompt: BiometricPrompt
    private var pendingReport: ReportDocument? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        configureBiometricPrompt()

        setContent {
            val appLockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
            when {
                !appLockState.initialized -> AppLockScreen(
                    initializing = true,
                    authenticationInProgress = false,
                    errorMessage = appLockState.errorMessage,
                    onUnlock = {},
                )
                appLockState.locked -> AppLockScreen(
                    initializing = false,
                    authenticationInProgress = appLockState.pendingAuthPurpose != null,
                    errorMessage = appLockState.errorMessage,
                    onUnlock = { requestAuthentication(AppLockAuthPurpose.UNLOCK) },
                )
                else -> UnlockedMagoContent(appLockState)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLockViewModel.onBackgrounded()
    }

    private fun configureBiometricPrompt() {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appLockViewModel.onAuthenticationSucceeded()
                }

                override fun onAuthenticationFailed() {
                    appLockViewModel.onAuthenticationAttemptFailed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    appLockViewModel.onAuthenticationError(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            -> "系統驗證已取消。"
                            else -> errString.toString().ifBlank { "無法完成系統驗證" }
                        },
                    )
                }
            },
        )
    }

    private fun requestAuthentication(purpose: AppLockAuthPurpose) {
        val availability = BiometricManager.from(this).canAuthenticate(AUTHENTICATORS)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            val message = when (availability) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "請先在 Android 設定中建立生物辨識或裝置鎖定。"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "此裝置沒有可用的系統驗證硬體。"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "系統驗證目前不可用，請稍後再試。"
                else -> "此裝置目前無法使用生物辨識或裝置憑證。"
            }
            appLockViewModel.onAuthenticationError(message)
            return
        }

        appLockViewModel.beginAuthentication(purpose)
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(
                    when (purpose) {
                        AppLockAuthPurpose.UNLOCK -> "解鎖 MAGO"
                        AppLockAuthPurpose.ENABLE -> "啟用 MAGO App 鎖"
                        AppLockAuthPurpose.DISABLE -> "停用 MAGO App 鎖"
                    },
                )
                .setSubtitle("使用生物辨識或裝置 PIN／圖形驗證")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    @Composable
    private fun UnlockedMagoContent(appLockState: AppLockUiState) {
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
            dashboardState = dashboardState.copy(
                appLockEnabled = appLockState.enabled,
                appLockSettingBusy = appLockState.saving || appLockState.pendingAuthPurpose != null,
                appLockError = appLockState.errorMessage,
                onRequestAppLockChange = { enabled ->
                    requestAuthentication(
                        if (enabled) AppLockAuthPurpose.ENABLE else AppLockAuthPurpose.DISABLE,
                    )
                },
            ),
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

    private companion object {
        val AUTHENTICATORS: Int =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
