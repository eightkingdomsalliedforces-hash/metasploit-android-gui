package dev.mago.android.metasploit

import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

data class ModuleExecutionRecord(
    val correlationId: String,
    val action: MetasploitModuleRunAction,
    val type: MetasploitModuleType,
    val name: String,
    val status: MetasploitModuleRunStatus,
    val jobId: Long?,
    val uuid: String?,
    val redactedOptions: Map<String, String>,
    val resultSummary: String?,
    val error: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

interface ModuleLocalStore {
    suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>)
    suspend fun cacheInfo(info: MetasploitModuleInfo)
    suspend fun cachedModules(type: MetasploitModuleType): List<MetasploitModuleSummary>
    suspend fun recordOpened(module: MetasploitModuleSummary)
    suspend fun recent(limit: Int = 20): List<MetasploitModuleSummary>
    suspend fun favorites(): Set<String>
    suspend fun setFavorite(module: MetasploitModuleSummary, favorite: Boolean)
    suspend fun recordExecution(record: ModuleExecutionRecord)
    suspend fun updateExecution(
        uuid: String,
        status: MetasploitModuleRunStatus,
        resultSummary: String?,
        error: String?,
        updatedAtEpochMillis: Long,
    )
    suspend fun executionHistory(limit: Int = 50): List<ModuleExecutionRecord>
}

object NoOpModuleLocalStore : ModuleLocalStore {
    override suspend fun cacheModules(type: MetasploitModuleType, modules: List<MetasploitModuleSummary>) = Unit
    override suspend fun cacheInfo(info: MetasploitModuleInfo) = Unit
    override suspend fun cachedModules(type: MetasploitModuleType): List<MetasploitModuleSummary> = emptyList()
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
    override suspend fun executionHistory(limit: Int): List<ModuleExecutionRecord> = emptyList()
}
