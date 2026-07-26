package dev.mago.android.reports

import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType

enum class ReportPreviewTab(val label: String) {
    HOSTS("Hosts"),
    SERVICES("Services"),
    VULNERABILITIES("弱點"),
    EXECUTIONS("執行紀錄"),
}

data class ReportPreviewUiModel(
    val generatedAtEpochMillis: Long,
    val workspaceName: String,
    val hosts: List<ReportHostPreviewItem>,
    val services: List<ReportServicePreviewItem>,
    val vulnerabilities: List<ReportVulnerabilityPreviewItem>,
    val executions: List<ReportExecutionPreviewItem>,
)

data class ReportHostPreviewItem(
    val address: String,
    val name: String?,
    val state: String?,
    val operatingSystem: String?,
    val operatingSystemFlavor: String?,
    val purpose: String?,
)

data class ReportServicePreviewItem(
    val host: String,
    val port: Int,
    val protocol: String,
    val state: String?,
    val name: String?,
)

data class ReportVulnerabilityPreviewItem(
    val host: String,
    val port: Int?,
    val protocol: String?,
    val name: String,
    val references: List<String>,
)

data class ReportExecutionPreviewItem(
    val correlationId: String,
    val action: MetasploitModuleRunAction,
    val type: MetasploitModuleType,
    val name: String,
    val status: MetasploitModuleRunStatus,
    val jobId: Long?,
    val uuid: String?,
    val redactedOptions: Map<String, String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

fun ReportPreviewSnapshot.toUiModel(): ReportPreviewUiModel {
    val safe = toSafeReportSnapshot()
    return ReportPreviewUiModel(
        generatedAtEpochMillis = safe.generatedAtEpochMillis,
        workspaceName = safe.workspace.name,
        hosts = safe.hosts.map { host ->
            ReportHostPreviewItem(
                address = host.address,
                name = host.name,
                state = host.state,
                operatingSystem = host.operatingSystem,
                operatingSystemFlavor = host.operatingSystemFlavor,
                purpose = host.purpose,
            )
        },
        services = safe.services.map { service ->
            ReportServicePreviewItem(
                host = service.host,
                port = service.port,
                protocol = service.protocol,
                state = service.state,
                name = service.name,
            )
        },
        vulnerabilities = safe.vulnerabilities.map { vulnerability ->
            ReportVulnerabilityPreviewItem(
                host = vulnerability.host,
                port = vulnerability.port,
                protocol = vulnerability.protocol,
                name = vulnerability.name,
                references = vulnerability.references.toList(),
            )
        },
        executions = safe.executions.map { execution ->
            ReportExecutionPreviewItem(
                correlationId = execution.correlationId,
                action = execution.action,
                type = execution.type,
                name = execution.name,
                status = execution.status,
                jobId = execution.jobId,
                uuid = execution.uuid,
                redactedOptions = execution.redactedOptions.toMap(),
                createdAtEpochMillis = execution.createdAtEpochMillis,
                updatedAtEpochMillis = execution.updatedAtEpochMillis,
            )
        },
    )
}