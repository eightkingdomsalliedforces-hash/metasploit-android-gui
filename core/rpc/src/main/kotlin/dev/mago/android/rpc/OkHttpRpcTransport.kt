package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.common.DefaultDispatcherProvider
import dev.mago.android.common.DispatcherProvider
import dev.mago.android.model.AppError
import dev.mago.android.model.SuggestedAction
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.security.RpcEndpointPolicy
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpRpcTransport(
    endpointPolicy: RpcEndpointPolicy,
    client: OkHttpClient,
    private val codec: MessagePackRpcCodec,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    endpointRaw: String = DEFAULT_ENDPOINT,
) : RpcTransport {
    private val endpoint: HttpUrl = requireNotNull(endpointPolicy.validate(endpointRaw)) {
        "RPC endpoint rejected by localhost policy"
    }
    private val client = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue>,
    ): AppResult<RpcValue> = withContext(dispatcherProvider.io) {
        val requestBytes = try {
            codec.encodeRequest(method, token, arguments)
        } catch (error: RpcCodecException) {
            return@withContext failure(error.errorCode, "RPC 請求格式不正確", error.message)
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBytes.toRequestBody(MESSAGE_PACK))
            .header("Accept", "binary/message-pack")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code !in setOf(200, 401, 403, 500)) {
                    return@withContext failure(
                        "RPC_HTTP_ERROR",
                        "RPC 伺服器回傳非預期狀態",
                        "HTTP ${response.code}",
                    )
                }
                val body = response.body
                    ?: return@withContext failure("RPC_EMPTY_RESPONSE", "RPC 沒有回傳內容")
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_RESPONSE_BYTES) {
                    return@withContext failure(
                        "RPC_RESPONSE_TOO_LARGE",
                        "RPC 回傳內容過大",
                        "contentLength=$declaredLength",
                    )
                }
                val bytes = body.bytes()
                if (bytes.size > MAX_RESPONSE_BYTES) {
                    return@withContext failure(
                        "RPC_RESPONSE_TOO_LARGE",
                        "RPC 回傳內容過大",
                        "actualBytes=${bytes.size}",
                    )
                }
                val decoded = try {
                    codec.decode(bytes)
                } catch (error: RpcCodecException) {
                    return@withContext failure(error.errorCode, "RPC 回傳格式無法解析", error.message)
                }
                val serverError = decoded.asServerError()
                if (serverError != null) {
                    return@withContext AppResult.Failure(serverError)
                }
                AppResult.Success(decoded)
            }
        } catch (error: SocketTimeoutException) {
            failure(
                "RPC_TIMEOUT",
                "RPC 連線逾時",
                error.message,
                retryable = true,
                action = SuggestedAction.RETRY,
            )
        } catch (error: IOException) {
            failure(
                "RPC_NETWORK_ERROR",
                "無法連接本機 RPC",
                error.message,
                retryable = true,
                action = SuggestedAction.RESTART_RPC,
            )
        }
    }

    private fun RpcValue.asServerError(): AppError? {
        val map = (this as? RpcValue.MapValue)?.value ?: return null
        val isError = (map["error"] as? RpcValue.Bool)?.value == true
        if (!isError) return null
        val code = when (val value = map["error_code"]) {
            is RpcValue.StringValue -> value.value
            is RpcValue.IntValue -> value.value.toString()
            else -> "RPC_SERVER_ERROR"
        }
        val message = (map["error_message"] as? RpcValue.StringValue)?.value
            ?: (map["error_string"] as? RpcValue.StringValue)?.value
            ?: "Metasploit RPC 回報錯誤"
        return AppError(
            errorCode = code,
            userMessage = "Metasploit RPC 執行失敗",
            technicalMessage = message,
            suggestedAction = SuggestedAction.RETRY,
            retryable = true,
        )
    }

    private fun failure(
        code: String,
        userMessage: String,
        technicalMessage: String? = null,
        retryable: Boolean = false,
        action: SuggestedAction? = null,
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = userMessage,
            technicalMessage = technicalMessage,
            suggestedAction = action,
            retryable = retryable,
        ),
    )

    private companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:55552/api"
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        val MESSAGE_PACK = "binary/message-pack".toMediaType()
    }
}
