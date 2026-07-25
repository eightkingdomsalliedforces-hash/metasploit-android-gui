package dev.mago.android.model.bridge

import kotlinx.serialization.Serializable

@Serializable
data class BridgeRequest(
    val schemaVersion: Int = 1,
    val operationId: String,
    val action: BridgeAction,
    val parameters: Map<String, String> = emptyMap(),
)
