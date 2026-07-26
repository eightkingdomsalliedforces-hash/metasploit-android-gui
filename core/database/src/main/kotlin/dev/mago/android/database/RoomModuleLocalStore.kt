package dev.mago.android.database

import dev.mago.android.database.dao.ModuleCatalogDao
import dev.mago.android.database.dao.ModuleHistoryDao
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleRecentEntity
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

class RoomModuleLocalStore(
    private val catalogDao: ModuleCatalogDao,
    private val historyDao: ModuleHistoryDao,
    private val mapper: ModuleDatabaseMapper,
    private val clock: () -> Long = System::currentTimeMillis,
) : ModuleLocalStore {
    override suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>) {
        val now = clock()
        catalogDao.replaceType(type.rpcName, modules.map { mapper.catalog(it, now) })
    }

    override suspend fun cacheInfo(info: MetasploitModuleInfo) {
        catalogDao.upsert(mapper.catalog(info, clock()))
    }

    override suspend fun cachedModules(type: MetasploitModuleType): List<MetasploitModuleSummary> =
        catalogDao.listByType(type.rpcName).map(mapper::summary)

    override suspend fun recordOpened(module: MetasploitModuleSummary) {
        historyDao.upsertRecent(
            ModuleRecentEntity(
                type = module.type.rpcName,
                name = module.name,
                lastOpenedAtEpochMillis = clock(),
            ),
        )
    }

    override suspend fun recent(limit: Int): List<MetasploitModuleSummary> =
        historyDao.recent(limit.coerceIn(1, 100)).map {
            MetasploitModuleSummary(type = type(it.type), name = it.name)
        }

    override suspend fun favorites(): Set<String> = historyDao.favorites()
        .mapTo(linkedSetOf()) { "${it.type}/${it.name}" }

    override suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean) {
        val entry = ModuleFavoriteEntity(
            type = module.type.rpcName,
            name = module.name,
            createdAtEpochMillis = clock(),
        )
        if (favorite) historyDao.upsertFavorite(entry) else historyDao.deleteFavorite(entry)
    }

    override suspend fun recordExecution(record: ModuleExecutionRecord) {
        historyDao.upsertExecution(mapper.execution(record))
        historyDao.insertAudit(mapper.audit(record))
    }

    override suspend fun updateExecution(
        uuid: String,
        status: MetasploitModuleRunStatus,
        resultSummary: String?,
        error: String?,
        updatedAtEpochMillis: Long,
    ) {
        val current = historyDao.executionByUuid(uuid) ?: return
        val updated = mapper.execution(current).copy(
            status = status,
            resultSummary = resultSummary,
            error = error,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        historyDao.upsertExecution(mapper.execution(updated))
        historyDao.insertAudit(mapper.audit(updated))
    }

    override suspend fun executionHistory(limit: Int): List<ModuleExecutionRecord> =
        historyDao.executionHistory(limit.coerceIn(1, 200)).map(mapper::execution)

    private fun type(rpcName: String): MetasploitModuleType =
        MetasploitModuleType.entries.firstOrNull { it.rpcName == rpcName }
            ?: error("Unknown module type: $rpcName")
}
