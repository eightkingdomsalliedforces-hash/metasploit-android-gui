package dev.mago.android.reports

import com.google.common.truth.Truth.assertThat
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.model.rpc.RpcValue
import org.junit.Test

class ReportPreviewModelsTest {
    @Test
    fun `safe snapshot preserves exported fields and clears preview-only fields`() {
        val preview = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1_700_000_000_000,
            workspace = MetasploitWorkspaceSummary(
                id = 7,
                name = "lab",
                createdAtEpochSeconds = 11,
                updatedAtEpochSeconds = 12,
                extraFields = mapOf(
                    "workspace_secret" to RpcValue.StringValue("WORKSPACE_SECRET"),
                ),
            ),
            hosts = listOf(
                MetasploitHostRecord(
                    address = "192.0.2.10",
                    mac = "00:11:22:33:44:55",
                    name = "target",
                    state = "alive",
                    operatingSystem = "Linux",
                    operatingSystemFlavor = "Ubuntu",
                    servicePack = "PREVIEW_SERVICE_PACK",
                    language = "PREVIEW_LANGUAGE",
                    purpose = "server",
                    info = "HOST_INFO_SECRET",
                    comments = "HOST_COMMENT_SECRET",
                    createdAtEpochSeconds = 21,
                    updatedAtEpochSeconds = 22,
                    extraFields = mapOf(
                        "host_secret" to RpcValue.StringValue("HOST_EXTRA_SECRET"),
                    ),
                ),
            ),
            services = listOf(
                MetasploitServiceRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    state = "open",
                    name = "https",
                    info = "SERVICE_INFO_SECRET",
                    createdAtEpochSeconds = 31,
                    updatedAtEpochSeconds = 32,
                    extraFields = mapOf(
                        "service_secret" to RpcValue.StringValue("SERVICE_EXTRA_SECRET"),
                    ),
                ),
            ),
            vulnerabilities = listOf(
                MetasploitVulnerabilityRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    name = "CVE-TEST",
                    references = listOf("CVE-TEST"),
                    resource = "VULN_RESOURCE_SECRET",
                    reportedAtEpochSeconds = 41,
                    extraFields = mapOf(
                        "vuln_secret" to RpcValue.StringValue("VULN_EXTRA_SECRET"),
                    ),
                ),
            ),
            executions = listOf(
                ModuleExecutionRecord(
                    correlationId = "correlation-1",
                    action = MetasploitModuleRunAction.CHECK,
                    type = MetasploitModuleType.AUXILIARY,
                    name = "scanner/test",
                    status = MetasploitModuleRunStatus.READY,
                    jobId = 9,
                    uuid = "uuid-1",
                    redactedOptions = mapOf("PASSWORD" to "[REDACTED]"),
                    resultSummary = "RESULT_SECRET",
                    error = "ERROR_SECRET",
                    createdAtEpochMillis = 1_700_000_000_000,
                    updatedAtEpochMillis = 1_700_000_000_100,
                ),
            ),
        )

        val safe = preview.toSafeReportSnapshot()

        assertThat(safe.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
        assertThat(safe.workspace.id).isEqualTo(7)
        assertThat(safe.workspace.name).isEqualTo("lab")
        assertThat(safe.workspace.createdAtEpochSeconds).isNull()
        assertThat(safe.workspace.updatedAtEpochSeconds).isNull()
        assertThat(safe.workspace.extraFields).isEmpty()

        val host = safe.hosts.single()
        assertThat(host.address).isEqualTo("192.0.2.10")
        assertThat(host.name).isEqualTo("target")
        assertThat(host.state).isEqualTo("alive")
        assertThat(host.operatingSystem).isEqualTo("Linux")
        assertThat(host.operatingSystemFlavor).isEqualTo("Ubuntu")
        assertThat(host.purpose).isEqualTo("server")
        assertThat(host.mac).isNull()
        assertThat(host.servicePack).isNull()
        assertThat(host.language).isNull()
        assertThat(host.info).isNull()
        assertThat(host.comments).isNull()
        assertThat(host.createdAtEpochSeconds).isNull()
        assertThat(host.updatedAtEpochSeconds).isNull()
        assertThat(host.extraFields).isEmpty()

        val service = safe.services.single()
        assertThat(service.host).isEqualTo("192.0.2.10")
        assertThat(service.port).isEqualTo(443)
        assertThat(service.protocol).isEqualTo("tcp")
        assertThat(service.state).isEqualTo("open")
        assertThat(service.name).isEqualTo("https")
        assertThat(service.info).isNull()
        assertThat(service.createdAtEpochSeconds).isNull()
        assertThat(service.updatedAtEpochSeconds).isNull()
        assertThat(service.extraFields).isEmpty()

        val vulnerability = safe.vulnerabilities.single()
        assertThat(vulnerability.host).isEqualTo("192.0.2.10")
        assertThat(vulnerability.port).isEqualTo(443)
        assertThat(vulnerability.protocol).isEqualTo("tcp")
        assertThat(vulnerability.name).isEqualTo("CVE-TEST")
        assertThat(vulnerability.references).containsExactly("CVE-TEST")
        assertThat(vulnerability.resource).isNull()
        assertThat(vulnerability.reportedAtEpochSeconds).isNull()
        assertThat(vulnerability.extraFields).isEmpty()

        val execution = safe.executions.single()
        assertThat(execution.correlationId).isEqualTo("correlation-1")
        assertThat(execution.jobId).isEqualTo(9)
        assertThat(execution.uuid).isEqualTo("uuid-1")
        assertThat(execution.redactedOptions).containsExactly("PASSWORD", "[REDACTED]")
        assertThat(execution.resultSummary).isNull()
        assertThat(execution.error).isNull()
    }
}
