package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleOption
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
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
class ModulesPersistenceTest {
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
    fun `remote list failure uses cached catalog and marks offline`() = runTest {
        val cached = listOf(MetasploitModuleSummary(MetasploitModuleType.AUXILIARY, "scanner/example"))
        val store = FakeLocalStore(cachedModules = cached)
        val repository = FakeRepository(listFailure = true)
        val viewModel = ModulesViewModel(repository, store)

        viewModel.selectType(MetasploitModuleType.AUXILIARY)

        assertThat(viewModel.uiState.value.modules).containsExactlyElementsIn(cached)
        assertThat(viewModel.uiState.value.offlineCatalog).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `confirmed execution stores redacted READY before RPC then RUNNING`() = runTest {
        val store = FakeLocalStore()
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository, store)
        val summary = MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/example")
        viewModel.selectModule(summary)
        viewModel.setOption("RHOSTS", "192.0.2.10")
        viewModel.setOption("PASSWORD", "plain-secret")

        viewModel.requestExecute()
        viewModel.setAuthorizationConfirmed(true)
        viewModel.confirmRun()

        assertThat(store.executions.map { it.status })
            .containsExactly(MetasploitModuleRunStatus.READY, MetasploitModuleRunStatus.RUNNING)
            .inOrder()
        store.executions.forEach { record ->
            assertThat(record.redactedOptions["PASSWORD"]).isEqualTo("••••••••")
            assertThat(record.redactedOptions.values).doesNotContain("plain-secret")
        }
        assertThat(store.executions.last().uuid).isEqualTo("run-uuid")
        assertThat(repository.executeRequests).hasSize(1)
    }

    @Test
    fun `audit write failure blocks repository execution`() = runTest {
        val store = FakeLocalStore(failRecordExecution = true)
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository, store)
        val summary = MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/example")
        viewModel.selectModule(summary)
        viewModel.setOption("RHOSTS", "192.0.2.10")

        viewModel.requestExecute()
        viewModel.setAuthorizationConfirmed(true)
        viewModel.confirmRun()

        assertThat(repository.executeRequests).isEmpty()
        assertThat(store.executions).isEmpty()
        assertThat(viewModel.uiState.value.runErrorMessage).contains("稽核")
    }

    private class FakeRepository(
        private val listFailure: Boolean = false,
    ) : MetasploitModuleRepository {
        val executeRequests = mutableListOf<MetasploitModuleRequest>()

        override suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>> =
            if (listFailure) {
                AppResult.Failure(AppError("OFFLINE", "RPC 離線", retryable = true))
            } else {
                AppResult.Success(emptyList())
            }

        override suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo> =
            AppResult.Success(
                MetasploitModuleInfo(
                    type = type,
                    name = name,
                    displayName = "Example",
                    description = "Example",
                    rank = "normal",
                    platforms = emptyList(),
                    architectures = emptyList(),
                    authors = emptyList(),
                    privileged = false,
                    hasCheck = true,
                    stance = "passive",
                    references = emptyList(),
                    options = listOf(
                        MetasploitModuleOption(
                            name = "RHOSTS",
                            type = "address_range",
                            required = true,
                            advanced = false,
                            description = "Authorized target",
                            defaultValue = null,
                            enums = emptyList(),
                        ),
                        MetasploitModuleOption(
                            name = "PASSWORD",
                            type = "string",
                            required = false,
                            advanced = false,
                            description = "Secret",
                            defaultValue = null,
                            enums = emptyList(),
                        ),
                    ),
                    extraFields = emptyMap(),
                ),
            )

        override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String) =
            AppResult.Success(emptyList<String>())

        override suspend fun check(request: MetasploitModuleRequest) =
            AppResult.Success(MetasploitModuleLaunch(jobId = 7, uuid = "run-uuid"))

        override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> {
            executeRequests += request
            return AppResult.Success(MetasploitModuleLaunch(jobId = 8, uuid = "run-uuid"))
        }

        override suspend fun result(uuid: String) =
            AppResult.Success(MetasploitModuleRunResult(status = MetasploitModuleRunStatus.COMPLETED))
    }

    private class FakeLocalStore(
        private val cachedModules: List<MetasploitModuleSummary> = emptyList(),
        private val failRecordExecution: Boolean = false,
    ) : ModuleLocalStore {
        val executions = mutableListOf<ModuleExecutionRecord>()

        override suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>) = Unit
        override suspend fun cacheInfo(info: MetasploitModuleInfo) = Unit
        override suspend fun cachedModules(type: MetasploitModuleType) = cachedModules
        override suspend fun recordOpened(module: MetasploitModuleSummary) = Unit
        override suspend fun recent(limit: Int) = emptyList<MetasploitModuleSummary>()
        override suspend fun favorites() = emptySet<String>()
        override suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean) = Unit
        override suspend fun recordExecution(record: ModuleExecutionRecord) {
            if (failRecordExecution) error("database unavailable")
            executions += record
        }
        override suspend fun updateExecution(
            uuid: String,
            status: MetasploitModuleRunStatus,
            resultSummary: String?,
            error: String?,
            updatedAtEpochMillis: Long,
        ) = Unit
        override suspend fun executionHistory(limit: Int) = executions.toList()
    }
}
