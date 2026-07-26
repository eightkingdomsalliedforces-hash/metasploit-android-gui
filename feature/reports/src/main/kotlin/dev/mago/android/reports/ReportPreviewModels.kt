package dev.mago.android.reports

import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.reporting.ReportSnapshot

data class ReportPreviewSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)

fun ReportPreviewSnapshot.toSafeReportSnapshot(): ReportSnapshot = ReportSnapshot(
    generatedAtEpochMillis = generatedAtEpochMillis,
    workspace = workspace.copy(
        createdAtEpochSeconds = null,
        updatedAtEpochSeconds = null,
        extraFields = emptyMap(),
    ),
    hosts = hosts.map { host ->
        host.copy(
            mac = null,
            servicePack = null,
            language = null,
            info = null,
            comments = null,
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    services = services.map { service ->
        service.copy(
            info = null,
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    vulnerabilities = vulnerabilities.map { vulnerability ->
        vulnerability.copy(
            resource = null,
            reportedAtEpochSeconds = null,
            extraFields = emptyMap(),
        )
    },
    executions = executions.map { execution ->
        execution.copy(
            resultSummary = null,
            error = null,
        )
    },
)
