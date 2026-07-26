package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcJobService(private val transport: RpcTransport) {
    suspend fun list(token: String): AppResult<List<MetasploitJobSummary>> =
        when (val response = transport.call(RpcMethod.JOB_LIST, token)) {
            is AppResult.Failure -> response
            is AppResult.Success -> {
                val jobs = response.value.mapOrNull()
                    ?: return invalid("RPC_JOB_LIST_INVALID", "Metasploit Job 列表格式不正確")
                val parsed = jobs.mapNotNull { (id, value) ->
                    val name = (value as? RpcValue.StringValue)?.value ?: return@mapNotNull null
                    MetasploitJobSummary(id = id, name = name)
                }.sortedWith(compareBy<MetasploitJobSummary> { it.id.toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it.id })
                if (parsed.size != jobs.size) invalid("RPC_JOB_LIST_INVALID", "Metasploit Job 列表包含無效資料")
                else AppResult.Success(parsed)
            }
        }

    suspend fun info(token: String, id: String): AppResult<MetasploitJobInfo> {
        val jobId = id.toLongOrNull()?.takeIf { it >= 0 }
            ?: return invalid("RPC_JOB_ID_INVALID", "Job ID 不正確", retryable = false)
        return when (
            val response = transport.call(
                RpcMethod.JOB_INFO,
                token,
                listOf(RpcValue.IntValue(jobId)),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseInfo(id, response.value)
        }
    }

    suspend fun stop(token: String, id: String, userConfirmed: Boolean): AppResult<Unit> {
        if (!userConfirmed) {
            return invalid("RPC_JOB_CONFIRMATION_REQUIRED", "停止 Job 需要使用者明確確認", retryable = false)
        }
        val jobId = id.toLongOrNull()?.takeIf { it >= 0 }
            ?: return invalid("RPC_JOB_ID_INVALID", "Job ID 不正確", retryable = false)
        return when (
            val response = transport.call(
                RpcMethod.JOB_STOP,
                token,
                listOf(RpcValue.IntValue(jobId)),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseSuccess(response.value, "RPC_JOB_STOP_FAILED", "Metasploit 無法停止 Job")
        }
    }

    private fun parseInfo(requestedId: String, value: RpcValue): AppResult<MetasploitJobInfo> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_JOB_INFO_INVALID", "Metasploit Job 資料格式不正確")
        val id = map["jid"]?.displayValue() ?: requestedId
        val datastore = (map["datastore"] as? RpcValue.MapValue)?.value.orEmpty()
            .mapNotNull { (key, item) -> item.displayValue()?.let { key to it } }
            .toMap()
        return AppResult.Success(
            MetasploitJobInfo(
                id = id,
                name = map.string("name") ?: "Job $id",
                startTimeEpochSeconds = (map["start_time"] as? RpcValue.IntValue)?.value,
                uriPath = map.string("uripath"),
                datastore = datastore,
                extraFields = map.filterKeys { it !in KNOWN_FIELDS },
            ),
        )
    }

    private fun parseSuccess(value: RpcValue, code: String, message: String): AppResult<Unit> {
        val result = value.mapOrNull()?.string("result")
        return if (result.equals("success", ignoreCase = true)) AppResult.Success(Unit)
        else invalid(code, message)
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value
    private fun Map<String, RpcValue>.string(key: String): String? = (this[key] as? RpcValue.StringValue)?.value

    private fun RpcValue.displayValue(): String? = when (this) {
        RpcValue.Nil -> null
        is RpcValue.Bool -> value.toString()
        is RpcValue.IntValue -> value.toString()
        is RpcValue.FloatValue -> value.toString()
        is RpcValue.StringValue -> value
        is RpcValue.BinaryValue -> null
        is RpcValue.ArrayValue -> value.mapNotNull { it.displayValue() }.joinToString(", ")
        is RpcValue.MapValue -> null
    }

    private fun <T> invalid(code: String, message: String, retryable: Boolean = true): AppResult<T> =
        AppResult.Failure(AppError(errorCode = code, userMessage = message, retryable = retryable))

    private companion object {
        val KNOWN_FIELDS = setOf("jid", "name", "start_time", "uripath", "datastore")
    }
}
