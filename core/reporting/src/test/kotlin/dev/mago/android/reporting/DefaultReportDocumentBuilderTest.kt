package dev.mago.android.reporting

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
import java.nio.charset.StandardCharsets
import org.junit.Test

class DefaultReportDocumentBuilderTest {
    private val builder = DefaultReportDocumentBuilder()

    @Test
    fun `json uses whitelist fields and masks sensitive option values again`() {
        val text = builder.build(snapshot(), ReportFormat.JSON).bytes.toString(StandardCharsets.UTF_8)

        assertThat(text).contains("\"workspace\":{\"id\":7,\"name\":\"lab\"}")
        assertThat(text).contains("\"PASSWORD\":\"••••••••\"")
        assertThat(text).contains("host \\\"quoted\\\"\\nline")
        assertExcludedSecrets(text)
    }

    @Test
    fun `csv follows quoting rules and excludes free form secrets`() {
        val text = builder.build(snapshot(), ReportFormat.CSV).bytes.toString(StandardCharsets.UTF_8)

        assertThat(text.lines().first()).isEqualTo(
            "record_type,workspace,primary,secondary,status,created_at,updated_at,details",
        )
        assertThat(text).contains("\"host \"\"quoted\"\"\nline\"")
        assertThat(text).contains("PASSWORD=••••••••")
        assertExcludedSecrets(text)
    }

    private fun assertExcludedSecrets(text: String) {
        assertThat(text).doesNotContain("superSecretPassword")
        assertThat(text).doesNotContain("rpc-token-secret")
        assertThat(text).doesNotContain("result-secret")
        assertThat(text).doesNotContain("error-secret")
        assertThat(text).doesNotContain("asset-comment-secret")
        assertThat(text).doesNotContain("asset-info-secret")
        assertThat(text).doesNotContain("extra-secret")
    }

    private fun snapshot() = ReportSnapshot(
        generatedAtEpochMillis = 1_700_000_000_000,
        workspace = MetasploitWorkspaceSummary(
            id = 7,
            name = "lab",
            createdAtEpochSeconds = null,
            updatedAtEpochSeconds = null,
            extraFields = mapOf("rpc_token" to RpcValue.StringValue("rpc-token-secret")),
        ),
        hosts = listOf(
            MetasploitHostRecord(
                address = "192.0.2.10",
                mac = "00:11:22:33:44:55",
                name = "host \"quoted\"\nline",
                state = "alive",
                operatingSystem = "Linux",
                operatingSystemFlavor = "Example",
                servicePack = null,
                language = null,
                purpose = "server",
                info = "asset-info-secret",
                comments = "asset-comment-secret",
                createdAtEpochSeconds = null,
                updatedAtEpochSeconds = null,
                extraFields = mapOf("future" to RpcValue.StringValue("extra-secret")),
            ),
        ),
        services = listOf(
            MetasploitServiceRecord(
                host = "192.0.2.10",
                port = 443,
                protocol = "tcp",
                state = "open",
                name = "https",
                info = "asset-info-secret",
                createdAtEpochSeconds = null,
                updatedAtEpochSeconds = null,
                extraFields = mapOf("future" to RpcValue.StringValue("extra-secret")),
            ),
        ),
        vulnerabilities = listOf(
            MetasploitVulnerabilityRecord(
                host = "192.0.2.10",
                port = 443,
                protocol = "tcp",
                name = "Example, vulnerability",
                references = listOf("CVE-2026-0001"),
                resource = "/private/asset-info-secret",
                reportedAtEpochSeconds = null,
                extraFields = mapOf("future" to RpcValue.StringValue("extra-secret")),
            ),
        ),
        executions = listOf(
            ModuleExecutionRecord(
                correlationId = "correlation-1",
                action = MetasploitModuleRunAction.CHECK,
                type = MetasploitModuleType.AUXILIARY,
                name = "scanner/example",
                status = MetasploitModuleRunStatus.COMPLETED,
                jobId = 4,
                uuid = "uuid-1",
                redactedOptions = mapOf(
                    "RHOSTS" to "192.0.2.10",
                    "PASSWORD" to "superSecretPassword",
                ),
                resultSummary = "result-secret",
                error = "error-secret",
                createdAtEpochMillis = 1_700_000_000_000,
                updatedAtEpochMillis = 1_700_000_001_000,
            ),
        ),
    )
}
