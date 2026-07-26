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
import org.junit.Test

class ReportPreviewPresentationTest {
    @Test
    fun `ui model preserves compact summary fields alongside raw details`() {
        val preview = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1_700_000_000_000,
            workspace = MetasploitWorkspaceSummary(
                id = 7,
                name = "lab",
                createdAtEpochSeconds = 11,
                updatedAtEpochSeconds = 12,
                extraFields = emptyMap(),
            ),
            hosts = listOf(
                MetasploitHostRecord(
                    address = "192.0.2.10",
                    mac = "00:11:22:33:44:55",
                    name = "target",
                    state = "alive",
                    operatingSystem = "Linux",
                    operatingSystemFlavor = "Ubuntu",
                    servicePack = "SP1",
                    language = "en",
                    purpose = "server",
                    info = "host info",
                    comments = "host comments",
                    createdAtEpochSeconds = 21,
                    updatedAtEpochSeconds = 22,
                    extraFields = emptyMap(),
                ),
            ),
            services = listOf(
                MetasploitServiceRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    state = "open",
                    name = "https",
                    info = "service info",
                    createdAtEpochSeconds = 31,
                    updatedAtEpochSeconds = 32,
                    extraFields = emptyMap(),
                ),
            ),
            vulnerabilities = listOf(
                MetasploitVulnerabilityRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    name = "CVE-TEST",
                    references = listOf("CVE-TEST"),
                    resource = "resource",
                    reportedAtEpochSeconds = 41,
                    extraFields = emptyMap(),
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
                    resultSummary = "result",
                    error = "error",
                    createdAtEpochMillis = 1_700_000_000_000,
                    updatedAtEpochMillis = 1_700_000_000_100,
                ),
            ),
        )

        val model = preview.toUiModel()
        val host = model.hosts.single()
        val service = model.services.single()
        val vulnerability = model.vulnerabilities.single()
        val execution = model.executions.single()

        assertThat(model.generatedAtEpochMillis).isEqualTo(1_700_000_000_000)
        assertThat(model.workspaceName).isEqualTo("lab")
        assertThat(model.workspaceFields).isNotEmpty()

        assertThat(host.address).isEqualTo("192.0.2.10")
        assertThat(host.name).isEqualTo("target")
        assertThat(host.state).isEqualTo("alive")
        assertThat(host.operatingSystem).isEqualTo("Linux")
        assertThat(host.operatingSystemFlavor).isEqualTo("Ubuntu")
        assertThat(host.purpose).isEqualTo("server")
        assertThat(host.fields).isNotEmpty()

        assertThat(service.host).isEqualTo("192.0.2.10")
        assertThat(service.port).isEqualTo(443)
        assertThat(service.protocol).isEqualTo("tcp")
        assertThat(service.state).isEqualTo("open")
        assertThat(service.name).isEqualTo("https")
        assertThat(service.fields).isNotEmpty()

        assertThat(vulnerability.host).isEqualTo("192.0.2.10")
        assertThat(vulnerability.port).isEqualTo(443)
        assertThat(vulnerability.protocol).isEqualTo("tcp")
        assertThat(vulnerability.name).isEqualTo("CVE-TEST")
        assertThat(vulnerability.references).containsExactly("CVE-TEST")
        assertThat(vulnerability.fields).isNotEmpty()

        assertThat(execution.correlationId).isEqualTo("correlation-1")
        assertThat(execution.action).isEqualTo(MetasploitModuleRunAction.CHECK)
        assertThat(execution.type).isEqualTo(MetasploitModuleType.AUXILIARY)
        assertThat(execution.name).isEqualTo("scanner/test")
        assertThat(execution.status).isEqualTo(MetasploitModuleRunStatus.READY)
        assertThat(execution.redactedOptions).containsExactly("PASSWORD", "[REDACTED]")
        assertThat(execution.fields).isNotEmpty()
    }
}