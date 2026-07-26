package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleOption
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
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
class ModulesTargetPayloadViewModelTest {
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
    fun `changing TARGET reloads target compatible payloads`() = runTest {
        val repository = TargetRepository()
        val viewModel = ModulesViewModel(repository)
        viewModel.selectModule(MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/example"))

        viewModel.setOption("TARGET", "2")

        assertThat(repository.targetRequests).containsExactly(2)
        assertThat(viewModel.uiState.value.compatiblePayloads).containsExactly("windows/x64/target-2")
    }

    private class TargetRepository : MetasploitModuleRepository {
        val targetRequests = mutableListOf<Int>()

        override suspend fun list(type: MetasploitModuleType) =
            AppResult.Success(emptyList<MetasploitModuleSummary>())

        override suspend fun info(type: MetasploitModuleType, name: String) = AppResult.Success(
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
                stance = "aggressive",
                references = emptyList(),
                options = listOf(
                    MetasploitModuleOption(
                        name = "TARGET",
                        type = "int",
                        required = false,
                        advanced = false,
                        description = "Target index",
                        defaultValue = "0",
                        enums = emptyList(),
                    ),
                ),
                extraFields = emptyMap(),
            ),
        )

        override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String) =
            AppResult.Success(listOf("windows/x64/default"))

        override suspend fun compatiblePayloads(
            type: MetasploitModuleType,
            name: String,
            target: Int,
        ): AppResult<List<String>> {
            targetRequests += target
            return AppResult.Success(listOf("windows/x64/target-$target"))
        }

        override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")

        override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")

        override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> =
            error("not used")
    }
}
