package dev.mago.android.database

import dev.mago.android.common.AppResult
import dev.mago.android.database.dao.ModuleCatalogDao
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleRecentEntity
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.ModuleCatalogRepository
import dev.mago.android.metasploit.ModuleCatalogStatus
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class CachedMetasploitModuleRepository(
    private val remote: MetasploitModuleRepository,
    private val dao: ModuleCatalogDao,
    private val mapper: ModuleCatalogMapper = ModuleCatalogMapper(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ModuleCatalogRepository {
    private val mutableCatalogStatus = MutableStateFlow(ModuleCatalogStatus())
    override val catalogStatus = mutableCatalogStatus.asStateFlow()

    override suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>> {
        return when (val result = remote.list(type)) {
            is AppResult.Success -> {
                val now = clock()
                val indexValues = result.value.map { mapper.fromSummary(it, now) }
                runCatching {
                    dao.replaceType(type.rpcName, indexValues, indexValues.map(mapper::toSearch))
                }
                mutableCatalogStatus.value = ModuleCatalogStatus(
                    offline = false,
                    lastSuccessfulRefreshEpochMillis = now,
                )
                result
            }
            is AppResult.Failure -> {
                val cached = runCatching { dao.listByType(type.rpcName, MAX_LIST_RESULTS) }
                    .getOrDefault(emptyList())
                    .mapNotNull(mapper::toSummary)
                if (cached.isEmpty()) result else {
                    mutableCatalogStatus.value = mutableCatalogStatus.value.copy(offline = true)
                    AppResult.Success(cached)
                }
            }
        }
    }

    override suspend fun info(
        type: MetasploitModuleType,
        name: String,
    ): AppResult<MetasploitModuleInfo> {
        return when (val result = remote.info(type, name)) {
            is AppResult.Success -> {
                val now = clock()
                val index = mapper.fromInfo(result.value, now)
                runCatching {
                    dao.upsertDetail(index, mapper.toSearch(index))
                    dao.upsertRecent(ModuleRecentEntity(type.rpcName, name, now))
                }
                mutableCatalogStatus.value = ModuleCatalogStatus(
                    offline = false,
                    lastSuccessfulRefreshEpochMillis = now,
                )
                result
            }
            is AppResult.Failure -> {
                val cached = runCatching { dao.find(type.rpcName, name) }
                    .getOrNull()
                    ?.let(mapper::toInfo)
                if (cached == null) result else {
                    mutableCatalogStatus.value = mutableCatalogStatus.value.copy(offline = true)
                    runCatching { dao.upsertRecent(ModuleRecentEntity(type.rpcName, name, clock())) }
                    AppResult.Success(cached)
                }
            }
        }
    }

    override suspend fun compatiblePayloads(
        type: MetasploitModuleType,
        name: String,
    ): AppResult<List<String>> = remote.compatiblePayloads(type, name)

    override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        remote.check(request)

    override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        remote.execute(request)

    override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> = remote.result(uuid)

    override suspend fun searchCached(
        query: String,
        type: MetasploitModuleType?,
        limit: Int,
    ): AppResult<List<MetasploitModuleSummary>> = databaseResult("MODULE_CACHE_SEARCH_FAILED") {
        val boundedLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val matchQuery = buildMatchQuery(query)
        val entities = when {
            matchQuery == null && type == null -> dao.listAll(boundedLimit)
            matchQuery == null -> dao.listByType(requireNotNull(type).rpcName, boundedLimit)
            else -> dao.search(matchQuery, type?.rpcName, boundedLimit)
        }
        entities.mapNotNull(mapper::toSummary)
    }

    override fun observeFavorites(): Flow<Set<MetasploitModuleSummary>> =
        dao.observeFavorites().map { values ->
            values.mapNotNull { value -> summary(value.type, value.name) }.toSet()
        }

    override fun observeRecent(limit: Int): Flow<List<MetasploitModuleSummary>> =
        dao.observeRecent(limit.coerceIn(1, MAX_RECENT_RESULTS)).map { values ->
            values.mapNotNull { value -> summary(value.type, value.name) }
        }

    override suspend fun setFavorite(
        module: MetasploitModuleSummary,
        favorite: Boolean,
    ): AppResult<Unit> = databaseResult("MODULE_FAVORITE_WRITE_FAILED") {
        if (favorite) {
            dao.addFavorite(
                ModuleFavoriteEntity(
                    type = module.type.rpcName,
                    name = module.name,
                    createdAtEpochMillis = clock(),
                ),
            )
        } else {
            dao.removeFavorite(module.type.rpcName, module.name)
        }
    }

    override suspend fun recordRecent(module: MetasploitModuleSummary): AppResult<Unit> =
        databaseResult("MODULE_RECENT_WRITE_FAILED") {
            dao.upsertRecent(
                ModuleRecentEntity(
                    type = module.type.rpcName,
                    name = module.name,
                    lastOpenedAtEpochMillis = clock(),
                ),
            )
        }

    private fun summary(type: String, name: String): MetasploitModuleSummary? {
        val parsedType = MetasploitModuleType.entries.firstOrNull { it.rpcName == type } ?: return null
        return MetasploitModuleSummary(parsedType, name)
    }

    private fun buildMatchQuery(query: String): String? {
        val tokens = SEARCH_TOKEN.findAll(query.trim())
            .map { it.value }
            .filter { it.isNotBlank() }
            .take(MAX_SEARCH_TOKENS)
            .toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
    }

    private suspend fun <T> databaseResult(
        errorCode: String,
        block: suspend () -> T,
    ): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: Exception) {
        AppResult.Failure(
            AppError(
                errorCode = errorCode,
                userMessage = "無法讀寫本機模組資料",
                technicalMessage = error.message,
                retryable = true,
            ),
        )
    }

    private companion object {
        const val MAX_LIST_RESULTS = 20_000
        const val MAX_SEARCH_RESULTS = 500
        const val MAX_RECENT_RESULTS = 100
        const val MAX_SEARCH_TOKENS = 8
        val SEARCH_TOKEN = Regex("[\\p{L}\\p{N}_./:-]+")
    }
}
