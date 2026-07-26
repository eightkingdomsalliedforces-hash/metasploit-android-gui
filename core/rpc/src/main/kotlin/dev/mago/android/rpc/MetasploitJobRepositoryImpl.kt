package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitJobRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.rpc.service.RpcJobService
import dev.mago.android.security.RpcTokenStore

class MetasploitJobRepositoryImpl(
    private val service: RpcJobService,
    private val tokenStore: RpcTokenStore,
) : MetasploitJobRepository {
    constructor(transport: RpcTransport, tokenStore: RpcTokenStore) : this(
        service = RpcJobService(transport),
        tokenStore = tokenStore,
    )

    override suspend fun list(): AppResult<List<MetasploitJobSummary>> =
        token()?.let { service.list(it) } ?: notAuthenticated()

    override suspend fun info(id: String): AppResult<MetasploitJobInfo> =
        token()?.let { service.info(it, id) } ?: notAuthenticated()

    override suspend fun stop(id: String, userConfirmed: Boolean): AppResult<Unit> =
        token()?.let { service.stop(it, id, userConfirmed) } ?: notAuthenticated()

    private fun token(): String? = tokenStore.get()

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
