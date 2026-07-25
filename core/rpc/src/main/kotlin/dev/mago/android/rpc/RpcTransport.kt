package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue

interface RpcTransport {
    suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue> = emptyList(),
    ): AppResult<RpcValue>
}
