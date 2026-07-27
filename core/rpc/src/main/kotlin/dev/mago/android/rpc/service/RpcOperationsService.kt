package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcOperationsService(private val transport: RpcTransport) {
    suspend fun jobs(token: String): AppResult<List<MetasploitJobSummary>> =
        when (val result = transport.call(RpcMethod.JOB_LIST, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseJobs(result.value)
        }

    suspend fun jobInfo(token: String, jobId: String): AppResult<MetasploitJobInfo> {
        val numericId = jobId.toLongOrNull()?.takeIf { it >= 0 }
            ?: return invalid("RPC_JOB_ID_INVALID", "Job ID 必須是非負整數", retryable = false)
        return when (
            val result = transport.call(
                RpcMethod.JOB_INFO,
                token,
                listOf(RpcValue.IntValue(numericId)),
            )
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseJobInfo(jobId, result.value)
        }
    }

    suspend fun sessions(token: String): AppResult<List<MetasploitSessionSummary>> =
        when (val result = transport.call(RpcMethod.SESSION_LIST, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseSessions(result.value)
        }

    suspend fun stopJob(
        token: String,
        jobId: String,
        userConfirmed: Boolean,
    ): AppResult<Unit> {
        if (!userConfirmed) {
            return invalid(
                "RPC_JOB_CONFIRMATION_REQUIRED",
                "停止 Job 需要使用者明確確認",
                retryable = false,
            )
        }
        jobId.toLongOrNull()?.takeIf { it >= 0 }
            ?: return invalid("RPC_JOB_ID_INVALID", "Job ID 不正確", retryable = false)
        return invalid("RPC_JOB_STOP_FAILED", "Metasploit Job 停止尚未完成", retryable = false)
    }

    suspend fun stopSession(
        token: String,
        sessionId: Int,
        userConfirmed: Boolean,
    ): AppResult<Unit> {
        if (!userConfirmed) {
            return invalid(
                "RPC_SESSION_CONFIRMATION_REQUIRED",
                "停止 Session 需要使用者明確確認",
                retryable = false,
            )
        }
        if (sessionId < 0) {
            return invalid("RPC_SESSION_ID_INVALID", "Session ID 不正確", retryable = false)
        }
        return invalid("RPC_SESSION_STOP_FAILED", "Metasploit Session 停止尚未完成", retryable = false)
    }

    private fun parseJobs(value: RpcValue): AppResult<List<MetasploitJobSummary>> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_JOB_LIST_INVALID", "Metasploit Job 列表格式不正確")
        val jobs = mutableListOf<MetasploitJobSummary>()
        map.forEach { (id, rawName) ->
            val name = (rawName as? RpcValue.StringValue)?.value
                ?: return invalid("RPC_JOB_LIST_INVALID", "Metasploit Job 名稱格式不正確")
            if (id.toLongOrNull()?.let { it >= 0 } != true) {
                return invalid("RPC_JOB_LIST_INVALID", "Metasploit Job ID 格式不正確")
            }
            jobs += MetasploitJobSummary(id = id, name = name)
        }
        return AppResult.Success(
            jobs.sortedWith(compareBy<MetasploitJobSummary> { it.id.toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it.name }),
        )
    }

    private fun parseJobInfo(requestedId: String, value: RpcValue): AppResult<MetasploitJobInfo> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_JOB_INFO_INVALID", "Metasploit Job 詳情格式不正確")
        val id = map.scalar("jid") ?: requestedId
        val name = map.string("name")
            ?: return invalid("RPC_JOB_INFO_INVALID", "Metasploit Job 詳情缺少名稱")
        val datastore = (map["datastore"] as? RpcValue.MapValue)?.value.orEmpty()
            .mapValues { (name, item) ->
                if (SENSITIVE_MARKERS.any { name.contains(it, ignoreCase = true) }) MASK
                else item.displayValue()
            }
        return AppResult.Success(
            MetasploitJobInfo(
                id = id,
                name = name,
                startTimeEpochSeconds = (map["start_time"] as? RpcValue.IntValue)?.value,
                uriPath = map.string("uripath"),
                datastore = datastore,
                extraFields = map.filterKeys { it !in JOB_INFO_FIELDS },
            ),
        )
    }

    private fun parseSessions(value: RpcValue): AppResult<List<MetasploitSessionSummary>> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session 列表格式不正確")
        val sessions = mutableListOf<MetasploitSessionSummary>()
        map.forEach { (rawId, rawSession) ->
            val id = rawId.toIntOrNull()?.takeIf { it >= 0 }
                ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session ID 格式不正確")
            val session = rawSession.mapOrNull()
                ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session 資料格式不正確")
            val routes = session.string("routes")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            sessions += MetasploitSessionSummary(
                id = id,
                type = session.string("type").orEmpty(),
                description = session.string("desc").orEmpty(),
                info = session.string("info").orEmpty(),
                workspace = session.string("workspace").orEmpty(),
                sessionHost = session.string("session_host").nonBlankOrNull(),
                sessionPort = (session["session_port"] as? RpcValue.IntValue)?.value?.toInt(),
                targetHost = session.string("target_host").nonBlankOrNull(),
                username = session.string("username").nonBlankOrNull(),
                uuid = session.string("uuid").nonBlankOrNull(),
                exploitUuid = session.string("exploit_uuid").nonBlankOrNull(),
                viaExploit = session.string("via_exploit").nonBlankOrNull(),
                viaPayload = session.string("via_payload").nonBlankOrNull(),
                architecture = session.string("arch").nonBlankOrNull(),
                platform = session.string("platform").nonBlankOrNull(),
                tunnelLocal = session.string("tunnel_local").nonBlankOrNull(),
                tunnelPeer = session.string("tunnel_peer").nonBlankOrNull(),
                routes = routes,
                extraFields = session.filterKeys { it !in SESSION_FIELDS },
            )
        }
        return AppResult.Success(sessions.sortedBy { it.id })
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value
    private fun Map<String, RpcValue>.string(key: String): String? = (this[key] as? RpcValue.StringValue)?.value
    private fun Map<String, RpcValue>.scalar(key: String): String? = this[key]?.displayValue()
    private fun String?.nonBlankOrNull(): String? = this?.takeIf(String::isNotBlank)

    private fun RpcValue.displayValue(): String = when (this) {
        RpcValue.Nil -> ""
        is RpcValue.Bool -> value.toString()
        is RpcValue.IntValue -> value.toString()
        is RpcValue.FloatValue -> value.toString()
        is RpcValue.StringValue -> value
        is RpcValue.BinaryValue -> "<binary:${value.size}>"
        is RpcValue.ArrayValue -> value.joinToString(",") { it.displayValue() }
        is RpcValue.MapValue -> value.entries.joinToString(",") { (key, item) -> "$key=${item.displayValue()}" }
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
        const val MASK = "••••••••"
        val SENSITIVE_MARKERS = setOf("PASS", "PASSWORD", "TOKEN", "KEY", "SECRET", "CREDENTIAL")
        val SESSION_FIELDS = setOf(
            "type", "tunnel_local", "tunnel_peer", "via_exploit", "via_payload", "desc", "info",
            "workspace", "session_host", "session_port", "target_host", "username", "uuid",
            "exploit_uuid", "routes", "arch", "platform",
        )
    }
}
