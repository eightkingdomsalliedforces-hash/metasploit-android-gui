package dev.mago.android.model.bridge

import kotlinx.serialization.Serializable

@Serializable
data class BridgeResponse(
    val schemaVersion: Int,
    val operationId: String,
    val action: BridgeAction,
    val success: Boolean,
    val exitCode: Int,
    val message: String,
    val progress: Int,
    val data: Map<String, String> = emptyMap(),
)
