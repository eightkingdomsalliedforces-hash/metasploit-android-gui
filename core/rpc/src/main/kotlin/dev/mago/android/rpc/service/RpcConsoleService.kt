package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitConsoleSnapshot
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcConsoleService(private val transport: RpcTransport) {
    suspend fun create(token: String): AppResult<MetasploitConsoleSnapshot> =
        when (val result = transport.call(RpcMethod.CONSOLE_CREATE, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseSnapshot(result.value, output = "")
        }

    suspend fun read(token: String, consoleId: String): AppResult<MetasploitConsoleSnapshot> =
        when (val result = transport.call(
            RpcMethod.CONSOLE_READ,
            token,
            listOf(RpcValue.StringValue(consoleId)),
        )) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseSnapshot(result.value, fallbackId = consoleId)
        }

    suspend fun write(token: String, consoleId: String, command: String): AppResult<Unit> =
        when (val result = transport.call(
            RpcMethod.CONSOLE_WRITE,
            token,
            listOf(RpcValue.StringValue(consoleId), RpcValue.StringValue(command)),
        )) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val map = (result.value as? RpcValue.MapValue)?.value
                val wrote = (map?.get("wrote") as? RpcValue.IntValue)?.value
                if (wrote == null || wrote < 0L) invalid("RPC_CONSOLE_WRITE_INVALID", "Console 寫入結果格式不正確")
                else AppResult.Success(Unit)
            }
        }

    suspend fun destroy(token: String, consoleId: String): AppResult<Unit> =
        when (val result = transport.call(
            RpcMethod.CONSOLE_DESTROY,
            token,
            listOf(RpcValue.StringValue(consoleId)),
        )) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val status = ((result.value as? RpcValue.MapValue)?.value?.get("result") as? RpcValue.StringValue)?.value
                if (status == "success") AppResult.Success(Unit)
                else invalid("RPC_CONSOLE_DESTROY_FAILED", "無法關閉 Metasploit Console")
            }
        }

    private fun parseSnapshot(
        value: RpcValue,
        fallbackId: String? = null,
        output: String? = null,
    ): AppResult<MetasploitConsoleSnapshot> {
        val map = (value as? RpcValue.MapValue)?.value
            ?: return invalid("RPC_CONSOLE_RESPONSE_INVALID", "Console 回傳格式不正確")
        if ((map["result"] as? RpcValue.StringValue)?.value == "failure") {
            return invalid("RPC_CONSOLE_NOT_FOUND", "Metasploit Console 已不存在")
        }
        val id = map["id"].asId() ?: fallbackId
            ?: return invalid("RPC_CONSOLE_RESPONSE_INVALID", "Console 回傳資料缺少 ID")
        return AppResult.Success(
            MetasploitConsoleSnapshot(
                id = id,
                prompt = (map["prompt"] as? RpcValue.StringValue)?.value ?: "",
                busy = (map["busy"] as? RpcValue.Bool)?.value ?: false,
                output = output ?: (map["data"] as? RpcValue.StringValue)?.value ?: "",
            ),
        )
    }

    private fun RpcValue?.asId(): String? = when (this) {
        is RpcValue.StringValue -> value
        is RpcValue.IntValue -> value.toString()
        else -> null
    }

    private fun <T> invalid(code: String, message: String): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = message,
            retryable = true,
        ),
    )
}
