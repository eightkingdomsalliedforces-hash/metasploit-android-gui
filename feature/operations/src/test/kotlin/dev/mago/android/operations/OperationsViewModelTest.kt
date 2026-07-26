package dev.mago.android.operations

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationsViewModelTest {
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
    fun `initial refresh loads jobs and sessions once`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = OperationsViewModel(repository, clock = { 1234 })

        val state = viewModel.uiState.value
        assertThat(state.jobs.map { it.id }).containsExactly(2)
        assertThat(state.sessions.map { it.id }).containsExactly(7)
        assertThat(state.refreshedAtEpochMillis).isEqualTo(1234)
        assertThat(repository.jobListCalls).isEqualTo(1)
        assertThat(repository.sessionListCalls).isEqualTo(1)
    }

    @Test
    fun `selecting a job loads only that job detail`() = runTest {
        val repository = FakeOperationsRepository()
        val viewModel = OperationsViewModel(repository)

        viewModel.selectJob(MetasploitJobSummary(2, "Job Two"))

        assertThat(viewModel.uiState.value.selectedJob?.id).isEqualTo(2)
        assertThat(repository.requestedJobIds).containsExactly(2)
    }
}

private class FakeOperationsRepository : MetasploitOperationsRepository {
    var jobListCalls = 0
    var sessionListCalls = 0
    val requestedJobIds = mutableListOf<Int>()

    override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> {
        jobListCalls += 1
        return AppResult.Success(listOf(MetasploitJobSummary(2, "Job Two")))
    }

    override suspend fun jobInfo(jobId: Int): AppResult<MetasploitJobInfo> {
        requestedJobIds += jobId
        return AppResult.Success(
            MetasploitJobInfo(
                id = jobId,
                name = "Job Two",
                startTimeEpochSeconds = 100,
                uriPath = null,
                datastore = emptyMap(),
                extraFields = emptyMap(),
            ),
        )
    }

    override suspend fun sessions(): AppResult<List<MetasploitSessionInfo>> {
        sessionListCalls += 1
        return AppResult.Success(
            listOf(
                MetasploitSessionInfo(
                    id = 7,
                    type = "meterpreter",
                    tunnelLocal = "",
                    tunnelPeer = "",
                    viaExploit = "",
                    viaPayload = "",
                    description = "Session",
                    info = "Authorized lab",
                    workspace = "Lab",
                    sessionHost = "192.0.2.10",
                    sessionPort = 445,
                    targetHost = "192.0.2.10",
                    username = "lab-user",
                    uuid = "session-uuid",
                    exploitUuid = "exploit-uuid",
                    routes = emptyList(),
                    architecture = "x64",
                    platform = "windows",
                    extraFields = emptyMap(),
                ),
            ),
        )
    }
}
