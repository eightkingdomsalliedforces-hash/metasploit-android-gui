package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcSessionServiceTest {
    @Test
    fun `session list parses official optional metadata`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "7" to RpcValue.MapValue(
                        mapOf(
                            "type" to RpcValue.StringValue("meterpreter"),
                            "tunnel_local" to RpcValue.StringValue("127.0.0.1:4444"),
                            "session_host" to RpcValue.StringValue("192.0.2.10"),
                            "session_port" to RpcValue.IntValue(445),
                            "username" to RpcValue.StringValue("authorized-user"),
                            "uuid" to RpcValue.StringValue("session-uuid"),
                            "platform" to RpcValue.StringValue("windows"),
                            "future" to RpcValue.StringValue("kept"),
                        ),
                    ),
                ),
            ),
        )

        val result = RpcSessionService(transport).list("token")
        val session = (result as AppResult.Success).value.single()

        assertThat(session.id).isEqualTo(7)
        assertThat(session.type).isEqualTo("meterpreter")
        assertThat(session.sessionPort).isEqualTo(445)
        assertThat(session.extraFields).containsKey("future")
    }

    @Test
    fun `unconfirmed or oversized writes are rejected without transport`() = runTest {
        val transport = FakeTransport(successResponse())
        val service = RpcSessionService(transport)

        val unconfirmed = service.write("token", 2, "sysinfo", userConfirmed = false)
        val oversized = service.write("token", 2, "界".repeat(3000), userConfirmed = true)
        val control = service.write("token", 2, "whoami\n", userConfirmed = true)

        assertThat(unconfirmed).isInstanceOf(AppResult.Failure::class.java)
        assertThat(oversized).isInstanceOf(AppResult.Failure::class.java)
        assertThat(control).isInstanceOf(AppResult.Failure::class.java)
        assertThat(transport.callCount).isEqualTo(0)
    }

    @Test
    fun `confirmed write and manual read use official argument order`() = runTest {
        val writeTransport = FakeTransport(successResponse())
        val service = RpcSessionService(writeTransport)

        val write = service.write("token", 2, "sysinfo", userConfirmed = true)

        assertThat(write).isInstanceOf(AppResult.Success::class.java)
        assertThat(writeTransport.lastMethod).isEqualTo(RpcMethod.SESSION_INTERACTIVE_WRITE)
        assertThat(writeTransport.lastArguments).containsExactly(
            RpcValue.IntValue(2),
            RpcValue.StringValue("sysinfo"),
        ).inOrder()

        val readTransport = FakeTransport(
            RpcValue.MapValue(mapOf("data" to RpcValue.StringValue("output"))),
        )
        val read = RpcSessionService(readTransport).read("token", 2)

        assertThat((read as AppResult.Success).value.data).isEqualTo("output")
        assertThat(readTransport.lastMethod).isEqualTo(RpcMethod.SESSION_INTERACTIVE_READ)
        assertThat(readTransport.lastArguments).containsExactly(RpcValue.IntValue(2))
    }

    private fun successResponse() =
        RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("success")))

    private class FakeTransport(private val response: RpcValue) : RpcTransport {
        var callCount: Int = 0
        var lastMethod: RpcMethod? = null
        var lastArguments: List<RpcValue> = emptyList()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            callCount += 1
            lastMethod = method
            lastArguments = arguments
            return AppResult.Success(response)
        }
    }
}
