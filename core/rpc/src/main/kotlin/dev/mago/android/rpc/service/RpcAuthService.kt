package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcAuthService(private val transport: RpcTransport) {
    suspend fun login(username: String, password: CharArray): AppResult<String> {
        val result = transport.call(
            method = RpcMethod.AUTH_LOGIN,
            token = null,
            arguments = listOf(
                RpcValue.StringValue(username),
                RpcValue.StringValue(password.concatToString()),
            ),
        )
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val map = (result.value as? RpcValue.MapValue)?.value
                val status = (map?.get("result") as? RpcValue.StringValue)?.value
                val token = (map?.get("token") as? RpcValue.StringValue)?.value
                if (status == "success" && !token.isNullOrBlank()) {
                    AppResult.Success(token)
                } else {
                    AppResult.Failure(
                        AppError(
                            errorCode = "RPC_AUTHENTICATION_FAILED",
                            userMessage = "RPC 登入失敗",
                            technicalMessage = "Unexpected auth.login response",
                            retryable = false,
                        ),
                    )
                }
            }
        }
    }
}
