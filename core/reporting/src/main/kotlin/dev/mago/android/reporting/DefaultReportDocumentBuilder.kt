package dev.mago.android.reporting

import dev.mago.android.metasploit.ModuleExecutionRecord
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DefaultReportDocumentBuilder : ReportDocumentBuilder {
    override fun build(snapshot: ReportSnapshot, format: ReportFormat): ReportDocument {
        val timestamp = FILE_TIMESTAMP.format(Instant.ofEpochMilli(snapshot.generatedAtEpochMillis))
        val safeWorkspace = snapshot.workspace.name.replace(UNSAFE_FILE_CHARACTER, "_")
        val fileName = "mago-report-$safeWorkspace-$timestamp.${format.extension}"
        val bytes = when (format) {
            ReportFormat.JSON -> json(snapshot).utf8()
            ReportFormat.CSV -> csv(snapshot).utf8()
            ReportFormat.HTML -> html(snapshot).utf8()
            ReportFormat.ZIP -> zip(snapshot)
        }
        return ReportDocument(
            id = "${snapshot.generatedAtEpochMillis}-${snapshot.workspace.id}-${format.name}",
            format = format,
            fileName = fileName,
            mimeType = format.mimeType,
            bytes = bytes,
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
        sortedHosts(snapshot).forEachIndexed { index, host ->
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
        sortedServices(snapshot).forEachIndexed { index, service ->
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
        sortedVulnerabilities(snapshot).forEachIndexed { index, vulnerability ->
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
        sortedExecutions(snapshot).forEachIndexed { index, execution ->
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
        sortedHosts(snapshot).forEach { host ->
            row(
                "host", workspace, host.address, host.name.orEmpty(), host.state.orEmpty(),
                "", "", details(
                    "operatingSystem" to host.operatingSystem,
                    "operatingSystemFlavor" to host.operatingSystemFlavor,
                    "purpose" to host.purpose,
                ),
            )
        }
        sortedServices(snapshot).forEach { service ->
            row(
                "service", workspace, service.host, "${service.port}/${service.protocol}", service.state.orEmpty(),
                "", "", details("name" to service.name),
            )
        }
        sortedVulnerabilities(snapshot).forEach { vulnerability ->
            val endpoint = listOfNotNull(vulnerability.port?.toString(), vulnerability.protocol).joinToString("/")
            row(
                "vulnerability", workspace, vulnerability.host, endpoint, "", "", "",
                details(
                    "name" to vulnerability.name,
                    "references" to vulnerability.references.sorted().joinToString("|"),
                ),
            )
        }
        sortedExecutions(snapshot).forEach { execution ->
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

    private fun html(snapshot: ReportSnapshot): String = buildString {
        append("<!doctype html><html lang=\"zh-Hant\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<meta name=\"referrer\" content=\"no-referrer\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">")
        append("<title>MAGO 安全報告</title><style>")
        append("body{font-family:system-ui,sans-serif;margin:24px;line-height:1.5;color:#1d1b20}")
        append("table{border-collapse:collapse;width:100%;margin:12px 0 24px}th,td{border:1px solid #bbb;padding:7px;text-align:left;vertical-align:top}")
        append("th{background:#eee}code{white-space:pre-wrap;word-break:break-word}.notice{padding:12px;background:#f3f3f3;border-radius:8px}")
        append("</style></head><body><h1>MAGO 安全報告</h1><dl>")
        htmlDefinition("產生時間", iso(snapshot.generatedAtEpochMillis))
        htmlDefinition("Workspace", snapshot.workspace.name)
        htmlDefinition("Workspace ID", snapshot.workspace.id.toString())
        append("</dl><p class=\"notice\">此報告不包含 RPC 密碼、Token、Credentials、Keystore、Console、資產自由文字、模組結果或錯誤內容；敏感模組參數已再次遮罩。</p>")

        append("<h2>Hosts（${snapshot.hosts.size}）</h2><table><thead><tr><th>Address</th><th>Name</th><th>State</th><th>OS</th><th>Purpose</th></tr></thead><tbody>")
        sortedHosts(snapshot).forEach { host ->
            append("<tr>")
            htmlCell(host.address)
            htmlCell(host.name.orEmpty())
            htmlCell(host.state.orEmpty())
            htmlCell(listOfNotNull(host.operatingSystem, host.operatingSystemFlavor).joinToString(" "))
            htmlCell(host.purpose.orEmpty())
            append("</tr>")
        }
        append("</tbody></table>")

        append("<h2>Services（${snapshot.services.size}）</h2><table><thead><tr><th>Host</th><th>Port</th><th>Protocol</th><th>State</th><th>Name</th></tr></thead><tbody>")
        sortedServices(snapshot).forEach { service ->
            append("<tr>")
            htmlCell(service.host)
            htmlCell(service.port.toString())
            htmlCell(service.protocol)
            htmlCell(service.state.orEmpty())
            htmlCell(service.name.orEmpty())
            append("</tr>")
        }
        append("</tbody></table>")

        append("<h2>Vulnerabilities（${snapshot.vulnerabilities.size}）</h2><table><thead><tr><th>Host</th><th>Endpoint</th><th>Name</th><th>References</th></tr></thead><tbody>")
        sortedVulnerabilities(snapshot).forEach { vulnerability ->
            append("<tr>")
            htmlCell(vulnerability.host)
            htmlCell(listOfNotNull(vulnerability.port?.toString(), vulnerability.protocol).joinToString("/"))
            htmlCell(vulnerability.name)
            htmlCell(vulnerability.references.sorted().joinToString(", "))
            append("</tr>")
        }
        append("</tbody></table>")

        append("<h2>模組執行紀錄（${snapshot.executions.size}）</h2><table><thead><tr><th>時間</th><th>模組</th><th>動作</th><th>狀態</th><th>Job／UUID</th><th>遮罩參數</th></tr></thead><tbody>")
        sortedExecutions(snapshot).forEach { execution ->
            append("<tr>")
            htmlCell(iso(execution.createdAtEpochMillis))
            htmlCell("${execution.type.rpcName}/${execution.name}")
            htmlCell(execution.action.name)
            htmlCell(execution.status.name)
            htmlCell(listOfNotNull(execution.jobId?.let { "Job $it" }, execution.uuid).joinToString(" / "))
            htmlCell(redact(execution.redactedOptions).entries.joinToString("\n") { (key, value) -> "$key=$value" }, code = true)
            append("</tr>")
        }
        append("</tbody></table></body></html>")
    }

    private fun zip(snapshot: ReportSnapshot): ByteArray {
        val entries = listOf(
            "report.json" to json(snapshot).utf8(),
            "report.csv" to csv(snapshot).utf8(),
            "report.html" to html(snapshot).utf8(),
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name).apply { time = 0L }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun StringBuilder.htmlDefinition(term: String, value: String) {
        append("<dt><strong>")
        appendHtml(term)
        append("</strong></dt><dd>")
        appendHtml(value)
        append("</dd>")
    }

    private fun StringBuilder.htmlCell(value: String, code: Boolean = false) {
        append("<td>")
        if (code) append("<code>")
        appendHtml(value)
        if (code) append("</code>")
        append("</td>")
    }

    private fun StringBuilder.appendHtml(value: String) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
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

    private fun sortedHosts(snapshot: ReportSnapshot) = snapshot.hosts.sortedBy { it.address }
    private fun sortedServices(snapshot: ReportSnapshot) =
        snapshot.services.sortedWith(compareBy({ it.host }, { it.port }, { it.protocol }))
    private fun sortedVulnerabilities(snapshot: ReportSnapshot) =
        snapshot.vulnerabilities.sortedWith(compareBy({ it.host }, { it.port ?: -1 }, { it.name }))
    private fun sortedExecutions(snapshot: ReportSnapshot) =
        snapshot.executions.sortedWith(compareBy<ModuleExecutionRecord> { it.createdAtEpochMillis }.thenBy { it.correlationId })

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
    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

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
