package dev.mago.android.inventory

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
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
    fun `initial load selects default workspace and loads hosts once`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedWorkspace).isEqualTo("default")
        assertThat(repository.workspaceCalls).isEqualTo(1)
        assertThat(repository.hostCalls).containsExactly("default")
        assertThat(repository.serviceCalls).isEmpty()
    }

    @Test
    fun `tab and workspace selections issue only explicit bounded reads`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()

        viewModel.selectTab(InventoryTab.SERVICES)
        advanceUntilIdle()
        viewModel.selectWorkspace("lab")
        advanceUntilIdle()

        assertThat(repository.serviceCalls).containsExactly("default", "lab").inOrder()
        assertThat(repository.lastLimit).isEqualTo(100)
        assertThat(repository.lastOffset).isEqualTo(0)
    }

    private class FakeRepository : MetasploitInventoryRepository {
        var workspaceCalls = 0
        val hostCalls = mutableListOf<String>()
        val serviceCalls = mutableListOf<String>()
        var lastLimit = -1
        var lastOffset = -1

        override suspend fun workspaces(): AppResult<List<MetasploitWorkspaceSummary>> {
            workspaceCalls += 1
            return AppResult.Success(
                listOf(
                    workspace("lab", 2),
                    workspace("default", 1),
                ),
            )
        }

        override suspend fun hosts(workspace: String, limit: Int, offset: Int): AppResult<List<MetasploitHostRecord>> {
            hostCalls += workspace
            lastLimit = limit
            lastOffset = offset
            return AppResult.Success(emptyList())
        }

        override suspend fun services(
            workspace: String,
            limit: Int,
            offset: Int,
        ): AppResult<List<MetasploitServiceRecord>> {
            serviceCalls += workspace
            lastLimit = limit
            lastOffset = offset
            return AppResult.Success(emptyList())
        }

        override suspend fun vulnerabilities(
            workspace: String,
            limit: Int,
            offset: Int,
        ): AppResult<List<MetasploitVulnerabilityRecord>> = AppResult.Success(emptyList())

        private fun workspace(name: String, id: Long) = MetasploitWorkspaceSummary(
            id = id,
            name = name,
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    }
}
