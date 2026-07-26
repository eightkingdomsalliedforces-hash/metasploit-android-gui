package dev.mago.android.modules

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
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
class ModulesLibraryStateTest {
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
    fun `recent mode and favorite toggle use local store only`() = runTest {
        val favorite = MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/favorite")
        val recent = MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/recent")
        val store = FakeLocalStore(
            favoriteNames = linkedSetOf(favorite.fullName),
            recentModules = mutableListOf(recent),
        )
        val viewModel = ModulesViewModel(FakeRepository(), store)

        assertThat(viewModel.uiState.value.favorites).contains(favorite.fullName)
        viewModel.setListMode(ModuleListMode.RECENT)
        assertThat(viewModel.uiState.value.visibleModules).containsExactly(recent)

        viewModel.toggleFavorite(favorite)

        assertThat(store.favoriteNames).doesNotContain(favorite.fullName)
        assertThat(viewModel.uiState.value.favorites).doesNotContain(favorite.fullName)
    }

    private class FakeRepository : MetasploitModuleRepository {
        override suspend fun list(type: MetasploitModuleType) = AppResult.Success(emptyList<MetasploitModuleSummary>())
        override suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo> =
            error("not used")
        override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String) =
            AppResult.Success(emptyList<String>())
        override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")
        override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
            error("not used")
        override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> = error("not used")
    }

    private class FakeLocalStore(
        val favoriteNames: MutableSet<String> = linkedSetOf(),
        val recentModules: MutableList<MetasploitModuleSummary> = mutableListOf(),
    ) : ModuleLocalStore {
        override suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>) = Unit
        override suspend fun cacheInfo(info: MetasploitModuleInfo) = Unit
        override suspend fun cachedModules(type: MetasploitModuleType) = emptyList<MetasploitModuleSummary>()
        override suspend fun recordOpened(module: MetasploitModuleSummary) {
            recentModules.removeAll { it.fullName == module.fullName }
            recentModules.add(0, module)
        }
        override suspend fun recent(limit: Int) = recentModules.take(limit)
        override suspend fun favorites() = favoriteNames.toSet()
        override suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean) {
            if (favorite) favoriteNames += module.fullName else favoriteNames -= module.fullName
        }
        override suspend fun recordExecution(record: ModuleExecutionRecord) = Unit
        override suspend fun updateExecution(
            uuid: String,
            status: MetasploitModuleRunStatus,
            resultSummary: String?,
            error: String?,
            updatedAtEpochMillis: Long,
        ) = Unit
        override suspend fun executionHistory(limit: Int) = emptyList<ModuleExecutionRecord>()
    }
}
