package dev.mago.android.reporting

import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary

enum class ReportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
) {
    JSON("json", "application/json", "JSON"),
    CSV("csv", "text/csv", "CSV"),
}

data class ReportSnapshot(
    val generatedAtEpochMillis: Long,
    val workspace: MetasploitWorkspaceSummary,
    val hosts: List<MetasploitHostRecord>,
    val services: List<MetasploitServiceRecord>,
    val vulnerabilities: List<MetasploitVulnerabilityRecord>,
    val executions: List<ModuleExecutionRecord>,
)

data class ReportDocument(
    val id: String,
    val format: ReportFormat,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

interface ReportDocumentBuilder {
    fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument
}
