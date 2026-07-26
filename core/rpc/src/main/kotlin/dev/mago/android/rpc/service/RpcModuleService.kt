package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleOption
import dev.mago.android.model.MetasploitModuleReference
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcModuleService(private val transport: RpcTransport) {
    suspend fun list(token: String, type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>> {
        return when (val result = transport.call(type.listMethod(), token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val root = result.value.mapOrNull()
                val modules = (root?.get("modules") as? RpcValue.ArrayValue)?.value
                    ?.mapNotNull { (it as? RpcValue.StringValue)?.value }
                if (modules == null) invalid("RPC_MODULE_LIST_INVALID", "Metasploit 模組列表格式不正確")
                else AppResult.Success(modules.sorted().map { MetasploitModuleSummary(type, it) })
            }
        }
    }

    suspend fun info(
        token: String,
        type: MetasploitModuleType,
        name: String,
    ): AppResult<MetasploitModuleInfo> {
        if (name.isBlank()) return invalid("RPC_MODULE_NAME_INVALID", "模組名稱不可為空", retryable = false)
        val arguments = listOf(
            RpcValue.StringValue(type.rpcName),
            RpcValue.StringValue(name),
        )
        return when (val result = transport.call(RpcMethod.MODULE_INFO, token, arguments)) {
            is AppResult.Failure -> result
            is AppResult.Success -> parseInfo(type, name, result.value)
        }
    }

    suspend fun compatiblePayloads(
        token: String,
        type: MetasploitModuleType,
        name: String,
    ): AppResult<List<String>> {
        val method = when (type) {
            MetasploitModuleType.EXPLOIT -> RpcMethod.MODULE_COMPATIBLE_PAYLOADS
            MetasploitModuleType.EVASION -> RpcMethod.MODULE_COMPATIBLE_EVASION_PAYLOADS
            else -> return AppResult.Success(emptyList())
        }
        val result = transport.call(method, token, listOf(RpcValue.StringValue(name)))
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val payloads = (result.value.mapOrNull()?.get("payloads") as? RpcValue.ArrayValue)?.value
                    ?.mapNotNull { (it as? RpcValue.StringValue)?.value }
                if (payloads == null) invalid("RPC_COMPATIBLE_PAYLOADS_INVALID", "相容 Payload 格式不正確")
                else AppResult.Success(payloads.sorted())
            }
        }
    }

    suspend fun check(token: String, request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        launch(token, RpcMethod.MODULE_CHECK, request)

    suspend fun execute(token: String, request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        launch(token, RpcMethod.MODULE_EXECUTE, request)

    suspend fun result(token: String, uuid: String): AppResult<MetasploitModuleRunResult> {
        if (uuid.isBlank()) return invalid("RPC_MODULE_UUID_INVALID", "模組執行 UUID 不可為空", retryable = false)
        return when (
            val response = transport.call(
                RpcMethod.MODULE_RESULTS,
                token,
                listOf(RpcValue.StringValue(uuid)),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseRunResult(response.value)
        }
    }

    private suspend fun launch(
        token: String,
        method: RpcMethod,
        request: MetasploitModuleRequest,
    ): AppResult<MetasploitModuleLaunch> {
        if (!request.userConfirmed) {
            return invalid(
                "RPC_MODULE_CONFIRMATION_REQUIRED",
                "模組執行需要使用者明確確認",
                retryable = false,
            )
        }
        if (request.name.isBlank()) return invalid("RPC_MODULE_NAME_INVALID", "模組名稱不可為空", retryable = false)
        val optionValues = request.options.mapValues { RpcValue.StringValue(it.value) }
        val arguments = listOf(
            RpcValue.StringValue(request.type.rpcName),
            RpcValue.StringValue(request.name),
            RpcValue.MapValue(optionValues),
        )
        return when (val response = transport.call(method, token, arguments)) {
            is AppResult.Failure -> response
            is AppResult.Success -> parseLaunch(response.value)
        }
    }

    private fun parseLaunch(value: RpcValue): AppResult<MetasploitModuleLaunch> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_MODULE_LAUNCH_INVALID", "Metasploit 執行回應格式不正確")
        val uuid = map.string("uuid")?.takeIf { it.isNotBlank() }
            ?: return invalid("RPC_MODULE_UUID_MISSING", "Metasploit 沒有回傳執行 UUID")
        val jobId = (map["job_id"] as? RpcValue.IntValue)?.value
        return AppResult.Success(MetasploitModuleLaunch(jobId = jobId, uuid = uuid))
    }

    private fun parseRunResult(value: RpcValue): AppResult<MetasploitModuleRunResult> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_MODULE_RESULT_INVALID", "Metasploit 結果格式不正確")
        val status = when (map.string("status")?.lowercase()) {
            "ready" -> MetasploitModuleRunStatus.READY
            "running" -> MetasploitModuleRunStatus.RUNNING
            "completed" -> MetasploitModuleRunStatus.COMPLETED
            "errored" -> MetasploitModuleRunStatus.ERRORED
            else -> return invalid("RPC_MODULE_STATUS_INVALID", "Metasploit 回傳未知的執行狀態")
        }
        return AppResult.Success(
            MetasploitModuleRunResult(
                status = status,
                result = map["result"],
                error = map["error"]?.displayValue(),
            ),
        )
    }

    private fun parseInfo(
        type: MetasploitModuleType,
        name: String,
        value: RpcValue,
    ): AppResult<MetasploitModuleInfo> {
        val map = value.mapOrNull()
            ?: return invalid("RPC_MODULE_INFO_INVALID", "Metasploit 模組資料格式不正確")
        val displayName = map.string("name") ?: name
        val description = map.string("description") ?: ""
        val options = (map["options"] as? RpcValue.MapValue)?.value.orEmpty().map { (optionName, raw) ->
            val option = raw.mapOrNull().orEmpty()
            MetasploitModuleOption(
                name = optionName,
                type = option.string("type") ?: "string",
                required = option.bool("required"),
                advanced = option.bool("advanced"),
                description = option.string("desc") ?: "",
                defaultValue = option["default"]?.displayValue(),
                enums = (option["enums"] as? RpcValue.ArrayValue)?.value
                    ?.mapNotNull { it.displayValue() }
                    .orEmpty(),
            )
        }.sortedWith(compareByDescending<MetasploitModuleOption> { it.required }.thenBy { it.name })
        val references = (map["references"] as? RpcValue.ArrayValue)?.value.orEmpty().mapNotNull { raw ->
            val values = (raw as? RpcValue.ArrayValue)?.value ?: return@mapNotNull null
            val referenceType = values.getOrNull(0)?.displayValue() ?: return@mapNotNull null
            val referenceValue = values.getOrNull(1)?.displayValue() ?: return@mapNotNull null
            MetasploitModuleReference(referenceType, referenceValue)
        }
        return AppResult.Success(
            MetasploitModuleInfo(
                type = type,
                name = name,
                displayName = displayName,
                description = description,
                rank = map.string("rank"),
                platforms = map.stringList("platform"),
                architectures = map.stringList("arch"),
                authors = map.stringList("authors"),
                privileged = map.bool("privileged"),
                hasCheck = map.bool("check"),
                stance = map.string("stance"),
                references = references,
                options = options,
                extraFields = map.filterKeys { it !in KNOWN_INFO_FIELDS },
            ),
        )
    }

    private fun MetasploitModuleType.listMethod(): RpcMethod = when (this) {
        MetasploitModuleType.EXPLOIT -> RpcMethod.MODULE_EXPLOITS
        MetasploitModuleType.AUXILIARY -> RpcMethod.MODULE_AUXILIARY
        MetasploitModuleType.POST -> RpcMethod.MODULE_POST
        MetasploitModuleType.PAYLOAD -> RpcMethod.MODULE_PAYLOADS
        MetasploitModuleType.ENCODER -> RpcMethod.MODULE_ENCODERS
        MetasploitModuleType.NOP -> RpcMethod.MODULE_NOPS
        MetasploitModuleType.EVASION -> RpcMethod.MODULE_EVASION
    }

    private fun RpcValue.mapOrNull(): Map<String, RpcValue>? = (this as? RpcValue.MapValue)?.value
    private fun Map<String, RpcValue>.string(key: String): String? = (this[key] as? RpcValue.StringValue)?.value
    private fun Map<String, RpcValue>.bool(key: String): Boolean = (this[key] as? RpcValue.Bool)?.value ?: false
    private fun Map<String, RpcValue>.stringList(key: String): List<String> =
        (this[key] as? RpcValue.ArrayValue)?.value?.mapNotNull { it.displayValue() }.orEmpty()

    private fun RpcValue.displayValue(): String? = when (this) {
        RpcValue.Nil -> null
        is RpcValue.Bool -> value.toString()
        is RpcValue.IntValue -> value.toString()
        is RpcValue.FloatValue -> value.toString()
        is RpcValue.StringValue -> value
        is RpcValue.BinaryValue -> null
        is RpcValue.ArrayValue -> value.mapNotNull { it.displayValue() }.joinToString(", ")
        is RpcValue.MapValue -> value.entries.joinToString(", ") { (key, item) ->
            "$key=${item.displayValue().orEmpty()}"
        }
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
        val KNOWN_INFO_FIELDS = setOf(
            "name", "fullname", "rank", "disclosuredate", "type", "author", "authors",
            "description", "license", "filepath", "arch", "platform", "privileged", "check",
            "default_options", "notes", "references", "targets", "default_target", "stance",
            "actions", "default_action", "options",
        )
    }
}
