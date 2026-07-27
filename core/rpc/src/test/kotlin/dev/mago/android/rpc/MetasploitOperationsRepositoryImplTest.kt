package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.security.RpcTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MetasploitOperationsRepositoryImplTest {
    @Test
    fun `missing token blocks job stop before transport`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(
            transport,
            FakeTokenStore(null),
        )

        val result = repository.stopJob("4", userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.errorCode)
            .isEqualTo("RPC_NOT_AUTHENTICATED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `missing token blocks session stop before transport`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(
            transport,
            FakeTokenStore(null),
        )

        val result = repository.stopSession(7, userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.errorCode)
            .isEqualTo("RPC_NOT_AUTHENTICATED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `authenticated job stop forwards once`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(
            transport,
            FakeTokenStore("token"),
        )

        val result = repository.stopJob("4", userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
        assertThat(transport.lastToken).isEqualTo("token")
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
    }

    @Test
    fun `authenticated session stop forwards once`() = runTest {
        val transport = RecordingTransport()
        val repository = MetasploitOperationsRepositoryImpl(
            transport,
            FakeTokenStore("token"),
        )

        val result = repository.stopSession(7, userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
        assertThat(transport.lastToken).isEqualTo("token")
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(7))
    }

    private class RecordingTransport : RpcTransport {
        var calls = 0
        var lastMethod: RpcMethod? = null
        var lastToken: String? = null
        var lastArguments: List<RpcValue> = emptyList()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            calls += 1
            lastMethod = method
            lastToken = token
            lastArguments = arguments
            return AppResult.Success(
                RpcValue.MapValue(
                    mapOf("result" to RpcValue.StringValue("success")),
                ),
            )
        }
    }

    private class FakeTokenStore(private val token: String?) : RpcTokenStore {
        override fun get(): String? = token
        override fun set(token: String) = Unit
        override fun clear() = Unit
    }
}
