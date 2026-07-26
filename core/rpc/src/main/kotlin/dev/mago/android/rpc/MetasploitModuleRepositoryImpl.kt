package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
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

    override suspend fun search(query: String): AppResult<List<MetasploitModuleSummary>> =
        token()?.let { service.search(it, query) } ?: notAuthenticated()

    override suspend fun info(
        type: MetasploitModuleType,
        name: String,
    ): AppResult<MetasploitModuleInfo> = token()?.let { service.info(it, type, name) } ?: notAuthenticated()

    override suspend fun compatiblePayloads(
        type: MetasploitModuleType,
        name: String,
    ): AppResult<List<String>> = token()?.let { service.compatiblePayloads(it, type, name) } ?: notAuthenticated()

    override suspend fun compatiblePayloads(
        type: MetasploitModuleType,
        name: String,
        target: Int,
    ): AppResult<List<String>> = token()?.let {
        service.compatiblePayloads(it, type, name, target)
    } ?: notAuthenticated()

    override suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        token()?.let { service.check(it, request) } ?: notAuthenticated()

    override suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch> =
        token()?.let { service.execute(it, request) } ?: notAuthenticated()

    override suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult> =
        token()?.let { service.result(it, uuid) } ?: notAuthenticated()

    private fun token(): String? = tokenStore.get()

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
