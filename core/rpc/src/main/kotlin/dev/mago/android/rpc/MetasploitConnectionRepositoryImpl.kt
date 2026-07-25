package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.ServiceStatus
import dev.mago.android.rpc.service.RpcAuthService
import dev.mago.android.rpc.service.RpcCoreService
import dev.mago.android.security.RpcTokenStore
import dev.mago.android.security.SecretStore

class MetasploitConnectionRepositoryImpl(
    private val authService: RpcAuthService,
    private val coreService: RpcCoreService,
    private val secretStore: SecretStore,
    private val tokenStore: RpcTokenStore,
) : MetasploitConnectionRepository {
    constructor(
        transport: RpcTransport,
        secretStore: SecretStore,
        tokenStore: RpcTokenStore,
    ) : this(
        authService = RpcAuthService(transport),
        coreService = RpcCoreService(transport),
        secretStore = secretStore,
        tokenStore = tokenStore,
    )

    override suspend fun login(username: String): AppResult<Unit> {
        val passwordResult = secretStore.readRpcPassword()
        if (passwordResult is AppResult.Failure) {
            tokenStore.clear()
            return passwordResult
        }
        val password = (passwordResult as AppResult.Success).value
        if (password == null || password.isEmpty()) {
            tokenStore.clear()
            password?.fill('\u0000')
            return credentialsNotConfigured()
        }

        return try {
            when (val auth = authService.login(username, password)) {
                is AppResult.Failure -> {
                    tokenStore.clear()
                    auth
                }
                is AppResult.Success -> {
                    tokenStore.set(auth.value)
                    AppResult.Success(Unit)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun version(): AppResult<MetasploitVersion> {
        val token = tokenStore.get()
            ?: return AppResult.Failure(
                AppError(
                    errorCode = "RPC_NOT_AUTHENTICATED",
                    userMessage = "尚未登入 Metasploit RPC",
                    retryable = false,
                ),
            )
        return coreService.version(token)
    }

    override suspend fun health(): AppResult<ServiceStatus> {
        if (tokenStore.get() == null) {
            when (val login = login()) {
                is AppResult.Failure -> return login
                is AppResult.Success -> Unit
            }
        }
        return when (val version = version()) {
            is AppResult.Failure -> version
            is AppResult.Success -> AppResult.Success(ServiceStatus.RUNNING)
        }
    }

    override fun logout() {
        tokenStore.clear()
    }

    private fun credentialsNotConfigured(): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = "RPC_CREDENTIALS_NOT_CONFIGURED",
            userMessage = "RPC 尚未完成設定",
            retryable = false,
        ),
    )
}
