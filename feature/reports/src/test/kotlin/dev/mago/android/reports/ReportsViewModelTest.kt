package dev.mago.android.reports

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitModuleInfo
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun `initial preview load reads every bounded source once and stores one snapshot`() = runTest {
        val inventory = FakeInventoryRepository()
        val store = FakeModuleLocalStore()
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(inventory.hostCalls).isEqualTo(1)
        assertThat(inventory.serviceCalls).isEqualTo(1)
        assertThat(inventory.vulnerabilityCalls).isEqualTo(1)
        assertThat(inventory.lastLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
        assertThat(inventory.lastOffset).isEqualTo(0)
        assertThat(store.historyCalls).isEqualTo(1)
        assertThat(store.lastHistoryLimit).isEqualTo(ReportsViewModel.RECORD_LIMIT)
        assertThat(builder.calls).isEqualTo(0)
        assertThat(viewModel.uiState.value.previewSnapshot?.workspace?.name).isEqualTo("lab")
        assertThat(viewModel.uiState.value.previewSnapshot?.generatedAtEpochMillis)
            .isEqualTo(1_700_000_000_000)
        assertThat(viewModel.uiState.value.initialLoading).isFalse()
    }

    @Test
    fun `two initial requests while the first read is suspended produce one load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val inventory = FakeInventoryRepository(currentWorkspaceGate = gate)
        val viewModel = ReportsViewModel(inventory, FakeModuleLocalStore(), CapturingBuilder())

        viewModel.ensurePreviewLoaded()
        viewModel.ensurePreviewLoaded()

        assertThat(viewModel.uiState.value.initialLoading).isTrue()
        assertThat(inventory.currentCalls).isEqualTo(1)

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.previewSnapshot).isNotNull()
    }

    @Test
    fun `ensure preview loaded is a no-op after a snapshot exists`() = runTest {
        val inventory = FakeInventoryRepository()
        val store = FakeModuleLocalStore()
        val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()
        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(inventory.hostCalls).isEqualTo(1)
        assertThat(inventory.serviceCalls).isEqualTo(1)
        assertThat(inventory.vulnerabilityCalls).isEqualTo(1)
        assertThat(store.historyCalls).isEqualTo(1)
    }

    @Test
    fun `initial source failure leaves no snapshot and performs no retry`() = runTest {
        val inventory = FakeInventoryRepository(failServicesInitially = true)
        val store = FakeModuleLocalStore()
        val viewModel = ReportsViewModel(inventory, store, CapturingBuilder())

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(1)
        assertThat(inventory.hostCalls).isEqualTo(1)
        assertThat(inventory.serviceCalls).isEqualTo(1)
        assertThat(inventory.vulnerabilityCalls).isEqualTo(0)
        assertThat(store.historyCalls).isEqualTo(0)
        assertThat(viewModel.uiState.value.previewSnapshot).isNull()
        assertThat(viewModel.uiState.value.refreshErrorMessage).isEqualTo("services unavailable")
        assertThat(viewModel.uiState.value.initialLoading).isFalse()
    }

    @Test
    fun `refresh failure preserves the previous complete snapshot`() = runTest {
        val inventory = FakeInventoryRepository()
        val viewModel = ReportsViewModel(inventory, FakeModuleLocalStore(), CapturingBuilder())

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()
        val original = viewModel.uiState.value.previewSnapshot

        inventory.workspaceName = "new-lab"
        inventory.failServices = true
        viewModel.refreshPreview()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.previewSnapshot).isSameInstanceAs(original)
        assertThat(viewModel.uiState.value.refreshing).isFalse()
        assertThat(viewModel.uiState.value.refreshErrorMessage).isEqualTo("services unavailable")
    }

    @Test
    fun `export without a preview snapshot fails closed`() = runTest {
        val inventory = FakeInventoryRepository()
        val store = FakeModuleLocalStore()
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(inventory, store, builder)

        viewModel.requestExport()
        advanceUntilIdle()

        assertThat(inventory.currentCalls).isEqualTo(0)
        assertThat(store.historyCalls).isEqualTo(0)
        assertThat(builder.calls).isEqualTo(0)
        assertThat(viewModel.uiState.value.pendingDocument).isNull()
        assertThat(viewModel.uiState.value.exportErrorMessage).isEqualTo("請先載入報告預覽")
    }

    @Test
    fun `export uses the current snapshot without additional source reads`() = runTest {
        val inventory = FakeInventoryRepository()
        val store = FakeModuleLocalStore()
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(inventory, store, builder) { 1_700_000_000_000 }

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()
        val before = listOf(
            inventory.currentCalls,
            inventory.hostCalls,
            inventory.serviceCalls,
            inventory.vulnerabilityCalls,
            store.historyCalls,
        )

        viewModel.selectFormat(ReportFormat.HTML)
        viewModel.requestExport()
        advanceUntilIdle()

        val after = listOf(
            inventory.currentCalls,
            inventory.hostCalls,
            inventory.serviceCalls,
            inventory.vulnerabilityCalls,
            store.historyCalls,
        )
        assertThat(after).isEqualTo(before)
        assertThat(builder.calls).isEqualTo(1)
        assertThat(builder.format).isEqualTo(ReportFormat.HTML)
        assertThat(builder.snapshot?.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
        assertThat(viewModel.uiState.value.pendingDocument?.fileName).isEqualTo("report.html")
        assertThat(viewModel.uiState.value.exporting).isFalse()
    }

    @Test
    fun `two export requests before the builder runs create one document`() = runTest {
        val standardDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standardDispatcher)
        val builder = CapturingBuilder()
        val viewModel = ReportsViewModel(
            FakeInventoryRepository(),
            FakeModuleLocalStore(),
            builder,
        )

        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()

        viewModel.requestExport()
        viewModel.requestExport()

        assertThat(viewModel.uiState.value.exporting).isTrue()
        advanceUntilIdle()

        assertThat(builder.calls).isEqualTo(1)
        assertThat(viewModel.uiState.value.exporting).isFalse()
    }

    @Test
    fun `consuming document clears picker event`() = runTest {
        val viewModel = ReportsViewModel(
            FakeInventoryRepository(),
            FakeModuleLocalStore(),
            CapturingBuilder(),
        )
        viewModel.ensurePreviewLoaded()
        advanceUntilIdle()
        viewModel.requestExport()
        advanceUntilIdle()
        val document = viewModel.uiState.value.pendingDocument!!

        viewModel.consumePendingDocument(document.id)

        assertThat(viewModel.uiState.value.pendingDocument).isNull()
    }

    private class CapturingBuilder : ReportDocumentBuilder {
        var calls = 0
        var snapshot: ReportSnapshot? = null
        var format: ReportFormat? = null

        override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
            calls += 1
            this.snapshot = snapshot
            this.format = format
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
        failServicesInitially: Boolean = false,
        private val currentWorkspaceGate: CompletableDeferred<Unit>? = null,
    ) : MetasploitInventoryRepository {
        var workspaceName = "lab"
        var failServices = failServicesInitially
        var currentCalls = 0
        var hostCalls = 0
        var serviceCalls = 0
        var vulnerabilityCalls = 0
        var lastLimit = -1
        var lastOffset = -1

        override suspend fun workspaces() = AppResult.Success(listOf(workspace(workspaceName)))

        override suspend fun currentWorkspace(): AppResult<MetasploitWorkspaceSummary> {
            currentCalls += 1
            currentWorkspaceGate?.await()
            return AppResult.Success(workspace(workspaceName))
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

        private fun workspace(name: String) = MetasploitWorkspaceSummary(
            id = 1,
            name = name,
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

        override suspend fun cacheModules(
            type: MetasploitModuleType,
            modules: List<MetasploitModuleSummary>,
        ) = Unit

        override suspend fun cacheInfo(info: MetasploitModuleInfo) = Unit

        override suspend fun cachedModules(
            type: MetasploitModuleType,
        ): List<MetasploitModuleSummary> = emptyList()

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
