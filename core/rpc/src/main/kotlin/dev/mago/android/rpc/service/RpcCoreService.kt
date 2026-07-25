package dev.mago.android.rpc.service

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport

class RpcCoreService(private val transport: RpcTransport) {
    suspend fun version(token: String): AppResult<MetasploitVersion> {
        return when (val result = transport.call(RpcMethod.CORE_VERSION, token)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val map = (result.value as? RpcValue.MapValue)?.value
                if (map == null) {
                    AppResult.Failure(
                        AppError(
                            errorCode = "RPC_VERSION_RESPONSE_INVALID",
                            userMessage = "Metasploit 版本資料格式不正確",
                            retryable = true,
                        ),
                    )
                } else {
                    val framework = (map["version"] as? RpcValue.StringValue)?.value
                    if (framework.isNullOrBlank()) {
                        AppResult.Failure(
                            AppError(
                                errorCode = "RPC_VERSION_RESPONSE_INVALID",
                                userMessage = "Metasploit 版本資料缺少版本號",
                                retryable = true,
                            ),
                        )
                    } else {
                        AppResult.Success(
                            MetasploitVersion(
                                frameworkVersion = framework,
                                rubyVersion = (map["ruby"] as? RpcValue.StringValue)?.value,
                                apiVersion = (map["api"] as? RpcValue.StringValue)?.value,
                                extraFields = map.filterKeys { it !in KNOWN_FIELDS },
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val KNOWN_FIELDS = setOf("version", "ruby", "api")
    }
}
