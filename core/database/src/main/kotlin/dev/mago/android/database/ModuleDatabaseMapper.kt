package dev.mago.android.database

import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.ModuleCatalogEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

class ModuleDatabaseMapper {
    fun catalog(summary: MetasploitModuleSummary, refreshedAtEpochMillis: Long): ModuleCatalogEntity =
        ModuleCatalogEntity(
            type = summary.type.rpcName,
            name = summary.name,
            displayName = summary.displayName ?: summary.name,
            description = "",
            rank = summary.rank,
            refreshedAtEpochMillis = refreshedAtEpochMillis,
        )

    fun catalog(info: MetasploitModuleInfo, refreshedAtEpochMillis: Long): ModuleCatalogEntity =
        ModuleCatalogEntity(
            type = info.type.rpcName,
            name = info.name,
            displayName = info.displayName,
            description = info.description,
            rank = info.rank,
            refreshedAtEpochMillis = refreshedAtEpochMillis,
        )

    fun summary(entity: ModuleCatalogEntity): MetasploitModuleSummary =
        MetasploitModuleSummary(
            type = type(entity.type),
            name = entity.name,
            displayName = entity.displayName,
            rank = entity.rank,
        )

    fun execution(record: ModuleExecutionRecord): ModuleExecutionEntity = ModuleExecutionEntity(
        correlationId = record.correlationId,
        action = record.action.name,
        type = record.type.rpcName,
        name = record.name,
        status = record.status.name,
        jobId = record.jobId,
        uuid = record.uuid,
        redactedOptions = ModuleOptionSummaryCodec.encode(record.redactedOptions),
        resultSummary = record.resultSummary,
        error = record.error,
        createdAtEpochMillis = record.createdAtEpochMillis,
        updatedAtEpochMillis = record.updatedAtEpochMillis,
    )

    fun execution(entity: ModuleExecutionEntity): ModuleExecutionRecord = ModuleExecutionRecord(
        correlationId = entity.correlationId,
        action = MetasploitModuleRunAction.valueOf(entity.action),
        type = type(entity.type),
        name = entity.name,
        status = MetasploitModuleRunStatus.valueOf(entity.status),
        jobId = entity.jobId,
        uuid = entity.uuid,
        redactedOptions = ModuleOptionSummaryCodec.decode(entity.redactedOptions),
        resultSummary = entity.resultSummary,
        error = entity.error,
        createdAtEpochMillis = entity.createdAtEpochMillis,
        updatedAtEpochMillis = entity.updatedAtEpochMillis,
    )

    fun audit(record: ModuleExecutionRecord): AuditEventEntity = AuditEventEntity(
        correlationId = record.correlationId,
        category = "module_operation",
        action = record.action.name,
        moduleName = "${record.type.rpcName}/${record.name}",
        result = record.status.name,
        redactedOptions = ModuleOptionSummaryCodec.encode(record.redactedOptions),
        createdAtEpochMillis = record.updatedAtEpochMillis,
    )

    private fun type(rpcName: String): MetasploitModuleType =
        MetasploitModuleType.entries.firstOrNull { it.rpcName == rpcName }
            ?: error("Unknown module type: $rpcName")
}
