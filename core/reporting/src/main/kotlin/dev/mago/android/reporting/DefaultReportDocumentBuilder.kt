package dev.mago.android.reporting

import dev.mago.android.metasploit.ModuleExecutionRecord
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DefaultReportDocumentBuilder : ReportDocumentBuilder {
    override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
        val timestamp = FILE_TIMESTAMP.format(Instant.ofEpochMilli(snapshot.generatedAtEpochMillis))
        val safeWorkspace = snapshot.workspace.name.replace(UNSAFE_FILE_CHARACTER, "_")
        val fileName = "mago-report-$safeWorkspace-$timestamp.${format.extension}"
        val content = when (format) {
            ReportFormat.JSON -> json(snapshot)
            ReportFormat.CSV -> csv(snapshot)
        }
        return ReportDocument(
            id = "${snapshot.generatedAtEpochMillis}-${snapshot.workspace.id}-${format.name}",
            format = format,
            fileName = fileName,
            mimeType = format.mimeType,
            bytes = content.toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun json(snapshot: ReportSnapshot): String = buildString {
        append('{')
        jsonProperty("schemaVersion", "1", quoted = false)
        append(',')
        jsonProperty("generatedAt", iso(snapshot.generatedAtEpochMillis))
        append(',')
        append("\"workspace\":{")
        jsonProperty("id", snapshot.workspace.id.toString(), quoted = false)
        append(',')
        jsonProperty("name", snapshot.workspace.name)
        append('}')
        append(',')
        append("\"security\":{")
        jsonProperty("credentialsIncluded", "false", quoted = false)
        append(',')
        jsonProperty("consoleIncluded", "false", quoted = false)
        append(',')
        jsonProperty("secretsRedacted", "true", quoted = false)
        append('}')
        append(',')
        append("\"inventory\":{")
        append("\"recordLimit\":100,")
        append("\"hosts\":[")
        snapshot.hosts.sortedBy { it.address }.forEachIndexed { index, host ->
            if (index > 0) append(',')
            append('{')
            jsonProperty("address", host.address)
            host.name?.let { append(','); jsonProperty("name", it) }
            host.state?.let { append(','); jsonProperty("state", it) }
            host.operatingSystem?.let { append(','); jsonProperty("operatingSystem", it) }
            host.operatingSystemFlavor?.let { append(','); jsonProperty("operatingSystemFlavor", it) }
            host.purpose?.let { append(','); jsonProperty("purpose", it) }
            append('}')
        }
        append("],\"services\":[")
        snapshot.services.sortedWith(compareBy({ it.host }, { it.port }, { it.protocol })).forEachIndexed { index, service ->
            if (index > 0) append(',')
            append('{')
            jsonProperty("host", service.host)
            append(','); jsonProperty("port", service.port.toString(), quoted = false)
            append(','); jsonProperty("protocol", service.protocol)
            service.state?.let { append(','); jsonProperty("state", it) }
            service.name?.let { append(','); jsonProperty("name", it) }
            append('}')
        }
        append("],\"vulnerabilities\":[")
        snapshot.vulnerabilities.sortedWith(compareBy({ it.host }, { it.port ?: -1 }, { it.name })).forEachIndexed { index, vulnerability ->
            if (index > 0) append(',')
            append('{')
            jsonProperty("host", vulnerability.host)
            vulnerability.port?.let { append(','); jsonProperty("port", it.toString(), quoted = false) }
            vulnerability.protocol?.let { append(','); jsonProperty("protocol", it) }
            append(','); jsonProperty("name", vulnerability.name)
            append(",\"references\":[")
            vulnerability.references.sorted().forEachIndexed { referenceIndex, reference ->
                if (referenceIndex > 0) append(',')
                appendJsonString(reference)
            }
            append("]}")
        }
        append("]}")
        append(',')
        append("\"moduleExecutions\":[")
        snapshot.executions.sortedWith(compareBy<ModuleExecutionRecord> { it.createdAtEpochMillis }.thenBy { it.correlationId })
            .forEachIndexed { index, execution ->
                if (index > 0) append(',')
                append('{')
                jsonProperty("correlationId", execution.correlationId)
                append(','); jsonProperty("action", execution.action.name)
                append(','); jsonProperty("moduleType", execution.type.rpcName)
                append(','); jsonProperty("moduleName", execution.name)
                append(','); jsonProperty("status", execution.status.name)
                execution.jobId?.let { append(','); jsonProperty("jobId", it.toString(), quoted = false) }
                execution.uuid?.let { append(','); jsonProperty("uuid", it) }
                append(','); jsonProperty("createdAt", iso(execution.createdAtEpochMillis))
                append(','); jsonProperty("updatedAt", iso(execution.updatedAtEpochMillis))
                append(",\"options\":{")
                redact(execution.redactedOptions).entries.forEachIndexed { optionIndex, (name, value) ->
                    if (optionIndex > 0) append(',')
                    jsonProperty(name, value)
                }
                append("}}")
            }
        append("]}")
    }

    private fun csv(snapshot: ReportSnapshot): String = buildString {
        csvLine(CSV_COLUMNS)
        val workspace = snapshot.workspace.name
        snapshot.hosts.sortedBy { it.address }.forEach { host ->
            row(
                "host", workspace, host.address, host.name.orEmpty(), host.state.orEmpty(),
                "", "", details(
                    "operatingSystem" to host.operatingSystem,
                    "operatingSystemFlavor" to host.operatingSystemFlavor,
                    "purpose" to host.purpose,
                ),
            )
        }
        snapshot.services.sortedWith(compareBy({ it.host }, { it.port }, { it.protocol })).forEach { service ->
            row(
                "service", workspace, service.host, "${service.port}/${service.protocol}", service.state.orEmpty(),
                "", "", details("name" to service.name),
            )
        }
        snapshot.vulnerabilities.sortedWith(compareBy({ it.host }, { it.port ?: -1 }, { it.name })).forEach { vulnerability ->
            val endpoint = listOfNotNull(vulnerability.port?.toString(), vulnerability.protocol).joinToString("/")
            row(
                "vulnerability", workspace, vulnerability.host, endpoint, "", "", "",
                details(
                    "name" to vulnerability.name,
                    "references" to vulnerability.references.sorted().joinToString("|"),
                ),
            )
        }
        snapshot.executions.sortedWith(compareBy<ModuleExecutionRecord> { it.createdAtEpochMillis }.thenBy { it.correlationId })
            .forEach { execution ->
                row(
                    "module_execution",
                    workspace,
                    "${execution.type.rpcName}/${execution.name}",
                    execution.action.name,
                    execution.status.name,
                    iso(execution.createdAtEpochMillis),
                    iso(execution.updatedAtEpochMillis),
                    details(
                        "correlationId" to execution.correlationId,
                        "jobId" to execution.jobId?.toString(),
                        "uuid" to execution.uuid,
                        "options" to redact(execution.redactedOptions).entries.joinToString(";") { (key, value) -> "$key=$value" },
                    ),
                )
            }
    }

    private fun StringBuilder.row(vararg values: String) {
        csvLine(values.toList())
    }

    private fun StringBuilder.csvLine(values: List<String>) {
        append(values.joinToString(",") { csvCell(it) })
        append("\r\n")
    }

    private fun details(vararg values: Pair<String, String?>): String = values
        .filter { !it.second.isNullOrBlank() }
        .joinToString(";") { (key, value) -> "$key=${value.orEmpty()}" }

    private fun redact(options: Map<String, String>): Map<String, String> = options
        .filterValues { it.isNotBlank() }
        .toSortedMap()
        .mapValues { (name, value) -> if (isSensitive(name)) MASK else value }

    private fun isSensitive(name: String): Boolean {
        val normalized = name.uppercase()
        return SENSITIVE_MARKERS.any(normalized::contains)
    }

    private fun StringBuilder.jsonProperty(name: String, value: String, quoted: Boolean = true) {
        appendJsonString(name)
        append(':')
        if (quoted) appendJsonString(value) else append(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"$escaped\"" else escaped
    }

    private fun iso(epochMillis: Long): String = ISO_TIMESTAMP.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        const val MASK = "••••••••"
        val SENSITIVE_MARKERS = setOf(
            "PASSWORD", "PASS", "TOKEN", "API_KEY", "PRIVATE_KEY", "SMBPASS",
            "DB_PASSWORD", "SECRET", "CREDENTIAL",
        )
        val CSV_COLUMNS = listOf(
            "record_type", "workspace", "primary", "secondary", "status", "created_at", "updated_at", "details",
        )
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        val ISO_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
        val UNSAFE_FILE_CHARACTER = Regex("[^A-Za-z0-9._-]")
    }
}
