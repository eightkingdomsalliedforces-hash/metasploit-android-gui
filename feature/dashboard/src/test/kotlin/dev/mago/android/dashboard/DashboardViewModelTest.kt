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
import kotlinx.coroutines.CompletableDeferred
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
    fun `manual refresh failure preserves both old lists`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = DashboardViewModel(FakeCoordinator(), repository, FakeTermuxGateway())
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        repository.jobsResult = AppResult.Failure(
            AppError(errorCode = "JOBS_FAILED", userMessage = "jobs failed"),
        )
        repository.sessionsResult = AppResult.Success(emptyList())
        viewModel.refreshOperations()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.jobs)
            .containsExactlyElementsIn(FakeOperationsRepository.defaultJobs())
        assertThat(viewModel.uiState.value.sessions)
            .containsExactlyElementsIn(FakeOperationsRepository.defaultSessions())
        collection.cancel()
    }

    @Test
    fun `request and cancel perform zero stop calls`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = DashboardViewModel(
            FakeCoordinator(InstallationStage.READY),
            repository,
            FakeTermuxGateway(),
        )
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestStopJob("2")

        assertThat(viewModel.uiState.value.stopConfirmation)
            .isEqualTo(OperationStopTarget.Job("2", "Example Job"))
        assertThat(repository.stopJobCalls).isEmpty()

        viewModel.cancelStop()

        assertThat(viewModel.uiState.value.stopConfirmation).isNull()
        assertThat(repository.stopJobCalls).isEmpty()
        collection.cancel()
    }

    @Test
    fun `missing target fails before confirmation`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = DashboardViewModel(
            FakeCoordinator(InstallationStage.READY),
            repository,
            FakeTermuxGateway(),
        )
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.requestStopSession(99)

        assertThat(viewModel.uiState.value.stopConfirmation).isNull()
        assertThat(viewModel.uiState.value.stopError?.title).contains("已不在目前列表")
        assertThat(repository.stopSessionCalls).isEmpty()
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
        var jobsResult: AppResult<List<MetasploitJobSummary>> = AppResult.Success(defaultJobs())
        var sessionsResult: AppResult<List<MetasploitSessionSummary>> = AppResult.Success(defaultSessions())
        var jobInfoResult: AppResult<MetasploitJobInfo> = AppResult.Success(defaultJobInfo("2"))
        var stopJobResult: AppResult<Unit> = AppResult.Success(Unit)
        var stopSessionResult: AppResult<Unit> = AppResult.Success(Unit)
        var stopJobGate: CompletableDeferred<Unit>? = null

        var jobsCalls = 0
        var sessionsCalls = 0
        val jobInfoCalls = mutableListOf<String>()
        val stopJobCalls = mutableListOf<Pair<String, Boolean>>()
        val stopSessionCalls = mutableListOf<Pair<Int, Boolean>>()

        override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> =
            jobsResult.also { jobsCalls += 1 }

        override suspend fun sessions(): AppResult<List<MetasploitSessionSummary>> =
            sessionsResult.also { sessionsCalls += 1 }

        override suspend fun jobInfo(jobId: String): AppResult<MetasploitJobInfo> =
            jobInfoResult.also { jobInfoCalls += jobId }

        override suspend fun stopJob(
            jobId: String,
            userConfirmed: Boolean,
        ): AppResult<Unit> {
            stopJobCalls += jobId to userConfirmed
            stopJobGate?.await()
            return stopJobResult
        }

        override suspend fun stopSession(
            sessionId: Int,
            userConfirmed: Boolean,
        ): AppResult<Unit> {
            stopSessionCalls += sessionId to userConfirmed
            return stopSessionResult
        }

        companion object {
            fun defaultJobs() = listOf(MetasploitJobSummary("2", "Example Job"))

            fun defaultJobInfo(id: String) = MetasploitJobInfo(
                id = id,
                name = "Example Job",
                startTimeEpochSeconds = 100,
                uriPath = null,
                datastore = emptyMap(),
                extraFields = emptyMap(),
            )

            fun defaultSessions() = listOf(
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
                    viaExploit = "exploit/multi/handler",
                    viaPayload = null,
                    architecture = "x64",
                    platform = "windows",
                    tunnelLocal = null,
                    tunnelPeer = null,
                    routes = emptyList(),
                    extraFields = emptyMap(),
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
