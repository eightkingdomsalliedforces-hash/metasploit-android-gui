package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary
import dev.mago.android.rpc.service.RpcInventoryService
import dev.mago.android.security.RpcTokenStore

class MetasploitInventoryRepositoryImpl(
    private val service: RpcInventoryService,
    private val tokenStore: RpcTokenStore,
) : MetasploitInventoryRepository {
    constructor(transport: RpcTransport, tokenStore: RpcTokenStore) : this(
        service = RpcInventoryService(transport),
        tokenStore = tokenStore,
    )

    override suspend fun workspaces(): AppResult<List<MetasploitWorkspaceSummary>> =
        token()?.let { service.workspaces(it) } ?: notAuthenticated()

    override suspend fun hosts(
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitHostRecord>> =
        token()?.let { service.hosts(it, workspace, limit, offset) } ?: notAuthenticated()

    override suspend fun services(
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitServiceRecord>> =
        token()?.let { service.services(it, workspace, limit, offset) } ?: notAuthenticated()

    override suspend fun vulnerabilities(
        workspace: String,
        limit: Int,
        offset: Int,
    ): AppResult<List<MetasploitVulnerabilityRecord>> =
        token()?.let { service.vulnerabilities(it, workspace, limit, offset) } ?: notAuthenticated()

    private fun token(): String? = tokenStore.get()

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
