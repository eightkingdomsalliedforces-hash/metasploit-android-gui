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

class RawReportPreviewPresentationTest {
    @Test
    fun `full preview contains known raw fields and nested extra fields`() {
        val preview = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1_700_000_000_000,
            workspace = MetasploitWorkspaceSummary(
                id = 7,
                name = "lab",
                createdAtEpochSeconds = 11,
                updatedAtEpochSeconds = 12,
                extraFields = mapOf(
                    "zeta" to RpcValue.StringValue("WORKSPACE_EXTRA"),
                    "alpha" to RpcValue.MapValue(
                        mapOf(
                            "array" to RpcValue.ArrayValue(
                                listOf(
                                    RpcValue.Nil,
                                    RpcValue.Bool(true),
                                    RpcValue.IntValue(42),
                                    RpcValue.FloatValue(3.5),
                                ),
                            ),
                        ),
                    ),
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
                    servicePack = "HOST_SERVICE_PACK",
                    language = "HOST_LANGUAGE",
                    purpose = "server",
                    info = "HOST_INFO",
                    comments = "HOST_COMMENTS",
                    createdAtEpochSeconds = 21,
                    updatedAtEpochSeconds = 22,
                    extraFields = mapOf(
                        "binary" to RpcValue.BinaryValue(byteArrayOf(0x00, 0x0f, 0xff.toByte())),
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
                    info = "SERVICE_INFO",
                    createdAtEpochSeconds = 31,
                    updatedAtEpochSeconds = 32,
                    extraFields = mapOf("service_extra" to RpcValue.StringValue("SERVICE_EXTRA")),
                ),
            ),
            vulnerabilities = listOf(
                MetasploitVulnerabilityRecord(
                    host = "192.0.2.10",
                    port = 443,
                    protocol = "tcp",
                    name = "CVE-TEST",
                    references = listOf("CVE-TEST"),
                    resource = "VULNERABILITY_RESOURCE",
                    reportedAtEpochSeconds = 41,
                    extraFields = mapOf("vulnerability_extra" to RpcValue.Bool(false)),
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
                    resultSummary = "RESULT_SUMMARY",
                    error = "RAW_ERROR",
                    createdAtEpochMillis = 1_700_000_000_000,
                    updatedAtEpochMillis = 1_700_000_000_100,
                ),
            ),
        )

        val model = preview.toUiModel()

        assertThat(model.workspaceFields.scalar("ID")).isEqualTo("7")
        assertThat(model.workspaceFields.scalar("建立時間（epoch seconds）")).isEqualTo("11")
        val workspaceExtras = model.workspaceFields.objectValue("extraFields")
        assertThat(workspaceExtras.entries.map { it.key }).containsExactly("alpha", "zeta").inOrder()
        assertThat(workspaceExtras.find("zeta").scalarText()).isEqualTo("WORKSPACE_EXTRA")
        val nestedArray = workspaceExtras.find("alpha").objectValue().find("array").arrayValue()
        assertThat(nestedArray.values.map { it.scalarText() })
            .containsExactly("null", "true", "42", "3.5").inOrder()

        val hostFields = model.hosts.single().fields
        assertThat(hostFields.scalar("MAC")).isEqualTo("00:11:22:33:44:55")
        assertThat(hostFields.scalar("Service Pack")).isEqualTo("HOST_SERVICE_PACK")
        assertThat(hostFields.scalar("Language")).isEqualTo("HOST_LANGUAGE")
        assertThat(hostFields.scalar("Info")).isEqualTo("HOST_INFO")
        assertThat(hostFields.scalar("Comments")).isEqualTo("HOST_COMMENTS")
        val binary = hostFields.objectValue("extraFields").find("binary") as ReportPreviewValue.Binary
        assertThat(binary.hex).isEqualTo("000fff")
        assertThat(binary.totalBytes).isEqualTo(3)
        assertThat(binary.truncated).isFalse()

        assertThat(model.services.single().fields.scalar("Info")).isEqualTo("SERVICE_INFO")
        assertThat(
            model.services.single().fields.objectValue("extraFields")
                .find("service_extra").scalarText(),
        ).isEqualTo("SERVICE_EXTRA")

        assertThat(model.vulnerabilities.single().fields.scalar("Resource"))
            .isEqualTo("VULNERABILITY_RESOURCE")
        assertThat(
            model.vulnerabilities.single().fields.objectValue("extraFields")
                .find("vulnerability_extra").scalarText(),
        ).isEqualTo("false")

        val executionFields = model.executions.single().fields
        assertThat(executionFields.scalar("Result Summary")).isEqualTo("RESULT_SUMMARY")
        assertThat(executionFields.scalar("Error")).isEqualTo("RAW_ERROR")
        assertThat(executionFields.objectValue("已遮罩選項").find("PASSWORD").scalarText())
            .isEqualTo("[REDACTED]")
    }

