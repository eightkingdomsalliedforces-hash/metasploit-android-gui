package dev.mago.android.database

import dev.mago.android.common.AppResult
import dev.mago.android.database.dao.ModuleHistoryDao
import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import dev.mago.android.metasploit.ModuleExecutionHistoryRepository
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.metasploit.ModuleExecutionStatus
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomModuleExecutionHistoryRepository(
    private val dao: ModuleHistoryDao,
    private val codec: RedactedParameterCodec = RedactedParameterCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ModuleExecutionHistoryRepository {
    override fun observe(limit: Int): Flow<List<ModuleExecutionRecord>> =
        dao.observeExecutions(limit.coerceIn(1, MAX_HISTORY_RESULTS)).map { values ->
            values.mapNotNull(::toRecord)
        }

    override suspend fun begin(
        action: MetasploitModuleRunAction,
        request: MetasploitModuleRequest,
        workspace: String?,
        redactedParameters: Map<String, String>,
    ): AppResult<String> {
        if (!request.userConfirmed) {
            return failure(
                code = "MODULE_HISTORY_CONFIRMATION_REQUIRED",
                message = "建立執行紀錄前需要使用者明確確認",
                retryable = false,
            )
        }
        if (!isSafelyRedacted(redactedParameters)) {
            return failure(
                code = "MODULE_HISTORY_REDACTION_REQUIRED",
                message = "敏感參數尚未完成遮罩",
                retryable = false,
            )
        }
        val correlationId = idFactory()
        if (correlationId.isBlank()) {
            return failure("MODULE_HISTORY_ID_INVALID", "無法建立執行識別碼", retryable = false)
        }
        val now = clock()
        val encoded = codec.encode(redactedParameters)
        val execution = ModuleExecutionEntity(
            correlationId = correlationId,
            action = action.name,
            type = request.type.rpcName,
            name = request.name,
            workspace = workspace?.takeIf(String::isNotBlank),
            status = ModuleExecutionStatus.REQUESTED.name,
            jobId = null,
            uuid = null,
            redactedParameters = encoded,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return write(correlationId) {
            dao.record(
                execution = execution,
                audit = auditFor(execution, result = ModuleExecutionStatus.REQUESTED.name, now = now),
            )
        }
    }

    override suspend fun markLaunched(
        correlationId: String,
        launch: MetasploitModuleLaunch,
    ): AppResult<Unit> = update(correlationId, ModuleExecutionStatus.RUNNING) { current, now ->
        current.copy(
            status = ModuleExecutionStatus.RUNNING.name,
            jobId = launch.jobId,
            uuid = launch.uuid,
            updatedAtEpochMillis = now,
        )
    }

    override suspend fun markResult(
        correlationId: String,
        result: MetasploitModuleRunResult,
    ): AppResult<Unit> {
        val status = when (result.status) {
            MetasploitModuleRunStatus.READY,
            MetasploitModuleRunStatus.RUNNING,
            -> ModuleExecutionStatus.RUNNING
            MetasploitModuleRunStatus.COMPLETED -> ModuleExecutionStatus.COMPLETED
            MetasploitModuleRunStatus.ERRORED -> ModuleExecutionStatus.ERRORED
        }
        return update(correlationId, status) { current, now ->
            current.copy(status = status.name, updatedAtEpochMillis = now)
        }
    }

    override suspend fun markFailed(correlationId: String): AppResult<Unit> =
        update(correlationId, ModuleExecutionStatus.ERRORED) { current, now ->
            current.copy(status = ModuleExecutionStatus.ERRORED.name, updatedAtEpochMillis = now)
        }

    private suspend fun update(
        correlationId: String,
        status: ModuleExecutionStatus,
        transform: (ModuleExecutionEntity, Long) -> ModuleExecutionEntity,
    ): AppResult<Unit> {
        if (correlationId.isBlank()) {
            return failure("MODULE_HISTORY_ID_INVALID", "執行識別碼不可為空", retryable = false)
        }
        val current = try {
            dao.findExecution(correlationId)
        } catch (error: Exception) {
            return databaseFailure(error)
        } ?: return failure(
            "MODULE_HISTORY_NOT_FOUND",
            "找不到模組執行紀錄",
            retryable = false,
        )
        val now = clock()
        val updated = transform(current, now)
        return write(Unit) {
            dao.record(
                execution = updated,
                audit = auditFor(updated, result = status.name, now = now),
            )
        }
    }

    private fun auditFor(
        execution: ModuleExecutionEntity,
        result: String,
        now: Long,
    ): AuditEventEntity = AuditEventEntity(
        correlationId = execution.correlationId,
        category = "MODULE_OPERATION",
        action = execution.action,
        moduleName = "${execution.type}/${execution.name}",
        workspace = execution.workspace,
        result = result,
        redactedParameters = execution.redactedParameters,
        createdAtEpochMillis = now,
    )

    private fun toRecord(value: ModuleExecutionEntity): ModuleExecutionRecord? {
        val action = runCatching { MetasploitModuleRunAction.valueOf(value.action) }.getOrNull() ?: return null
        val type = MetasploitModuleType.entries.firstOrNull { it.rpcName == value.type } ?: return null
        val status = runCatching { ModuleExecutionStatus.valueOf(value.status) }.getOrNull() ?: return null
        val parameters = codec.decode(value.redactedParameters) ?: return null
        return ModuleExecutionRecord(
            correlationId = value.correlationId,
            action = action,
            request = MetasploitModuleRequest(
                type = type,
                name = value.name,
                options = parameters,
                userConfirmed = true,
            ),
            workspace = value.workspace,
            status = status,
            jobId = value.jobId,
            uuid = value.uuid,
            redactedParameters = parameters,
            createdAtEpochMillis = value.createdAtEpochMillis,
            updatedAtEpochMillis = value.updatedAtEpochMillis,
        )
    }

    private fun isSafelyRedacted(values: Map<String, String>): Boolean = values.all { (name, value) ->
        value.toByteArray(Charsets.UTF_8).size <= MAX_PARAMETER_BYTES &&
            value.none(Char::isISOControl) &&
            (!isSensitive(name) || value == MASK)
    }

    private fun isSensitive(name: String): Boolean {
        val upper = name.uppercase()
        val tokens = upper.split(NON_IDENTIFIER).filter(String::isNotEmpty)
        return tokens.any { it in SENSITIVE_TOKENS } ||
            upper.endsWith("PASS") ||
            upper.endsWith("PASSWORD")
    }

    private suspend fun <T> write(value: T, block: suspend () -> Unit): AppResult<T> = try {
        block()
        AppResult.Success(value)
    } catch (error: Exception) {
        databaseFailure(error)
    }

    private fun <T> databaseFailure(error: Exception): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "MODULE_HISTORY_DATABASE_FAILED",
            userMessage = "無法保存模組稽核紀錄",
            technicalMessage = error.message,
            retryable = true,
        ),
    )

    private fun <T> failure(code: String, message: String, retryable: Boolean): AppResult<T> =
        AppResult.Failure(AppError(code, message, retryable = retryable))

    private companion object {
        const val MAX_HISTORY_RESULTS = 500
        const val MAX_PARAMETER_BYTES = 8 * 1024
        const val MASK = "••••••••"
        val SENSITIVE_TOKENS = setOf("PASS", "PASSWORD", "TOKEN", "KEY", "SECRET", "CREDENTIAL")
        val NON_IDENTIFIER = Regex("[^A-Z0-9]+")
    }
}
