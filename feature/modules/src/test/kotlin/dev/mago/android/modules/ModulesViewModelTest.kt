package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleOption
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue
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
class ModulesViewModelTest {
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
    fun `invalid required option blocks confirmation and repository execution`() = runTest {
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository)
        viewModel.selectModule(summary())

        viewModel.requestExecute()

        assertThat(viewModel.uiState.value.validationErrors).containsKey("RHOSTS")
        assertThat(viewModel.uiState.value.confirmation).isNull()
        assertThat(repository.executeRequests).isEmpty()
    }

    @Test
    fun `execute requires authorization acknowledgement before sending exactly one request`() = runTest {
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository)
        viewModel.selectModule(summary())
        viewModel.setOption("RHOSTS", "192.0.2.10")

        viewModel.requestExecute()

        assertThat(viewModel.uiState.value.confirmation).isNotNull()
        assertThat(repository.executeRequests).isEmpty()

        viewModel.confirmRun()

        assertThat(repository.executeRequests).isEmpty()
        assertThat(viewModel.uiState.value.confirmation).isNotNull()

        viewModel.setAuthorizationConfirmed(true)
        viewModel.confirmRun()

        assertThat(repository.executeRequests).hasSize(1)
        assertThat(repository.executeRequests.single().options["RHOSTS"]).isEqualTo("192.0.2.10")
        assertThat(repository.executeRequests.single().userConfirmed).isTrue()
        assertThat(viewModel.uiState.value.launch?.uuid).isEqualTo("run-uuid")
    }

    @Test
    fun `payload generation is not exposed as background module execution`() = runTest {
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository)

        viewModel.selectModule(summary(MetasploitModuleType.PAYLOAD))

        assertThat(viewModel.uiState.value.canExecute).isFalse()
        viewModel.requestExecute()
        assertThat(viewModel.uiState.value.confirmation).isNull()
        assertThat(repository.executeRequests).isEmpty()
    }

    @Test
    fun `result is fetched only after manual refresh`() = runTest {
        val repository = FakeRepository()
        val viewModel = ModulesViewModel(repository)
        viewModel.selectModule(summary())
        viewModel.setOption("RHOSTS", "192.0.2.10")
        viewModel.requestCheck()
        viewModel.setAuthorizationConfirmed(true)
        viewModel.confirmRun()

        assertThat(repository.resultRequests).isEmpty()
        assertThat(viewModel.uiState.value.runResult).isNull()

        viewModel.refreshResult()

        assertThat(repository.resultRequests).containsExactly("run-uuid")
        assertThat(viewModel.uiState.value.runResult?.status).isEqualTo(MetasploitModuleRunStatus.COMPLETED)
    }

    private fun summary(type: MetasploitModuleType = MetasploitModuleType.EXPLOIT) =
        MetasploitModuleSummary(type, "windows/example")

    private class FakeRepository : MetasploitModuleRepository {
        val executeRequests = mutableListOf<MetasploitModuleRequest>()
        val checkRequests = mutableListOf<MetasploitModuleRequest>()
        val resultRequests = mutableListOf<String>()

        override suspend fun list(type: MetasploitModuleType) = AppResult.Success(emptyList<MetasploitModuleSummary>())

        override suspend fun info(type: MetasploitModuleType, name: String) = AppResult.Success(
            MetasploitModuleInfo(
                type = type,
                name = name,
                displayName = "Example",
                description = "Example module",
                rank = "normal",
                platforms = emptyList(),
                architectures = emptyList(),
                authors = emptyList(),
                privileged = false,
                hasCheck = type in setOf(MetasploitModuleType.EXPLOIT, MetasploitModuleType.AUXILIARY),
                stance = "passive",
                references = emptyList(),
                options = if (type == MetasploitModuleType.PAYLOAD) {
                    emptyList()
                } else {
                    listOf(
                        MetasploitModuleOption(
                            name = "RHOSTS",
                            type = "address_range",
                            required = true,
                            advanced = false,
                            description = "Authorized target",
                            defaultValue = null,
                            enums = emptyList(),
                        ),
                    )
                },
                extraFields = emptyMap(),
            ),
        )

        override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String) =
            AppResult.Success(listOf("windows/x64/example"))

        override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> {
            checkRequests += request
            return AppResult.Success(MetasploitModuleLaunch(jobId = 7, uuid = "run-uuid"))
        }

        override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> {
            executeRequests += request
            return AppResult.Success(MetasploitModuleLaunch(jobId = 8, uuid = "run-uuid"))
        }

        override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> {
            resultRequests += uuid
            return AppResult.Success(
                MetasploitModuleRunResult(
                    status = MetasploitModuleRunStatus.COMPLETED,
                    result = RpcValue.StringValue("completed"),
                ),
            )
        }
    }
}
