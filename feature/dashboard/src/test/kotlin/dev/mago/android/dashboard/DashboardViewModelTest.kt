package dev.mago.android.dashboard

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.InstallationState
import dev.mago.android.installation.TermuxEnvironment
import dev.mago.android.installation.TermuxGateway
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load reads jobs and sessions once and job detail only on selection`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = DashboardViewModel(FakeCoordinator(), repository, FakeTermuxGateway())
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        advanceUntilIdle()

        assertThat(repository.jobsCalls).isEqualTo(1)
        assertThat(repository.sessionsCalls).isEqualTo(1)
        assertThat(repository.jobInfoCalls).isEmpty()
        assertThat(viewModel.uiState.value.jobs.single().id).isEqualTo("2")
        assertThat(viewModel.uiState.value.sessions.single().id).isEqualTo(7)

        viewModel.selectJob("2")
        advanceUntilIdle()

        assertThat(repository.jobInfoCalls).containsExactly("2")
        assertThat(viewModel.uiState.value.selectedJob?.name).isEqualTo("Example Job")
        collection.cancel()
    }

    @Test
    fun `maintenance request performs zero bridge calls until explicit confirmation`() = runTest {
        val gateway = FakeTermuxGateway()
        val viewModel = DashboardViewModel(
            FakeCoordinator(InstallationStage.READY),
            FakeOperationsRepository(),
            gateway,
        )
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestMaintenance(MaintenanceAction.UPDATE_METASPLOIT)
        advanceUntilIdle()

        assertThat(gateway.actions).isEmpty()
        assertThat(viewModel.uiState.value.maintenanceConfirmation)
            .isEqualTo(MaintenanceAction.UPDATE_METASPLOIT)

        viewModel.confirmMaintenance()
        advanceUntilIdle()

        assertThat(gateway.actions).containsExactly(
            BridgeAction.UPDATE_METASPLOIT,
            BridgeAction.HEALTH_CHECK,
        ).inOrder()
        assertThat(viewModel.uiState.value.maintenanceMessage).contains("健康檢查通過")
        collection.cancel()
    }

    @Test
    fun `maintenance fails closed before ready and performs zero bridge calls`() = runTest {
        val gateway = FakeTermuxGateway()
        val viewModel = DashboardViewModel(
            FakeCoordinator(InstallationStage.CHECKING_DEVICE),
            FakeOperationsRepository(),
            gateway,
        )
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestMaintenance(MaintenanceAction.CLEAN_CACHE)
        viewModel.confirmMaintenance()
        advanceUntilIdle()

        assertThat(gateway.actions).isEmpty()
        assertThat(viewModel.uiState.value.maintenanceError).contains("尚未就緒")
        collection.cancel()
    }

    @Test
    fun `failed maintenance action does not run health check`() = runTest {
        val gateway = FakeTermuxGateway(failAction = BridgeAction.UPDATE_METASPLOIT)
        val viewModel = DashboardViewModel(
            FakeCoordinator(InstallationStage.READY),
            FakeOperationsRepository(),
            gateway,
        )
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestMaintenance(MaintenanceAction.UPDATE_METASPLOIT)
        viewModel.confirmMaintenance()
        advanceUntilIdle()

        assertThat(gateway.actions).containsExactly(BridgeAction.UPDATE_METASPLOIT)
        assertThat(viewModel.uiState.value.maintenanceError).isEqualTo("maintenance failed")
        collection.cancel()
    }

    private class FakeOperationsRepository : MetasploitOperationsRepository {
        var jobsCalls = 0
        var sessionsCalls = 0
        val jobInfoCalls = mutableListOf<String>()

        override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> {
            jobsCalls += 1
            return AppResult.Success(listOf(MetasploitJobSummary("2", "Example Job")))
        }

        override suspend fun jobInfo(jobId: String): AppResult<MetasploitJobInfo> {
            jobInfoCalls += jobId
            return AppResult.Success(
                MetasploitJobInfo(
                    id = jobId,
                    name = "Example Job",
                    startTimeEpochSeconds = 100,
                    uriPath = null,
                    datastore = emptyMap(),
                    extraFields = emptyMap(),
                ),
            )
        }

        override suspend fun sessions(): AppResult<List<MetasploitSessionSummary>> {
            sessionsCalls += 1
            return AppResult.Success(
                listOf(
                    MetasploitSessionSummary(
                        id = 7,
                        type = "meterpreter",
                        description = "Meterpreter",
                        info = "Authorized lab",
                        workspace = "default",
                        sessionHost = "192.0.2.10",
                        sessionPort = 445,
                        targetHost = null,
                        username = null,
                        uuid = null,
                        exploitUuid = null,
                        viaExploit = null,
                        viaPayload = null,
                        architecture = "x64",
                        platform = "windows",
                        tunnelLocal = null,
                        tunnelPeer = null,
                        routes = emptyList(),
                        extraFields = emptyMap(),
                    ),
                ),
            )
        }
    }

    private class FakeTermuxGateway(
        private val failAction: BridgeAction? = null,
    ) : TermuxGateway {
        val actions = mutableListOf<BridgeAction>()

        override suspend fun inspect(): AppResult<TermuxEnvironment> = AppResult.Success(
            TermuxEnvironment(installed = true, runCommandPermissionGranted = true),
        )

        override suspend fun deployBridge(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun execute(
            action: BridgeAction,
            operationId: String,
        ): AppResult<BridgeResponse> {
            actions += action
            if (action == failAction) {
                return AppResult.Failure(
                    AppError(
                        errorCode = "MAINTENANCE_FAILED",
                        userMessage = "maintenance failed",
                    ),
                )
            }
            return AppResult.Success(
                BridgeResponse(
                    schemaVersion = 1,
                    operationId = operationId,
                    action = action,
                    success = true,
                    exitCode = 0,
                    message = "ok",
                    progress = 100,
                    data = if (action == BridgeAction.HEALTH_CHECK) {
                        mapOf("rpcHost" to "127.0.0.1", "rpcPort" to "55552")
                    } else {
                        emptyMap()
                    },
                ),
            )
        }

        override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeCoordinator(
        stage: InstallationStage = InstallationStage.CHECKING_DEVICE,
    ) : BootstrapCoordinator {
        override val state: StateFlow<InstallationState> = MutableStateFlow(
            InstallationState(stage = stage),
        )
        override val environment: StateFlow<TermuxEnvironment?> = MutableStateFlow(null)
        override val metasploitVersion: StateFlow<MetasploitVersion?> = MutableStateFlow(null)
        override val diagnostics: StateFlow<List<DiagnosticEntry>> = MutableStateFlow(emptyList())

        override suspend fun inspectEnvironment() = Unit
        override suspend fun retryCurrentStage() = Unit
        override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
