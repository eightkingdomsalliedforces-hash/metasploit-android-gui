package dev.mago.android.termux

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalErrorCode: Int,
    val internalErrorMessage: String,
)

data class PendingTermuxCommand(
    val executionId: Int,
    val deferred: CompletableDeferred<TermuxCommandResult>,
)

object TermuxCommandResultRegistry {
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()

    fun register(): PendingTermuxCommand {
        val id = nextId.getAndUpdate { current -> if (current == Int.MAX_VALUE) 1 else current + 1 }
        val deferred = CompletableDeferred<TermuxCommandResult>()
        pending[id] = deferred
        return PendingTermuxCommand(id, deferred)
    }

    fun complete(executionId: Int, result: TermuxCommandResult) {
        pending.remove(executionId)?.complete(result)
    }

    fun remove(executionId: Int) {
        pending.remove(executionId)?.cancel()
    }
}
