package dev.mago.android.operations

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitJobRepository
import dev.mago.android.metasploit.MetasploitSessionRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionRead
import dev.mago.android.model.MetasploitSessionSummary
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
    fun `stop is sent once only after explicit confirmation`() = runTest {
        val jobs = FakeJobRepository()
        val sessions = FakeSessionRepository()
        val viewModel = OperationsViewModel(jobs, sessions)
        val job = MetasploitJobSummary("3", "Example")

        viewModel.requestStopJob(job)

        assertThat(jobs.stopRequests).isEmpty()
        assertThat(viewModel.uiState.value.stopConfirmation).isNotNull()

        viewModel.confirmStop()

        assertThat(jobs.stopRequests).containsExactly("3" to true)
        assertThat(viewModel.uiState.value.stopConfirmation).isNull()
    }

    @Test
    fun `Session interaction requires authorization acknowledgement`() = runTest {
        val sessions = FakeSessionRepository()
        val viewModel = OperationsViewModel(FakeJobRepository(), sessions)
        val session = session()

        viewModel.requestInteraction(session)
        viewModel.openInteraction()

        assertThat(viewModel.uiState.value.interaction).isNull()
        assertThat(viewModel.uiState.value.interactionCandidate).isEqualTo(session)

        viewModel.setInteractionAuthorizationConfirmed(true)
        viewModel.openInteraction()

        assertThat(viewModel.uiState.value.interaction?.session).isEqualTo(session)
    }

    @Test
    fun `send does not read automatically and closing clears in-memory content`() = runTest {
        val sessions = FakeSessionRepository()
        val viewModel = OperationsViewModel(FakeJobRepository(), sessions)
        viewModel.requestInteraction(session())
        viewModel.setInteractionAuthorizationConfirmed(true)
        viewModel.openInteraction()
        viewModel.setSessionInput("sysinfo")

        viewModel.sendSessionInput()

        assertThat(sessions.writeRequests).containsExactly(Triple(7, "sysinfo", true))
        assertThat(sessions.readRequests).isEmpty()
        assertThat(viewModel.uiState.value.interaction?.input).isEmpty()

        viewModel.readSessionOutput()

        assertThat(sessions.readRequests).containsExactly(7)
        assertThat(viewModel.uiState.value.interaction?.output).isEqualTo("authorized output")

        viewModel.closeInteraction()

        assertThat(viewModel.uiState.value.interaction).isNull()
        assertThat(viewModel.uiState.value.interactionAuthorizationConfirmed).isFalse()
    }

    private fun session() = MetasploitSessionSummary(
        id = 7,
        type = "shell",
        tunnelLocal = "127.0.0.1:4444",
        tunnelPeer = "192.0.2.10:50000",
        viaExploit = "exploit/example",
        viaPayload = "payload/example",
        description = "Shell",
        info = "Authorized lab",
        workspace = "default",
        sessionHost = "192.0.2.10",
        sessionPort = 445,
        targetHost = "192.0.2.10",
        username = "authorized-user",
        uuid = "session-uuid",
        exploitUuid = "exploit-uuid",
        routes = "",
        architecture = "x64",
        platform = "linux",
        extraFields = emptyMap(),
    )

    private class FakeJobRepository : MetasploitJobRepository {
        val stopRequests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun list() = AppResult.Success(emptyList<MetasploitJobSummary>())
        override suspend fun info(id: String) = AppResult.Success(
            MetasploitJobInfo(id, "Job", null, null, emptyMap(), emptyMap()),
        )

        override suspend fun stop(id: String, userConfirmed: Boolean): AppResult<Unit> {
            stopRequests += id to userConfirmed
            return AppResult.Success(Unit)
        }
    }

    private class FakeSessionRepository : MetasploitSessionRepository {
        val stopRequests = mutableListOf<Pair<Int, Boolean>>()
        val readRequests = mutableListOf<Int>()
        val writeRequests = mutableListOf<Triple<Int, String, Boolean>>()

        override suspend fun list() = AppResult.Success(emptyList<MetasploitSessionSummary>())

        override suspend fun stop(id: Int, userConfirmed: Boolean): AppResult<Unit> {
            stopRequests += id to userConfirmed
            return AppResult.Success(Unit)
        }

        override suspend fun read(id: Int): AppResult<MetasploitSessionRead> {
            readRequests += id
            return AppResult.Success(MetasploitSessionRead("authorized output"))
        }

        override suspend fun write(id: Int, input: String, userConfirmed: Boolean): AppResult<Unit> {
            writeRequests += Triple(id, input, userConfirmed)
            return AppResult.Success(Unit)
        }
    }
}
