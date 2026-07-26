package dev.mago.android.rpc

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionIoCoordinator {
    private val locks = ConcurrentHashMap<Int, Mutex>()

    suspend fun <T> withSessionLock(sessionId: Int, block: suspend () -> T): T {
        require(sessionId >= 0) { "Session ID must be non-negative" }
        val mutex = locks.computeIfAbsent(sessionId) { Mutex() }
        return mutex.withLock { block() }
    }
}
