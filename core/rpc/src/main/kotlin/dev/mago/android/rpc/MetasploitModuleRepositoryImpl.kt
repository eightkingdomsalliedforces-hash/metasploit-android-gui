package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.rpc.service.RpcModuleService
import dev.mago.android.security.RpcTokenStore

class MetasploitModuleRepositoryImpl(
    private val service: RpcModuleService,
    private val tokenStore: RpcTokenStore,
) : MetasploitModuleRepository {
    constructor(transport: RpcTransport, tokenStore: RpcTokenStore) : this(
        service = RpcModuleService(transport),
        tokenStore = tokenStore,
    )

    override suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>> =
        token()?.let { service.list(it, type) } ?: notAuthenticated()

    override suspend fun info(
        type: MetasploitModuleType,
        name: String,
    ): AppResult<MetasploitModuleInfo> = token()?.let { service.info(it, type, name) } ?: notAuthenticated()

    private fun token(): String? = tokenStore.get()

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
