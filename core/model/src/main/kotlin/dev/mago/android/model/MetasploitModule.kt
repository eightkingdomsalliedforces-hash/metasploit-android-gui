package dev.mago.android.model

import dev.mago.android.model.rpc.RpcValue

enum class MetasploitModuleType(val rpcName: String, val displayName: String) {
    EXPLOIT("exploit", "Exploit"),
    AUXILIARY("auxiliary", "Auxiliary"),
    POST("post", "Post"),
    PAYLOAD("payload", "Payload"),
    ENCODER("encoder", "Encoder"),
    NOP("nop", "NOP"),
    EVASION("evasion", "Evasion"),
}

data class MetasploitModuleSummary(
    val type: MetasploitModuleType,
    val name: String,
) {
    val fullName: String = "${type.rpcName}/$name"
}

data class MetasploitModuleOption(
    val name: String,
    val type: String,
    val required: Boolean,
    val advanced: Boolean,
    val description: String,
    val defaultValue: String?,
    val enums: List<String>,
)

data class MetasploitModuleReference(
    val type: String,
    val value: String,
)

data class MetasploitModuleInfo(
    val type: MetasploitModuleType,
    val name: String,
    val displayName: String,
    val description: String,
    val rank: String?,
    val platforms: List<String>,
    val architectures: List<String>,
    val authors: List<String>,
    val privileged: Boolean,
    val hasCheck: Boolean,
    val stance: String?,
    val references: List<MetasploitModuleReference>,
    val options: List<MetasploitModuleOption>,
    val extraFields: Map<String, RpcValue>,
)

enum class MetasploitModuleRunAction {
    CHECK,
    EXECUTE,
}

data class MetasploitModuleRequest(
    val type: MetasploitModuleType,
    val name: String,
    val options: Map<String, String>,
    val userConfirmed: Boolean = false,
)

data class MetasploitModuleLaunch(
    val jobId: Long?,
    val uuid: String,
)

enum class MetasploitModuleRunStatus {
    READY,
    RUNNING,
    COMPLETED,
    ERRORED,
}

data class MetasploitModuleRunResult(
    val status: MetasploitModuleRunStatus,
    val result: RpcValue? = null,
    val error: String? = null,
)
