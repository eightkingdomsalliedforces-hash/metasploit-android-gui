package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo
import dev.mago.android.rpc.service.RpcOperationsService
import dev.mago.android.security.RpcTokenStore

class MetasploitOperationsRepositoryImpl(
    private val service: RpcOperationsService,
    private val tokenStore: RpcTokenStore,
) : MetasploitOperationsRepository {
    constructor(
        transport: RpcTransport,
        tokenStore: RpcTokenStore,
    ) : this(
        service = RpcOperationsService(transport),
        tokenStore = tokenStore,
    )

    override suspend fun jobs(): AppResult<List<MetasploitJobSummary>> =
        token()?.let { service.jobs(it) } ?: notAuthenticated()

    override suspend fun jobInfo(jobId: Int): AppResult<MetasploitJobInfo> =
        token()?.let { service.jobInfo(it, jobId) } ?: notAuthenticated()

    override suspend fun sessions(): AppResult<List<MetasploitSessionInfo>> =
        token()?.let { service.sessions(it) } ?: notAuthenticated()

    private fun token(): String? = tokenStore.get()

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
