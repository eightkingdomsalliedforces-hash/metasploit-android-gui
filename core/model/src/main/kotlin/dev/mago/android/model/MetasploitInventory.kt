package dev.mago.android.model

import dev.mago.android.model.rpc.RpcValue

data class MetasploitWorkspaceSummary(
    val id: Long,
    val name: String,
    val createdAtEpochSeconds: Long?,
    val updatedAtEpochSeconds: Long?,
    val extraFields: Map<String, RpcValue>,
)

data class MetasploitHostRecord(
    val address: String,
    val mac: String?,
    val name: String?,
    val state: String?,
    val operatingSystem: String?,
    val operatingSystemFlavor: String?,
    val servicePack: String?,
    val language: String?,
    val purpose: String?,
    val info: String?,
    val comments: String?,
    val createdAtEpochSeconds: Long?,
    val updatedAtEpochSeconds: Long?,
    val extraFields: Map<String, RpcValue>,
)

data class MetasploitServiceRecord(
    val host: String,
    val port: Int,
    val protocol: String,
    val state: String?,
    val name: String?,
    val info: String?,
    val createdAtEpochSeconds: Long?,
    val updatedAtEpochSeconds: Long?,
    val extraFields: Map<String, RpcValue>,
)

data class MetasploitVulnerabilityRecord(
    val host: String,
    val port: Int?,
    val protocol: String?,
    val name: String,
    val references: List<String>,
    val resource: String?,
    val reportedAtEpochSeconds: Long?,
    val extraFields: Map<String, RpcValue>,
)
