package dev.mago.android.installation

import dev.mago.android.common.AppResult
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse

data class TermuxEnvironment(
    val installed: Boolean,
    val runCommandPermissionGranted: Boolean,
    val packageName: String = "com.termux",
)

interface TermuxGateway {
    suspend fun inspect(): AppResult<TermuxEnvironment>
    suspend fun deployBridge(): AppResult<Unit>
    suspend fun execute(
        action: BridgeAction,
        operationId: String,
    ): AppResult<BridgeResponse>
    fun openTermux(): AppResult<Unit>
}
