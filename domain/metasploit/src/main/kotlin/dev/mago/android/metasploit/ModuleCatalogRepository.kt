package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ModuleCatalogStatus(
    val offline: Boolean = false,
    val lastSuccessfulRefreshEpochMillis: Long? = null,
)

interface ModuleCatalogRepository : MetasploitModuleRepository {
    val catalogStatus: StateFlow<ModuleCatalogStatus>

    suspend fun searchCached(
        query: String,
        type: MetasploitModuleType? = null,
        limit: Int = 100,
    ): AppResult<List<MetasploitModuleSummary>>

    fun observeFavorites(): Flow<Set<MetasploitModuleSummary>>
    fun observeRecent(limit: Int = 20): Flow<List<MetasploitModuleSummary>>

    suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean): AppResult<Unit>
    suspend fun recordRecent(module: MetasploitModuleSummary): AppResult<Unit>
}
