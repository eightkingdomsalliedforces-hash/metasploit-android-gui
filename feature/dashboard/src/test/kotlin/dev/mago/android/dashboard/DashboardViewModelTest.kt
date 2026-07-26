package dev.mago.android.dashboard

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.InstallationState
import dev.mago.android.installation.TermuxEnvironment
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.MetasploitVersion
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
        val viewModel = DashboardViewModel(FakeCoordinator(), repository)
        val collection = backgroundScope.launch(dispatcher) { viewModel.uiState.collect() }

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

    private class FakeCoordinator : BootstrapCoordinator {
        override val state: StateFlow<InstallationState> = MutableStateFlow(InstallationState())
        override val environment: StateFlow<TermuxEnvironment?> = MutableStateFlow(null)
        override val metasploitVersion: StateFlow<MetasploitVersion?> = MutableStateFlow(null)
        override val diagnostics: StateFlow<List<DiagnosticEntry>> = MutableStateFlow(emptyList())

        override suspend fun inspectEnvironment() = Unit
        override suspend fun retryCurrentStage() = Unit
        override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
