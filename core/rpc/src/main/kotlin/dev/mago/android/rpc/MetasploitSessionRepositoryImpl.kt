package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitSessionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitSessionRead
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.rpc.service.RpcSessionService
import dev.mago.android.security.RpcTokenStore

class MetasploitSessionRepositoryImpl(
    private val service: RpcSessionService,
    private val tokenStore: RpcTokenStore,
    private val ioCoordinator: SessionIoCoordinator,
) : MetasploitSessionRepository {
    constructor(
        transport: RpcTransport,
        tokenStore: RpcTokenStore,
        ioCoordinator: SessionIoCoordinator = SessionIoCoordinator(),
    ) : this(
        service = RpcSessionService(transport),
        tokenStore = tokenStore,
        ioCoordinator = ioCoordinator,
    )

    override suspend fun list(): AppResult<List<MetasploitSessionSummary>> =
        withToken { token -> service.list(token) }

    override suspend fun stop(id: Int, userConfirmed: Boolean): AppResult<Unit> =
        withToken { token ->
            ioCoordinator.withSessionLock(id) { service.stop(token, id, userConfirmed) }
        }

    override suspend fun read(id: Int): AppResult<MetasploitSessionRead> =
        withToken { token ->
            ioCoordinator.withSessionLock(id) { service.read(token, id) }
        }

    override suspend fun write(id: Int, input: String, userConfirmed: Boolean): AppResult<Unit> =
        withToken { token ->
            ioCoordinator.withSessionLock(id) { service.write(token, id, input, userConfirmed) }
        }

    private suspend fun <T> withToken(block: suspend (String) -> AppResult<T>): AppResult<T> {
        val token = tokenStore.get() ?: return notAuthenticated()
        return block(token)
    }

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
