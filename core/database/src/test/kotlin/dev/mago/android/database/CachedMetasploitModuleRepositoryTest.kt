package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.database.dao.ModuleCatalogDao
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleIndexEntity
import dev.mago.android.database.entity.ModuleRecentEntity
import dev.mago.android.database.entity.ModuleSearchFtsEntity
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CachedMetasploitModuleRepositoryTest {
    @Test
    fun `remote failure returns bounded cached list and marks catalog offline`() = runTest {
        val cached = ModuleIndexEntity(
            type = "exploit",
            name = "windows/cached",
            displayName = "Cached",
            description = "",
            rank = null,
            platformsText = "",
            architecturesText = "",
            authorsText = "",
            refreshedAtEpochMillis = 10,
        )
        val dao = FakeCatalogDao(listOf(cached))
        val repository = CachedMetasploitModuleRepository(
            remote = FakeRemoteRepository(listResult = failure()),
            dao = dao,
            clock = { 100 },
        )

        val result = repository.list(MetasploitModuleType.EXPLOIT)

        assertThat((result as AppResult.Success).value)
            .containsExactly(MetasploitModuleSummary(MetasploitModuleType.EXPLOIT, "windows/cached"))
        assertThat(repository.catalogStatus.value.offline).isTrue()
    }

    @Test
    fun `remote success replaces type cache and records refresh time`() = runTest {
        val dao = FakeCatalogDao(emptyList())
        val remoteValues = listOf(
            MetasploitModuleSummary(MetasploitModuleType.AUXILIARY, "scanner/one"),
            MetasploitModuleSummary(MetasploitModuleType.AUXILIARY, "scanner/two"),
        )
        val repository = CachedMetasploitModuleRepository(
            remote = FakeRemoteRepository(listResult = AppResult.Success(remoteValues)),
            dao = dao,
            clock = { 4242 },
        )

        val result = repository.list(MetasploitModuleType.AUXILIARY)

        assertThat((result as AppResult.Success).value).containsExactlyElementsIn(remoteValues).inOrder()
        assertThat(dao.index.values.map { it.name }).containsExactly("scanner/one", "scanner/two")
        assertThat(repository.catalogStatus.value.offline).isFalse()
        assertThat(repository.catalogStatus.value.lastSuccessfulRefreshEpochMillis).isEqualTo(4242)
    }

    private fun <T> failure(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_OFFLINE",
            userMessage = "RPC offline",
            retryable = true,
        ),
    )
}

private class FakeRemoteRepository(
    private val listResult: AppResult<List<MetasploitModuleSummary>>,
) : MetasploitModuleRepository {
    override suspend fun list(type: MetasploitModuleType) = listResult

    override suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo> =
        failure()

    override suspend fun compatiblePayloads(type: MetasploitModuleType, name: String): AppResult<List<String>> =
        failure()

    override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> = failure()

    override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> = failure()

    override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> = failure()

    private fun <T> failure(): AppResult<T> = AppResult.Failure(
        AppError("UNUSED", "unused", retryable = false),
    )
}

private class FakeCatalogDao(initial: List<ModuleIndexEntity>) : ModuleCatalogDao {
    val index = linkedMapOf<Pair<String, String>, ModuleIndexEntity>().apply {
        initial.forEach { put(it.type to it.name, it) }
    }
    private val searchRows = mutableListOf<ModuleSearchFtsEntity>()
    private val favoriteFlow = MutableStateFlow<List<ModuleFavoriteEntity>>(emptyList())
    private val recentFlow = MutableStateFlow<List<ModuleRecentEntity>>(emptyList())

    override suspend fun listAll(limit: Int): List<ModuleIndexEntity> = index.values.take(limit)

    override suspend fun listByType(type: String, limit: Int): List<ModuleIndexEntity> =
        index.values.filter { it.type == type }.take(limit)

    override suspend fun search(matchQuery: String, type: String?, limit: Int): List<ModuleIndexEntity> =
        index.values.filter { value -> type == null || value.type == type }.take(limit)

    override suspend fun find(type: String, name: String): ModuleIndexEntity? = index[type to name]

    override suspend fun insertIndex(values: List<ModuleIndexEntity>) {
        values.forEach { index[it.type to it.name] = it }
    }

    override suspend fun insertSearch(values: List<ModuleSearchFtsEntity>) {
        searchRows += values
    }

    override suspend fun deleteSearchByType(type: String) {
        searchRows.removeAll { it.type == type }
    }

    override suspend fun deleteSearchItem(type: String, name: String) {
        searchRows.removeAll { it.type == type && it.name == name }
    }

    override suspend fun deleteIndexByType(type: String) {
        index.entries.removeAll { it.key.first == type }
    }

    override suspend fun isFavorite(type: String, name: String): Boolean =
        favoriteFlow.value.any { it.type == type && it.name == name }

    override suspend fun addFavorite(value: ModuleFavoriteEntity) {
        favoriteFlow.value = favoriteFlow.value.filterNot {
            it.type == value.type && it.name == value.name
        } + value
    }

    override suspend fun removeFavorite(type: String, name: String) {
        favoriteFlow.value = favoriteFlow.value.filterNot { it.type == type && it.name == name }
    }

    override fun observeFavorites(): Flow<List<ModuleFavoriteEntity>> = favoriteFlow

    override suspend fun upsertRecent(value: ModuleRecentEntity) {
        recentFlow.value = recentFlow.value.filterNot {
            it.type == value.type && it.name == value.name
        } + value
    }

    override fun observeRecent(limit: Int): Flow<List<ModuleRecentEntity>> = recentFlow
}
