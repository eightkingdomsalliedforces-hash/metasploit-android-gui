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
    fun `initial load selects active workspace and loads hosts once`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedWorkspace).isEqualTo("default")
        assertThat(viewModel.uiState.value.activeWorkspace?.name).isEqualTo("default")
        assertThat(repository.workspaceCalls).isEqualTo(1)
        assertThat(repository.currentWorkspaceCalls).isEqualTo(1)
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

    @Test
    fun `invalid or duplicate workspace never calls mutation RPC`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()

        viewModel.showCreateWorkspace()
        viewModel.setWorkspaceDraft("bad name")
        viewModel.submitCreateWorkspace()
        advanceUntilIdle()
        assertThat(repository.addCalls).isEmpty()
        assertThat(viewModel.uiState.value.workspaceValidationError).isNotNull()

        viewModel.setWorkspaceDraft("LAB")
        viewModel.submitCreateWorkspace()
        advanceUntilIdle()
        assertThat(repository.addCalls).isEmpty()
        assertThat(viewModel.uiState.value.workspaceValidationError).isEqualTo("Workspace 已存在")
    }

    @Test
    fun `create selects new workspace without changing active workspace`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()

        viewModel.showCreateWorkspace()
        viewModel.setWorkspaceDraft("new_lab")
        viewModel.submitCreateWorkspace()
        advanceUntilIdle()

        assertThat(repository.addCalls).containsExactly("new_lab")
        assertThat(repository.setCalls).isEmpty()
        assertThat(viewModel.uiState.value.selectedWorkspace).isEqualTo("new_lab")
        assertThat(viewModel.uiState.value.activeWorkspace?.name).isEqualTo("default")
        assertThat(viewModel.uiState.value.createWorkspaceDialogVisible).isFalse()
    }

    @Test
    fun `set active workspace performs one explicit mutation and verifies state`() = runTest {
        val repository = FakeRepository()
        val viewModel = InventoryViewModel(repository)
        advanceUntilIdle()
        viewModel.selectWorkspace("lab")
        advanceUntilIdle()

        viewModel.setSelectedWorkspaceActive()
        advanceUntilIdle()

        assertThat(repository.setCalls).containsExactly("lab")
        assertThat(viewModel.uiState.value.activeWorkspace?.name).isEqualTo("lab")
    }

    private class FakeRepository : MetasploitInventoryRepository {
        var workspaceCalls = 0
        var currentWorkspaceCalls = 0
        val hostCalls = mutableListOf<String>()
        val serviceCalls = mutableListOf<String>()
        val addCalls = mutableListOf<String>()
        val setCalls = mutableListOf<String>()
        var lastLimit = -1
        var lastOffset = -1
        private var activeName = "default"
        private val workspaceValues = mutableListOf(
            workspace("lab", 2),
            workspace("default", 1),
        )

        override suspend fun workspaces(): AppResult<List<MetasploitWorkspaceSummary>> {
            workspaceCalls += 1
            return AppResult.Success(workspaceValues.toList())
        }

        override suspend fun currentWorkspace(): AppResult<MetasploitWorkspaceSummary> {
            currentWorkspaceCalls += 1
            return AppResult.Success(workspaceValues.first { it.name == activeName })
        }

        override suspend fun addWorkspace(name: String): AppResult<Unit> {
            addCalls += name
            workspaceValues += workspace(name, workspaceValues.size.toLong() + 1)
            return AppResult.Success(Unit)
        }

        override suspend fun setWorkspace(name: String): AppResult<Unit> {
            setCalls += name
            activeName = name
            return AppResult.Success(Unit)
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
