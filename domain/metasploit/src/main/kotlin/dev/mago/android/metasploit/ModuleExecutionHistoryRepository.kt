package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunResult
import kotlinx.coroutines.flow.Flow

enum class ModuleExecutionStatus {
    REQUESTED,
    RUNNING,
    COMPLETED,
    ERRORED,
}

data class ModuleExecutionRecord(
    val correlationId: String,
    val action: MetasploitModuleRunAction,
    val request: MetasploitModuleRequest,
    val workspace: String?,
    val status: ModuleExecutionStatus,
    val jobId: Long?,
    val uuid: String?,
    val redactedParameters: Map<String, String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

interface ModuleExecutionHistoryRepository {
    fun observe(limit: Int = 100): Flow<List<ModuleExecutionRecord>>

    suspend fun begin(
        action: MetasploitModuleRunAction,
        request: MetasploitModuleRequest,
        workspace: String?,
        redactedParameters: Map<String, String>,
    ): AppResult<String>

    suspend fun markLaunched(
        correlationId: String,
        launch: MetasploitModuleLaunch,
    ): AppResult<Unit>

    suspend fun markResult(
        correlationId: String,
        result: MetasploitModuleRunResult,
    ): AppResult<Unit>

    suspend fun markFailed(correlationId: String): AppResult<Unit>
}
