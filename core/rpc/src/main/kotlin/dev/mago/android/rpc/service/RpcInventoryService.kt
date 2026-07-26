package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcInventoryService(private val transport: RpcTransport) {
    suspend fun workspaces(token: String): AppResult<List<MetasploitWorkspaceSummary>> =
        when (val result = transport.call(RpcMethod.DB_WORKSPACES, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseArray(result.value, "workspaces", ::parseWorkspace)
        }

    suspend fun hosts(
        token: String,
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitHostRecord>> =
        callCollection(RpcMethod.DB_HOSTS, token, workspace, limit, offset, "hosts", ::parseHost)

    suspend fun services(
        token: String,
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitServiceRecord>> =
        callCollection(RpcMethod.DB_SERVICES, token, workspace, limit, offset, "services", ::parseService)

    suspend fun vulnerabilities(
        token: String,
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitVulnerabilityRecord>> =
        callCollection(RpcMethod.DB_VULNS, token, workspace, limit, offset, "vulns", ::parseVulnerability)

    private suspend fun <T> callCollection(
        method: RpcMethod,
        token: String,
        workspace: String,
        limit: Int,
        offset: Int,
        key: String,
        parser: (RpcValue) -> T?,
    ): AppResult<List<T>> {
        if (workspace.isBlank()) return invalid("RPC_WORKSPACE_INVALID", "Workspace 不可為空", false)
        if (limit !in 1..MAX_LIMIT || offset < 0) {
            return invalid("RPC_INVENTORY_PAGE_INVALID", "資產分頁參數不正確", false)
        }
        val options = RpcValue.MapValue(
            linkedMapOf(
                "workspace" to RpcValue.StringValue(workspace),
                "limit" to RpcValue.IntValue(limit.toLong()),
                "offset" to RpcValue.IntValue(offset.toLong()),
            ),
        )
        return when (val result = transport.call(method, token, listOf(options))) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseArray(result.value, key, parser)
        }
    }

    private fun <T> parseArray(
        root: RpcValue,
        key: String,
        parser: (RpcValue) -> T?,
    ): AppResult<List<T>> {
        val map = root.mapOrNull()
            ?: return invalid("RPC_INVENTORY_RESPONSE_INVALID", "Metasploit 資產資料格式不正確")
        val raw = (map[key] as? RpcValue.ArrayValue)?.value
            ?: return invalid("RPC_INVENTORY_LIST_INVALID", "Metasploit 資產列表格式不正確")
        val parsed = raw.mapNotNull(parser)
        if (parsed.size != raw.size) {
            return invalid("RPC_INVENTORY_ENTRY_INVALID", "Metasploit 資產項目格式不正確")
        }
        return AppResult.Success(parsed)
    }

    private fun parseWorkspace(value: RpcValue): MetasploitWorkspaceSummary? {
        val map = value.mapOrNull() ?: return null
        val id = map.long("id") ?: return null
        val name = map.string("name")?.takeIf { it.isNotBlank() } ?: return null
        return MetasploitWorkspaceSummary(
            id = id,
            name = name,
            createdAtEpochSeconds = map.long("created_at"),
            updatedAtEpochSeconds = map.long("updated_at"),
            extraFields = map.filterKeys { it !in WORKSPACE_FIELDS },
        )
    }

    private fun parseHost(value: RpcValue): MetasploitHostRecord? {
        val map = value.mapOrNull() ?: return null
        val address = map.string("address")?.takeIf { it.isNotBlank() } ?: return null
        return MetasploitHostRecord(
            address = address,
            mac = map.nonBlankString("mac"),
            name = map.nonBlankString("name"),
            state = map.nonBlankString("state"),
            operatingSystem = map.nonBlankString("os_name"),
            operatingSystemFlavor = map.nonBlankString("os_flavor"),
            servicePack = map.nonBlankString("os_sp"),
            language = map.nonBlankString("os_lang"),
            purpose = map.nonBlankString("purpose"),
            info = map.nonBlankString("info"),
            comments = map.nonBlankString("comments"),
            createdAtEpochSeconds = map.long("created_at"),
            updatedAtEpochSeconds = map.long("updated_at"),
            extraFields = map.filterKeys { it !in HOST_FIELDS },
        )
    }

    private fun parseService(value: RpcValue): MetasploitServiceRecord? {
        val map = value.mapOrNull() ?: return null
        val host = map.string("host")?.takeIf { it.isNotBlank() } ?: return null
        val port = map.long("port")?.takeIf { it in 0..65535 }?.toInt() ?: return null
        val protocol = map.string("proto")?.takeIf { it.isNotBlank() } ?: return null
        return MetasploitServiceRecord(
            host = host,
            port = port,
            protocol = protocol,
            state = map.nonBlankString("state"),
            name = map.nonBlankString("name"),
            info = map.nonBlankString("info"),
            createdAtEpochSeconds = map.long("created_at"),
            updatedAtEpochSeconds = map.long("updated_at"),
            extraFields = map.filterKeys { it !in SERVICE_FIELDS },
        )
    }

    private fun parseVulnerability(value: RpcValue): MetasploitVulnerabilityRecord? {
        val map = value.mapOrNull() ?: return null
        val host = map.string("host")?.takeIf { it.isNotBlank() } ?: return null
        val name = map.string("name")?.takeIf { it.isNotBlank() } ?: return null
        return MetasploitVulnerabilityRecord(
            host = host,
            port = map.long("port")?.takeIf { it in 0..65535 }?.toInt(),
            protocol = map.nonBlankString("proto"),
            name = name,
            references = map.string("refs")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            resource = map.nonBlankString("resource"),
            reportedAtEpochSeconds = map.long("time"),
            extraFields = map.filterKeys { it !in VULNERABILITY_FIELDS },
        )
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value
    private fun Map<String, RpcValue>.string(key: String): String? = (this[key] as? RpcValue.StringValue)?.value
    private fun Map<String, RpcValue>.nonBlankString(key: String): String? = string(key)?.takeIf { it.isNotBlank() }
    private fun Map<String, RpcValue>.long(key: String): Long? = when (val value = this[key]) {
        is RpcValue.IntValue -> value.value
        is RpcValue.StringValue -> value.value.toLongOrNull()
        else -> null
    }

    private fun <T> invalid(code: String, message: String, retryable: Boolean = true): AppResult<T> =
        AppResult.Failure(
            AppError(
                errorCode = code,
                userMessage = message,
                retryable = retryable,
            ),
        )

    private companion object {
        const val MAX_LIMIT = 100
        val WORKSPACE_FIELDS = setOf("id", "name", "created_at", "updated_at")
        val HOST_FIELDS = setOf(
            "created_at", "address", "mac", "name", "state", "os_name", "os_flavor",
            "os_sp", "os_lang", "updated_at", "purpose", "info", "comments",
        )
        val SERVICE_FIELDS = setOf(
            "host", "created_at", "updated_at", "port", "proto", "state", "name", "info",
            "resource", "parents",
        )
        val VULNERABILITY_FIELDS = setOf("port", "proto", "time", "host", "name", "refs", "resource")
    }
}
