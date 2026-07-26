package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcOperationsService(private val transport: RpcTransport) {
    suspend fun jobs(token: String): AppResult<List<MetasploitJobSummary>> =
        when (val result = transport.call(RpcMethod.JOB_LIST, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseJobs(result.value)
        }

    suspend fun jobInfo(token: String, jobId: Int): AppResult<MetasploitJobInfo> {
        if (jobId < 0) return invalid("RPC_JOB_ID_INVALID", "Job ID 不可為負數", retryable = false)
        return when (
            val result = transport.call(
                RpcMethod.JOB_INFO,
                token,
                listOf(RpcValue.IntValue(jobId.toLong())),
            )
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseJobInfo(jobId, result.value)
        }
    }

    suspend fun sessions(token: String): AppResult<List<MetasploitSessionInfo>> =
        when (val result = transport.call(RpcMethod.SESSION_LIST, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseSessions(result.value)
        }

    private fun parseJobs(value: RpcValue): AppResult<List<MetasploitJobSummary>> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_JOB_LIST_INVALID", "Metasploit Job 列表格式不正確")
        val jobs = mutableListOf<MetasploitJobSummary>()
        for ((rawId, rawName) in map) {
            val id = rawId.toIntOrNull()
                ?: return invalid("RPC_JOB_ID_INVALID", "Metasploit 回傳了無效的 Job ID")
            val name = rawName.stringOrNull()
                ?: return invalid("RPC_JOB_NAME_INVALID", "Metasploit 回傳了無效的 Job 名稱")
            jobs += MetasploitJobSummary(id = id, name = name)
        }
        return AppResult.Success(jobs.sortedBy { it.id })
    }

    private fun parseJobInfo(requestedId: Int, value: RpcValue): AppResult<MetasploitJobInfo> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_JOB_INFO_INVALID", "Metasploit Job 詳細資料格式不正確")
        val id = map.int("jid") ?: requestedId
        val name = map.string("name")
            ?: return invalid("RPC_JOB_INFO_INVALID", "Metasploit Job 詳細資料缺少名稱")
        val datastore = (map["datastore"] as? RpcValue.MapValue)?.value.orEmpty()
        return AppResult.Success(
            MetasploitJobInfo(
                id = id,
                name = name,
                startTimeEpochSeconds = map.long("start_time"),
                uriPath = map.string("uripath"),
                datastore = datastore,
                extraFields = map.filterKeys { it !in JOB_INFO_FIELDS },
            ),
        )
    }

    private fun parseSessions(value: RpcValue): AppResult<List<MetasploitSessionInfo>> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session 列表格式不正確")
        val sessions = mutableListOf<MetasploitSessionInfo>()
        for ((rawId, rawInfo) in map) {
            val id = rawId.toIntOrNull()
                ?: return invalid("RPC_SESSION_ID_INVALID", "Metasploit 回傳了無效的 Session ID")
            val info = rawInfo.mapOrNull()
                ?: return invalid("RPC_SESSION_INFO_INVALID", "Metasploit Session 詳細資料格式不正確")
            sessions += MetasploitSessionInfo(
                id = id,
                type = info.string("type").orEmpty(),
                tunnelLocal = info.string("tunnel_local").orEmpty(),
                tunnelPeer = info.string("tunnel_peer").orEmpty(),
                viaExploit = info.string("via_exploit").orEmpty(),
                viaPayload = info.string("via_payload").orEmpty(),
                description = info.string("desc").orEmpty(),
                info = info.string("info").orEmpty(),
                workspace = info.string("workspace").orEmpty(),
                sessionHost = info.string("session_host").orEmpty(),
                sessionPort = info.int("session_port"),
                targetHost = info.string("target_host").orEmpty(),
                username = info.string("username").orEmpty(),
                uuid = info.string("uuid").orEmpty(),
                exploitUuid = info.string("exploit_uuid").orEmpty(),
                routes = info.string("routes")
                    .orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty),
                architecture = info.string("arch").orEmpty(),
                platform = info.string("platform"),
                extraFields = info.filterKeys { it !in SESSION_FIELDS },
            )
        }
        return AppResult.Success(sessions.sortedBy { it.id })
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value

    private fun RpcValue.stringOrNull(): String? = when (this) {
        is RpcValue.StringValue -> value
        else -> null
    }

    private fun Map<String, RpcValue>.string(key: String): String? = this[key]?.stringOrNull()

    private fun Map<String, RpcValue>.long(key: String): Long? = when (val value = this[key]) {
        is RpcValue.IntValue -> value.value
        is RpcValue.StringValue -> value.value.toLongOrNull()
        else -> null
    }

    private fun Map<String, RpcValue>.int(key: String): Int? = long(key)?.let { value ->
        if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
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
        val JOB_INFO_FIELDS = setOf("jid", "name", "start_time", "uripath", "datastore")
        val SESSION_FIELDS = setOf(
            "type", "tunnel_local", "tunnel_peer", "via_exploit", "via_payload", "desc", "info",
            "workspace", "session_host", "session_port", "target_host", "username", "uuid",
            "exploit_uuid", "routes", "arch", "platform",
        )
    }
}
