package dev.mago.android.model

import dev.mago.android.model.rpc.RpcValue

data class MetasploitJobSummary(
    val id: Int,
    val name: String,
)

data class MetasploitJobInfo(
    val id: Int,
    val name: String,
    val startTimeEpochSeconds: Long?,
    val uriPath: String?,
    val datastore: Map<String, RpcValue>,
    val extraFields: Map<String, RpcValue>,
)

data class MetasploitSessionInfo(
    val id: Int,
    val type: String,
    val tunnelLocal: String,
    val tunnelPeer: String,
    val viaExploit: String,
    val viaPayload: String,
    val description: String,
    val info: String,
    val workspace: String,
    val sessionHost: String,
    val sessionPort: Int?,
    val targetHost: String,
    val username: String,
    val uuid: String,
    val exploitUuid: String,
    val routes: List<String>,
    val architecture: String,
    val platform: String?,
    val extraFields: Map<String, RpcValue>,
)
