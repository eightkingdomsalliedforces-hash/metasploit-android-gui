package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitSessionRead
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcSessionService(private val transport: RpcTransport) {
    suspend fun list(token: String): AppResult<List<MetasploitSessionSummary>> =
        when (val response = transport.call(RpcMethod.SESSION_LIST, token)) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseList(response.value)
        }

    suspend fun stop(token: String, id: Int, userConfirmed: Boolean): AppResult<Unit> {
        if (!userConfirmed) {
            return invalid("RPC_SESSION_CONFIRMATION_REQUIRED", "停止 Session 需要使用者明確確認", retryable = false)
        }
        if (id < 0) return invalid("RPC_SESSION_ID_INVALID", "Session ID 不正確", retryable = false)
        return when (
            val response = transport.call(
                RpcMethod.SESSION_STOP,
                token,
                listOf(RpcValue.IntValue(id.toLong())),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseSuccess(response.value, "RPC_SESSION_STOP_FAILED", "Metasploit 無法停止 Session")
        }
    }

    suspend fun read(token: String, id: Int): AppResult<MetasploitSessionRead> {
        if (id < 0) return invalid("RPC_SESSION_ID_INVALID", "Session ID 不正確", retryable = false)
        return when (
            val response = transport.call(
                RpcMethod.SESSION_INTERACTIVE_READ,
                token,
                listOf(RpcValue.IntValue(id.toLong())),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> {
                val data = response.value.mapOrNull()?.string("data")
                    ?: return invalid("RPC_SESSION_READ_INVALID", "Session 輸出格式不正確")
                AppResult.Success(MetasploitSessionRead(data))
            }
        }
    }

    suspend fun write(
        token: String,
        id: Int,
        input: String,
        userConfirmed: Boolean,
    ): AppResult<Unit> {
        if (!userConfirmed) {
            return invalid("RPC_SESSION_CONFIRMATION_REQUIRED", "Session 互動需要使用者明確確認", retryable = false)
        }
        if (id < 0) return invalid("RPC_SESSION_ID_INVALID", "Session ID 不正確", retryable = false)
        if (input.isBlank()) return invalid("RPC_SESSION_INPUT_EMPTY", "Session 輸入不可為空", retryable = false)
        if (input.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES) {
            return invalid("RPC_SESSION_INPUT_TOO_LARGE", "Session 輸入不可超過 8 KiB", retryable = false)
        }
        if (input.any(Char::isISOControl)) {
            return invalid("RPC_SESSION_INPUT_CONTROL_CHARACTER", "Session 輸入不可包含控制字元", retryable = false)
        }
        return when (
            val response = transport.call(
                RpcMethod.SESSION_INTERACTIVE_WRITE,
                token,
                listOf(RpcValue.IntValue(id.toLong()), RpcValue.StringValue(input)),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseSuccess(response.value, "RPC_SESSION_WRITE_FAILED", "Metasploit 無法寫入 Session")
        }
    }

    private fun parseList(value: RpcValue): AppResult<List<MetasploitSessionSummary>> {
        val root = value.mapOrNull()
            ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session 列表格式不正確")
        val sessions = mutableListOf<MetasploitSessionSummary>()
        root.forEach { (rawId, rawValue) ->
            val id = rawId.toIntOrNull()?.takeIf { it >= 0 }
                ?: return invalid("RPC_SESSION_ID_INVALID", "Metasploit Session ID 格式不正確")
            val map = rawValue.mapOrNull()
                ?: return invalid("RPC_SESSION_LIST_INVALID", "Metasploit Session 資料格式不正確")
            sessions += MetasploitSessionSummary(
                id = id,
                type = map.string("type").orEmpty(),
                tunnelLocal = map.string("tunnel_local").orEmpty(),
                tunnelPeer = map.string("tunnel_peer").orEmpty(),
                viaExploit = map.string("via_exploit").orEmpty(),
                viaPayload = map.string("via_payload").orEmpty(),
                description = map.string("desc").orEmpty(),
                info = map.string("info").orEmpty(),
                workspace = map.string("workspace").orEmpty(),
                sessionHost = map.string("session_host").orEmpty(),
                sessionPort = (map["session_port"] as? RpcValue.IntValue)?.value?.toInt(),
                targetHost = map.string("target_host").orEmpty(),
                username = map.string("username").orEmpty(),
                uuid = map.string("uuid").orEmpty(),
                exploitUuid = map.string("exploit_uuid").orEmpty(),
                routes = map.string("routes").orEmpty(),
                architecture = map.string("arch").orEmpty(),
                platform = map.string("platform").orEmpty(),
                extraFields = map.filterKeys { it !in KNOWN_FIELDS },
            )
        }
        return AppResult.Success(sessions.sortedBy { it.id })
    }

    private fun parseSuccess(value: RpcValue, code: String, message: String): AppResult<Unit> {
        val result = value.mapOrNull()?.string("result")
        return if (result.equals("success", ignoreCase = true)) AppResult.Success(Unit)
        else invalid(code, message)
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value
    private fun Map<String, RpcValue>.string(key: String): String? = (this[key] as? RpcValue.StringValue)?.value

    private fun <T> invalid(code: String, message: String, retryable: Boolean = true): AppResult<T> =
        AppResult.Failure(AppError(errorCode = code, userMessage = message, retryable = retryable))

    private companion object {
        const val MAX_INPUT_BYTES = 8 * 1024
        val KNOWN_FIELDS = setOf(
            "type", "tunnel_local", "tunnel_peer", "via_exploit", "via_payload", "desc", "info",
            "workspace", "session_host", "session_port", "target_host", "username", "uuid",
            "exploit_uuid", "routes", "arch", "platform",
        )
    }
}
