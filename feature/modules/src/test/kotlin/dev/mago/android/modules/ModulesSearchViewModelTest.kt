package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModulesSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search is debounced and qualified with the selected type`() = runTest(dispatcher) {
        val repository = SearchRepository()
        val viewModel = ModulesViewModel(repository)

        viewModel.setQuery("smb")
        advanceTimeBy(249)
        runCurrent()
        assertThat(repository.searchQueries).isEmpty()

        advanceTimeBy(1)
        runCurrent()

        assertThat(repository.searchQueries).containsExactly("smb type:exploit")
        assertThat(viewModel.uiState.value.visibleModules.single().name).isEqualTo("windows/smb/example")
    }

    @Test
    fun `changing type cancels the previous debounce and searches only the new type`() = runTest(dispatcher) {
        val repository = SearchRepository()
        val viewModel = ModulesViewModel(repository)

        viewModel.setQuery("service")
        advanceTimeBy(100)
        viewModel.selectType(MetasploitModuleType.AUXILIARY)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertThat(repository.searchQueries).containsExactly("service type:auxiliary")
        assertThat(repository.searchQueries).doesNotContain("service type:exploit")
        assertThat(viewModel.uiState.value.type).isEqualTo(MetasploitModuleType.AUXILIARY)
    }

    private class SearchRepository : MetasploitModuleRepository {
        val searchQueries = mutableListOf<String>()

        override suspend fun list(type: MetasploitModuleType) =
            AppResult.Success(emptyList<MetasploitModuleSummary>())

        override suspend fun search(query: String): AppResult<List<MetasploitModuleSummary>> {
            searchQueries += query
            val type = if (query.contains("type:auxiliary")) {
                MetasploitModuleType.AUXILIARY
            } else {
                MetasploitModuleType.EXPLOIT
            }
            return AppResult.Success(
                listOf(MetasploitModuleSummary(type, "windows/smb/example", displayName = "Example")),
            )
        }

        override suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo> =
            error("not used")

        override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String) =
            AppResult.Success(emptyList<String>())

        override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")

        override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")

        override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> =
            error("not used")
    }
}
