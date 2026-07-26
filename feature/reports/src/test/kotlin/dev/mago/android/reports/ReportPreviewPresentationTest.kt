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

class ReportPreviewPresentationTest {
    @Test
    fun `ui model contains only fields permitted in safe export`() {
        val preview = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1_700_000_000_000,
            workspace = MetasploitWorkspaceSummary(
                id = 7,
                name = "lab",
                createdAtEpochSeconds = 11,
                updatedAtEpochSeconds = 12,
                extraFields = mapOf("workspace_secret" to RpcValue.StringValue("WORKSPACE_SECRET")),
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
                    extraFields = mapOf("host_secret" to RpcValue.StringValue("HOST_EXTRA_SECRET")),
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
                    extraFields = mapOf("service_secret" to RpcValue.StringValue("SERVICE_EXTRA_SECRET")),
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
                    extraFields = mapOf("vuln_secret" to RpcValue.StringValue("VULN_EXTRA_SECRET")),
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

        val model = preview.toUiModel()

        assertThat(model.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
        assertThat(model.workspaceName).isEqualTo("lab")
        assertThat(model.hosts.single()).isEqualTo(
            ReportHostPreviewItem(
                address = "192.0.2.10",
                name = "target",
                state = "alive",
                operatingSystem = "Linux",
                operatingSystemFlavor = "Ubuntu",
                purpose = "server",
            ),
        )
        assertThat(model.services.single()).isEqualTo(
            ReportServicePreviewItem(
                host = "192.0.2.10",
                port = 443,
                protocol = "tcp",
                state = "open",
                name = "https",
            ),
        )
        assertThat(model.vulnerabilities.single()).isEqualTo(
            ReportVulnerabilityPreviewItem(
                host = "192.0.2.10",
                port = 443,
                protocol = "tcp",
                name = "CVE-TEST",
                references = listOf("CVE-TEST"),
            ),
        )
        assertThat(model.executions.single()).isEqualTo(
            ReportExecutionPreviewItem(
                correlationId = "correlation-1",
                action = MetasploitModuleRunAction.CHECK,
                type = MetasploitModuleType.AUXILIARY,
                name = "scanner/test",
                status = MetasploitModuleRunStatus.READY,
                jobId = 9,
                uuid = "uuid-1",
                redactedOptions = mapOf("PASSWORD" to "[REDACTED]"),
                createdAtEpochMillis = 1_700_000_000_000,
                updatedAtEpochMillis = 1_700_000_000_100,
            ),
        )
    }
}