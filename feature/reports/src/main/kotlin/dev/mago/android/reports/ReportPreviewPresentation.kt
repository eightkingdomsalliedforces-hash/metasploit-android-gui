package dev.mago.android.reports

import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue

enum class ReportPreviewTab(val label: String) {
    HOSTS("Hosts"),
    SERVICES("Services"),
    VULNERABILITIES("弱點"),
    EXECUTIONS("執行紀錄"),
}

data class ReportPreviewUiModel(
    val generatedAtEpochMillis: Long,
    val workspaceName: String,
    val workspaceFields: List<ReportPreviewField>,
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
    val fields: List<ReportPreviewField> = emptyList(),
)

data class ReportServicePreviewItem(
    val host: String,
    val port: Int,
    val protocol: String,
    val state: String?,
    val name: String?,
    val fields: List<ReportPreviewField> = emptyList(),
)

data class ReportVulnerabilityPreviewItem(
    val host: String,
    val port: Int?,
    val protocol: String?,
    val name: String,
    val references: List<String>,
    val fields: List<ReportPreviewField> = emptyList(),
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
    val fields: List<ReportPreviewField> = emptyList(),
)

data class ReportPreviewField(
    val label: String,
    val value: ReportPreviewValue,
)

sealed interface ReportPreviewValue {
    data class Scalar(
        val text: String,
        val truncated: Boolean = false,
    ) : ReportPreviewValue

    data class Binary(
        val hex: String,
        val totalBytes: Int,
        val truncated: Boolean,
    ) : ReportPreviewValue

    data class Array(
        val values: List<ReportPreviewValue>,
        val truncated: Boolean,
    ) : ReportPreviewValue

    data class Object(
        val entries: List<ReportPreviewObjectEntry>,
        val truncated: Boolean,
    ) : ReportPreviewValue
}

data class ReportPreviewObjectEntry(
    val key: String,
    val value: ReportPreviewValue,
)

fun ReportPreviewSnapshot.toUiModel(): ReportPreviewUiModel = ReportPreviewUiModel(
    generatedAtEpochMillis = generatedAtEpochMillis,
    workspaceName = workspace.name,
    workspaceFields = listOf(
        scalarField("ID", workspace.id),
        scalarField("Name", workspace.name),
        scalarField("建立時間（epoch seconds）", workspace.createdAtEpochSeconds),
        scalarField("更新時間（epoch seconds）", workspace.updatedAtEpochSeconds),
        ReportPreviewField("extraFields", workspace.extraFields.toPreviewObject()),
    ),
    hosts = hosts.map { host ->
        ReportHostPreviewItem(
            address = host.address,
            name = host.name,
            state = host.state,
            operatingSystem = host.operatingSystem,
            operatingSystemFlavor = host.operatingSystemFlavor,
            purpose = host.purpose,
            fields = listOf(
                scalarField("Address", host.address),
                scalarField("MAC", host.mac),
                scalarField("Name", host.name),
                scalarField("State", host.state),
                scalarField("OS", host.operatingSystem),
                scalarField("OS Flavor", host.operatingSystemFlavor),
                scalarField("Service Pack", host.servicePack),
                scalarField("Language", host.language),
                scalarField("Purpose", host.purpose),
                scalarField("Info", host.info),
                scalarField("Comments", host.comments),
                scalarField("建立時間（epoch seconds）", host.createdAtEpochSeconds),
                scalarField("更新時間（epoch seconds）", host.updatedAtEpochSeconds),
                ReportPreviewField("extraFields", host.extraFields.toPreviewObject()),
            ),
        )
    },
    services = services.map { service ->
        ReportServicePreviewItem(
            host = service.host,
            port = service.port,
            protocol = service.protocol,
            state = service.state,
            name = service.name,
            fields = listOf(
                scalarField("Host", service.host),
                scalarField("Port", service.port),
                scalarField("Protocol", service.protocol),
                scalarField("State", service.state),
                scalarField("Name", service.name),
                scalarField("Info", service.info),
                scalarField("建立時間（epoch seconds）", service.createdAtEpochSeconds),
                scalarField("更新時間（epoch seconds）", service.updatedAtEpochSeconds),
                ReportPreviewField("extraFields", service.extraFields.toPreviewObject()),
            ),
        )
    },
    vulnerabilities = vulnerabilities.map { vulnerability ->
        ReportVulnerabilityPreviewItem(
            host = vulnerability.host,
            port = vulnerability.port,
            protocol = vulnerability.protocol,
            name = vulnerability.name,
            references = vulnerability.references.toList(),
            fields = listOf(
                scalarField("Host", vulnerability.host),
                scalarField("Port", vulnerability.port),
                scalarField("Protocol", vulnerability.protocol),
                scalarField("Name", vulnerability.name),
                ReportPreviewField(
                    "References",
                    ReportPreviewValue.Array(
                        values = vulnerability.references.take(MAX_CONTAINER_ITEMS)
                            .map(String::toPreviewScalar),
                        truncated = vulnerability.references.size > MAX_CONTAINER_ITEMS,
                    ),
                ),
                scalarField("Resource", vulnerability.resource),
                scalarField("Reported At（epoch seconds）", vulnerability.reportedAtEpochSeconds),
                ReportPreviewField("extraFields", vulnerability.extraFields.toPreviewObject()),
            ),
        )
    },
    executions = executions.map { execution ->
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
            fields = listOf(
                scalarField("Correlation ID", execution.correlationId),
                scalarField("Action", execution.action.name),
                scalarField("Type", execution.type.displayName),
                scalarField("Name", execution.name),
                scalarField("Status", execution.status.name),
                scalarField("Job ID", execution.jobId),
                scalarField("UUID", execution.uuid),
                ReportPreviewField(
                    "已遮罩選項",
                    execution.redactedOptions.mapValues { RpcValue.StringValue(it.value) }
                        .toPreviewObject(),
                ),
                scalarField("Result Summary", execution.resultSummary),
                scalarField("Error", execution.error),
                scalarField("建立時間（epoch millis）", execution.createdAtEpochMillis),
                scalarField("更新時間（epoch millis）", execution.updatedAtEpochMillis),
            ),
        )
    },
)