    @Test
    fun `raw value rendering applies deterministic bounds without mutating source`() {
        val longString = "x".repeat(65_537)
        val binaryBytes = ByteArray(4_097) { it.toByte() }
        val arrayValues = List(501) { RpcValue.IntValue(it.toLong()) }
        val mapValues = (500 downTo 0).associate { index ->
            index.toString().padStart(3, '0') to RpcValue.IntValue(index.toLong())
        }
        val deepValue = nestedValue(10)
        val sourceExtraFields = mapOf(
            "long" to RpcValue.StringValue(longString),
            "binary" to RpcValue.BinaryValue(binaryBytes),
            "array" to RpcValue.ArrayValue(arrayValues),
            "map" to RpcValue.MapValue(mapValues),
            "deep" to deepValue,
        )
        val snapshot = ReportPreviewSnapshot(
            generatedAtEpochMillis = 1,
            workspace = MetasploitWorkspaceSummary(
                id = 1,
                name = "lab",
                createdAtEpochSeconds = null,
                updatedAtEpochSeconds = null,
                extraFields = sourceExtraFields,
            ),
            hosts = emptyList(),
            services = emptyList(),
            vulnerabilities = emptyList(),
            executions = emptyList(),
        )

        val extras = snapshot.toUiModel().workspaceFields.objectValue("extraFields")
        val renderedString = extras.find("long") as ReportPreviewValue.Scalar
        val renderedBinary = extras.find("binary") as ReportPreviewValue.Binary
        val renderedArray = extras.find("array") as ReportPreviewValue.Array
        val renderedMap = extras.find("map") as ReportPreviewValue.Object

        assertThat(renderedString.text.length).isEqualTo(65_536)
        assertThat(renderedString.truncated).isTrue()
        assertThat(renderedBinary.hex.length).isEqualTo(4_096 * 2)
        assertThat(renderedBinary.totalBytes).isEqualTo(4_097)
        assertThat(renderedBinary.truncated).isTrue()
        assertThat(renderedArray.values).hasSize(500)
        assertThat(renderedArray.truncated).isTrue()
        assertThat(renderedMap.entries).hasSize(500)
        assertThat(renderedMap.entries.first().key).isEqualTo("000")
        assertThat(renderedMap.entries.last().key).isEqualTo("499")
        assertThat(renderedMap.truncated).isTrue()
        assertThat(extras.find("deep").containsDepthLimit()).isTrue()

        assertThat((sourceExtraFields.getValue("long") as RpcValue.StringValue).value.length)
            .isEqualTo(65_537)
        assertThat((sourceExtraFields.getValue("binary") as RpcValue.BinaryValue).value)
            .hasLength(4_097)
        assertThat((sourceExtraFields.getValue("array") as RpcValue.ArrayValue).value)
            .hasSize(501)
        assertThat((sourceExtraFields.getValue("map") as RpcValue.MapValue).value)
            .hasSize(501)
    }

    private fun nestedValue(depth: Int): RpcValue = if (depth == 0) {
        RpcValue.StringValue("leaf")
    } else {
        RpcValue.MapValue(mapOf("level-$depth" to nestedValue(depth - 1)))
    }

    private fun List<ReportPreviewField>.field(label: String): ReportPreviewValue =
        single { it.label == label }.value

    private fun List<ReportPreviewField>.scalar(label: String): String = field(label).scalarText()

    private fun List<ReportPreviewField>.objectValue(label: String): ReportPreviewValue.Object =
        field(label).objectValue()

    private fun ReportPreviewValue.scalarText(): String = (this as ReportPreviewValue.Scalar).text

    private fun ReportPreviewValue.objectValue(): ReportPreviewValue.Object =
        this as ReportPreviewValue.Object

    private fun ReportPreviewValue.arrayValue(): ReportPreviewValue.Array =
        this as ReportPreviewValue.Array

    private fun ReportPreviewValue.Object.find(key: String): ReportPreviewValue =
        entries.single { it.key == key }.value

    private fun ReportPreviewValue.containsDepthLimit(): Boolean = when (this) {
        is ReportPreviewValue.Scalar -> text == "[已達顯示深度上限]"
        is ReportPreviewValue.Binary -> false
        is ReportPreviewValue.Array -> values.any { it.containsDepthLimit() }
        is ReportPreviewValue.Object -> entries.any { it.value.containsDepthLimit() }
    }
}