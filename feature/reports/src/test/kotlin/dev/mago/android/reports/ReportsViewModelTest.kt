package dev.mago.android.reports

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.reporting.ReportDocument
import dev.mago.android.reporting.ReportDocumentBuilder
import dev.mago.android.reporting.ReportFormat
import dev.mago.android.reporting.ReportSnapshot
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
class ReportsViewModelTest {
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
    fun `one export request loads each bounded source once and creates one document`() = runTest {
        val inventory = FakeInventoryRepository()
        val store = FakeModuleLocalStore()
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

        viewModel.requestExport()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(inventory.hostCalls).isEqualTo(1)
        assertThat(inventory.serviceCalls).isEqualTo(1)
        assertThat(inventory.vulnerabilityCalls).isEqualTo(1)
        assertThat(inventory.lastLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
        assertThat(inventory.lastOffset).isEqualTo(0)
        assertThat(store.historyCalls).isEqualTo(1)
        assertThat(store.lastHistoryLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
        assertThat(builder.calls).isEqualTo(1)
        assertThat(builder.snapshot?.workspace?.name).isEqualTo("lab")
        assertThat(viewModel.uiState.value.pendingDocument?.fileName).isEqualTo("report.json")
    }

    @Test
    fun `source failure produces no partial document and performs no retry`() = runTest {
        val inventory = FakeInventoryRepository(failServices = true)
        val store = FakeModuleLocalStore()
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(inventory, store, builder)

        viewModel.requestExport()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(inventory.hostCalls).isEqualTo(1)
        assertThat(inventory.serviceCalls).isEqualTo(1)
        assertThat(inventory.vulnerabilityCalls).isEqualTo(0)
        assertThat(store.historyCalls).isEqualTo(0)
        assertThat(builder.calls).isEqualTo(0)
        assertThat(viewModel.uiState.value.pendingDocument).isNull()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("services unavailable")
    }

    @Test
    fun `consuming document clears picker event`() = runTest {
        val viewModel = ReportsViewModel(FakeInventoryRepository(), FakeModuleLocalStore(), CapturingBuilder())
        viewModel.requestExport()
        advanceUntilIdle()
        val document = viewModel.uiState.value.pendingDocument!!

        viewModel.consumePendingDocument(document.id)

        assertThat(viewModel.uiState.value.pendingDocument).isNull()
    }

    private class CapturingBuilder : ReportDocumentBuilder {
        var calls = 0
        var snapshot: ReportSnapshot? = null

        override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
            calls += 1
            this.snapshot = snapshot
            return ReportDocument(
                id = "report-id",
                format = format,
                fileName = "report.${format.extension}",
                mimeType = format.mimeType,
                bytes = "report".encodeToByteArray(),
            )
        }
    }

    private class FakeInventoryRepository(
        private val failServices: Boolean = false,
    ) : MetasploitInventoryRepository {
        var currentCalls = 0
        var hostCalls = 0
        var serviceCalls = 0
        var vulnerabilityCalls = 0
        var lastLimit = -1
        var lastOffset = -1

        override suspend fun workspaces() = AppResult.Success(listOf(workspace()))

        override suspend fun currentWorkspace(): AppResult<MetasploitWorkspaceSummary> {
            currentCalls += 1
            return AppResult.Success(workspace())
        }

        override suspend fun addWorkspace(name: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun setWorkspace(name: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun hosts(
            workspace: String,
            limit: Int,
            offset: Int,
        ): AppResult<List<MetasploitHostRecord>> {
            hostCalls += 1
            lastLimit = limit
            lastOffset = offset
            return AppResult.Success(emptyList())
        }

        override suspend fun services(
            workspace: String,
            limit: Int,
            offset: Int,
        ): AppResult<List<MetasploitServiceRecord>> {
            serviceCalls += 1
            lastLimit = limit
            lastOffset = offset
            return if (failServices) {
                AppResult.Failure(AppError("SERVICES_UNAVAILABLE", "services unavailable"))
            } else {
                AppResult.Success(emptyList())
            }
        }

        override suspend fun vulnerabilities(
            workspace: String,
            limit: Int,
            offset: Int,
        ): AppResult<List<MetasploitVulnerabilityRecord>> {
            vulnerabilityCalls += 1
            lastLimit = limit
            lastOffset = offset
            return AppResult.Success(emptyList())
        }

        private fun workspace() = MetasploitWorkspaceSummary(
            id = 1,
            name = "lab",
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    }

    private class FakeModuleLocalStore : ModuleLocalStore {
        var historyCalls = 0
        var lastHistoryLimit = -1

        override suspend fun executionHistory(limit: Int): List<ModuleExecutionRecord> {
            historyCalls += 1
            lastHistoryLimit = limit
            return emptyList()
        }

        override suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>) = Unit
        override suspend fun cacheInfo(info: MetasploitModuleInfo) = Unit
        override suspend fun cachedModules(type: MetasploitModuleType): List<MetasploitModuleSummary> = emptyList()
        override suspend fun recordOpened(module: MetasploitModuleSummary) = Unit
        override suspend fun recent(limit: Int): List<MetasploitModuleSummary> = emptyList()
        override suspend fun favorites(): Set<String> = emptySet()
        override suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean) = Unit
        override suspend fun recordExecution(record: ModuleExecutionRecord) = Unit
        override suspend fun updateExecution(
            uuid: String,
            status: MetasploitModuleRunStatus,
            resultSummary: String?,
            error: String?,
            updatedAtEpochMillis: Long,
        ) = Unit
    }
}