private fun scalarField(label: String, value: Any?): ReportPreviewField = ReportPreviewField(
    label = label,
    value = (value?.toString() ?: "null").toPreviewScalar(),
)

private fun String.toPreviewScalar(): ReportPreviewValue.Scalar {
    val truncated = length > MAX_STRING_CHARACTERS
    return ReportPreviewValue.Scalar(
        text = if (truncated) take(MAX_STRING_CHARACTERS) else this,
        truncated = truncated,
    )
}

private fun Map<String, RpcValue>.toPreviewObject(): ReportPreviewValue.Object {
    val ordered = entries.sortedBy { it.key }
    return ReportPreviewValue.Object(
        entries = ordered.take(MAX_CONTAINER_ITEMS).map { (key, value) ->
            ReportPreviewObjectEntry(key = key, value = value.toPreviewValue(depth = 1))
        },
        truncated = ordered.size > MAX_CONTAINER_ITEMS,
    )
}

private fun RpcValue.toPreviewValue(depth: Int): ReportPreviewValue {
    if (depth >= MAX_NESTING_DEPTH) {
        return ReportPreviewValue.Scalar(DEPTH_LIMIT_MESSAGE)
    }
    return when (this) {
        RpcValue.Nil -> ReportPreviewValue.Scalar("null")
        is RpcValue.Bool -> ReportPreviewValue.Scalar(value.toString())
        is RpcValue.IntValue -> ReportPreviewValue.Scalar(value.toString())
        is RpcValue.FloatValue -> ReportPreviewValue.Scalar(value.toString())
        is RpcValue.StringValue -> value.toPreviewScalar()
        is RpcValue.BinaryValue -> {
            val renderedBytes = value.copyOfRange(0, minOf(value.size, MAX_BINARY_BYTES))
            ReportPreviewValue.Binary(
                hex = renderedBytes.toLowercaseHex(),
                totalBytes = value.size,
                truncated = value.size > MAX_BINARY_BYTES,
            )
        }
        is RpcValue.ArrayValue -> ReportPreviewValue.Array(
            values = value.take(MAX_CONTAINER_ITEMS).map { it.toPreviewValue(depth + 1) },
            truncated = value.size > MAX_CONTAINER_ITEMS,
        )
        is RpcValue.MapValue -> {
            val ordered = value.entries.sortedBy { it.key }
            ReportPreviewValue.Object(
                entries = ordered.take(MAX_CONTAINER_ITEMS).map { (key, child) ->
                    ReportPreviewObjectEntry(
                        key = key,
                        value = child.toPreviewValue(depth + 1),
                    )
                },
                truncated = ordered.size > MAX_CONTAINER_ITEMS,
            )
        }
    }
}

private fun ByteArray.toLowercaseHex(): String {
    val characters = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xff
        characters[index * 2] = HEX_DIGITS[unsigned ushr 4]
        characters[index * 2 + 1] = HEX_DIGITS[unsigned and 0x0f]
    }
    return characters.concatToString()
}

private const val MAX_NESTING_DEPTH = 8
private const val MAX_CONTAINER_ITEMS = 500
private const val MAX_STRING_CHARACTERS = 65_536
private const val MAX_BINARY_BYTES = 4_096
private const val DEPTH_LIMIT_MESSAGE = "[已達顯示深度上限]"
private const val HEX_DIGITS = "0123456789abcdef"
