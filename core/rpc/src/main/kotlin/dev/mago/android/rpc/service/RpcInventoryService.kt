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

    suspend fun currentWorkspace(token: String): AppResult<MetasploitWorkspaceSummary> {
        return when (val result = transport.call(RpcMethod.DB_CURRENT_WORKSPACE, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val map = result.value.mapOrNull()
                    ?: return invalid("RPC_CURRENT_WORKSPACE_INVALID", "Metasploit 作用中 Workspace 格式不正確")
                val name = map.string("workspace")?.takeIf { it.isNotBlank() }
                    ?: return invalid("RPC_CURRENT_WORKSPACE_NAME_MISSING", "Metasploit 沒有回傳作用中 Workspace")
                val id = map.long("workspace_id")?.takeIf { it >= 0 }
                    ?: return invalid("RPC_CURRENT_WORKSPACE_ID_MISSING", "Metasploit 沒有回傳 Workspace ID")
                AppResult.Success(
                    MetasploitWorkspaceSummary(
                        id = id,
                        name = name,
                        createdAtEpochSeconds = null,
                        updatedAtEpochSeconds = null,
                        extraFields = map.filterKeys { it !in CURRENT_WORKSPACE_FIELDS },
                    ),
                )
            }
        }
    }

    suspend fun addWorkspace(token: String, name: String): AppResult<Unit> =
        mutateWorkspace(RpcMethod.DB_ADD_WORKSPACE, token, name)

    suspend fun setWorkspace(token: String, name: String): AppResult<Unit> =
        mutateWorkspace(RpcMethod.DB_SET_WORKSPACE, token, name)

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

    private suspend fun mutateWorkspace(
        method: RpcMethod,
        token: String,
        name: String,
    ): AppResult<Unit> {
        val normalized = name.trim()
        if (!WORKSPACE_NAME_PATTERN.matches(normalized)) {
            return invalid("RPC_WORKSPACE_NAME_INVALID", "Workspace 名稱格式不正確", false)
        }
        val response = transport.call(method, token, listOf(RpcValue.StringValue(normalized)))
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> {
                val result = response.value.mapOrNull()?.string("result")
                if (result == "success") AppResult.Success(Unit)
                else invalid("RPC_WORKSPACE_MUTATION_FAILED", "Metasploit 無法完成 Workspace 操作", false)
            }
        }
    }

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
        val WORKSPACE_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}${'$'}")
        val CURRENT_WORKSPACE_FIELDS = setOf("workspace", "workspace_id")
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
