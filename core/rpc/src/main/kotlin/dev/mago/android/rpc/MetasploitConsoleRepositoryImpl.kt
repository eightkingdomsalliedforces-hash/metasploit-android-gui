package dev.mago.android.rpc

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConsoleRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitConsoleSnapshot
import dev.mago.android.rpc.service.RpcConsoleService
import dev.mago.android.security.RpcTokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MetasploitConsoleRepositoryImpl(
    private val service: RpcConsoleService,
    private val tokenStore: RpcTokenStore,
) : MetasploitConsoleRepository {
    constructor(transport: RpcTransport, tokenStore: RpcTokenStore) : this(
        service = RpcConsoleService(transport),
        tokenStore = tokenStore,
    )

    private val ioMutex = Mutex()
    private var consoleId: String? = null

    override suspend fun ensureConsole(): AppResult<MetasploitConsoleSnapshot> = ioMutex.withLock {
        val token = tokenStore.get() ?: return@withLock notAuthenticated()
        val existing = consoleId
        if (existing != null) return@withLock service.read(token, existing)
        when (val created = service.create(token)) {
            is AppResult.Failure -> created
            is AppResult.Success -> {
                consoleId = created.value.id
                created
            }
        }
    }

    override suspend fun read(): AppResult<MetasploitConsoleSnapshot> = ioMutex.withLock {
        val token = tokenStore.get() ?: return@withLock notAuthenticated()
        val id = consoleId ?: return@withLock when (val created = service.create(token)) {
            is AppResult.Failure -> created
            is AppResult.Success -> {
                consoleId = created.value.id
                created
            }
        }
        service.read(token, id)
    }

    override suspend fun write(command: String): AppResult<Unit> = ioMutex.withLock {
        if (command.isBlank()) return@withLock AppResult.Success(Unit)
        val token = tokenStore.get() ?: return@withLock notAuthenticated()
        val id = consoleId ?: when (val created = service.create(token)) {
            is AppResult.Failure -> return@withLock created
            is AppResult.Success -> created.value.id.also { consoleId = it }
        }
        service.write(token, id, command.normalizeConsoleCommand())
    }

    override suspend fun destroy(): AppResult<Unit> = ioMutex.withLock {
        val id = consoleId ?: return@withLock AppResult.Success(Unit)
        val token = tokenStore.get() ?: return@withLock notAuthenticated()
        when (val result = service.destroy(token, id)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                consoleId = null
                result
            }
        }
    }

    internal fun String.normalizeConsoleCommand(): String = trimEnd('\r', '\n') + "\r\n"

    private fun <T> notAuthenticated(): AppResult<T> = AppResult.Failure(
        AppError(
            errorCode = "RPC_NOT_AUTHENTICATED",
            userMessage = "Metasploit RPC 尚未連線",
            retryable = true,
        ),
    )
}
