package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionIoCoordinatorTest {
    @Test
    fun `operations for the same Session ID never overlap`() = runTest {
        val coordinator = SessionIoCoordinator()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)

        suspend fun guardedOperation() = coordinator.withSessionLock(7) {
            val now = active.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, now) }
            delay(10)
            active.decrementAndGet()
        }

        val first = async { guardedOperation() }
        val second = async { guardedOperation() }
        first.await()
        second.await()

        assertThat(maximum.get()).isEqualTo(1)
    }
}
