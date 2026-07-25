package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.security.InMemoryRpcTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MetasploitConsoleRepositoryImplTest {
    @Test
    fun `write creates one console and normalizes command to CRLF`() = runTest {
        val transport = RecordingTransport()
        val tokens = InMemoryRpcTokenStore().apply { set("token") }
        val repository = MetasploitConsoleRepositoryImpl(transport, tokens)

        repository.write("version\n")
        repository.write("help")

        assertThat(transport.methods).containsExactly(
            RpcMethod.CONSOLE_CREATE,
            RpcMethod.CONSOLE_WRITE,
            RpcMethod.CONSOLE_WRITE,
        ).inOrder()
        assertThat(transport.commands).containsExactly("version\r\n", "help\r\n").inOrder()
    }

    private class RecordingTransport : RpcTransport {
        val methods = mutableListOf<RpcMethod>()
        val commands = mutableListOf<String>()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            methods += method
            if (method == RpcMethod.CONSOLE_WRITE) {
                commands += (arguments[1] as RpcValue.StringValue).value
            }
            val response = when (method) {
                RpcMethod.CONSOLE_CREATE -> RpcValue.MapValue(
                    mapOf(
                        "id" to RpcValue.StringValue("1"),
                        "prompt" to RpcValue.StringValue("msf6 > "),
                        "busy" to RpcValue.Bool(false),
                    ),
                )
                RpcMethod.CONSOLE_WRITE -> RpcValue.MapValue(mapOf("wrote" to RpcValue.IntValue(1)))
                else -> RpcValue.MapValue(emptyMap())
            }
            return AppResult.Success(response)
        }
    }
}
