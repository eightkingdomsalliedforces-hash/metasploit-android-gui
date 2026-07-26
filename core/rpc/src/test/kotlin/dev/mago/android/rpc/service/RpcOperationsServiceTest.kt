package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcOperationsServiceTest {
    @Test
    fun `job list parses numeric ids and sorts them`() = runTest {
        val transport = RecordingTransport(
            response = RpcValue.MapValue(
                linkedMapOf(
                    "9" to RpcValue.StringValue("Job Nine"),
                    "2" to RpcValue.StringValue("Job Two"),
                ),
            ),
        )
        val service = RpcOperationsService(transport)

        val result = service.jobs("token-1") as AppResult.Success

        assertThat(result.value.map { it.id }).containsExactly(2, 9).inOrder()
        assertThat(result.value.map { it.name }).containsExactly("Job Two", "Job Nine").inOrder()
        assertThat(transport.calls.single().method).isEqualTo(RpcMethod.JOB_LIST)
        assertThat(transport.calls.single().arguments).isEmpty()
    }

    @Test
    fun `job info sends integer id and preserves unknown fields`() = runTest {
        val transport = RecordingTransport(
            response = RpcValue.MapValue(
                linkedMapOf(
                    "jid" to RpcValue.IntValue(4),
                    "name" to RpcValue.StringValue("Exploit: example"),
                    "start_time" to RpcValue.IntValue(1234),
                    "datastore" to RpcValue.MapValue(
                        linkedMapOf("RHOSTS" to RpcValue.StringValue("192.0.2.10")),
                    ),
                    "future_field" to RpcValue.StringValue("future-value"),
                ),
            ),
        )
        val service = RpcOperationsService(transport)

        val result = service.jobInfo("token-1", 4) as AppResult.Success

        assertThat(result.value.id).isEqualTo(4)
        assertThat(result.value.startTimeEpochSeconds).isEqualTo(1234)
        assertThat(result.value.datastore).containsKey("RHOSTS")
        assertThat(result.value.extraFields).containsKey("future_field")
        val call = transport.calls.single()
        assertThat(call.method).isEqualTo(RpcMethod.JOB_INFO)
        assertThat(call.arguments).containsExactly(RpcValue.IntValue(4))
    }

    @Test
    fun `session list parses metadata and preserves unknown fields`() = runTest {
        val transport = RecordingTransport(
            response = RpcValue.MapValue(
                linkedMapOf(
                    "7" to RpcValue.MapValue(
                        linkedMapOf(
                            "type" to RpcValue.StringValue("meterpreter"),
                            "tunnel_local" to RpcValue.StringValue("127.0.0.1:4444"),
                            "tunnel_peer" to RpcValue.StringValue("192.0.2.10:50000"),
                            "via_exploit" to RpcValue.StringValue("exploit/windows/example"),
                            "via_payload" to RpcValue.StringValue("payload/windows/example"),
                            "desc" to RpcValue.StringValue("Meterpreter"),
                            "info" to RpcValue.StringValue("Authorized lab host"),
                            "workspace" to RpcValue.StringValue("Lab"),
                            "session_host" to RpcValue.StringValue("192.0.2.10"),
                            "session_port" to RpcValue.IntValue(445),
                            "target_host" to RpcValue.StringValue("192.0.2.10"),
                            "username" to RpcValue.StringValue("lab-user"),
                            "uuid" to RpcValue.StringValue("session-uuid"),
                            "exploit_uuid" to RpcValue.StringValue("exploit-uuid"),
                            "routes" to RpcValue.StringValue("10.0.0.0/24,10.1.0.0/24"),
                            "arch" to RpcValue.StringValue("x64"),
                            "platform" to RpcValue.StringValue("windows"),
                            "future_field" to RpcValue.StringValue("future-value"),
                        ),
                    ),
                ),
            ),
        )
        val service = RpcOperationsService(transport)

        val result = service.sessions("token-1") as AppResult.Success
        val session = result.value.single()

        assertThat(session.id).isEqualTo(7)
        assertThat(session.sessionPort).isEqualTo(445)
        assertThat(session.routes).containsExactly("10.0.0.0/24", "10.1.0.0/24").inOrder()
        assertThat(session.extraFields).containsKey("future_field")
        assertThat(transport.calls.single().method).isEqualTo(RpcMethod.SESSION_LIST)
    }
}

private data class OperationsRpcCall(
    val method: RpcMethod,
    val token: String?,
    val arguments: List<RpcValue>,
)

private class RecordingTransport(
    private val response: RpcValue,
) : RpcTransport {
    val calls = mutableListOf<OperationsRpcCall>()

    override suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue>,
    ): AppResult<RpcValue> {
        calls += OperationsRpcCall(method, token, arguments)
        return AppResult.Success(response)
    }
}
