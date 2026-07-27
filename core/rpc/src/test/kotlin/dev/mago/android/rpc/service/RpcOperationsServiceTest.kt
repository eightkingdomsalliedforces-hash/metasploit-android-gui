package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcOperationsServiceTest {
    @Test
    fun `jobs parses IDs and names in numeric order`() = runTest {
        val service = RpcOperationsService(
            FakeTransport(
                RpcValue.MapValue(
                    linkedMapOf(
                        "10" to RpcValue.StringValue("Later"),
                        "2" to RpcValue.StringValue("Earlier"),
                    ),
                ),
            ),
        )

        val result = service.jobs("token")

        val jobs = (result as AppResult.Success).value
        assertThat(jobs.map { it.id }).containsExactly("2", "10").inOrder()
    }

    @Test
    fun `job info sends integer ID and preserves unknown fields`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "jid" to RpcValue.IntValue(4),
                    "name" to RpcValue.StringValue("Example Job"),
                    "start_time" to RpcValue.IntValue(100),
                    "datastore" to RpcValue.MapValue(
                        mapOf(
                            "RHOSTS" to RpcValue.StringValue("192.0.2.10"),
                            "PASSWORD" to RpcValue.StringValue("plain-secret"),
                        ),
                    ),
                    "future_field" to RpcValue.StringValue("kept"),
                ),
            ),
        )
        val service = RpcOperationsService(transport)

        val result = service.jobInfo("token", "4")

        val info = (result as AppResult.Success).value
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_INFO)
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
        assertThat(info.name).isEqualTo("Example Job")
        assertThat(info.datastore["RHOSTS"]).isEqualTo("192.0.2.10")
        assertThat(info.datastore["PASSWORD"]).isEqualTo("••••••••")
        assertThat(info.datastore.values).doesNotContain("plain-secret")
        assertThat(info.extraFields).containsKey("future_field")
    }

    @Test
    fun `sessions parses official fields and preserves unknown fields`() = runTest {
        val service = RpcOperationsService(
            FakeTransport(
                RpcValue.MapValue(
                    mapOf(
                        "7" to RpcValue.MapValue(
                            mapOf(
                                "type" to RpcValue.StringValue("meterpreter"),
                                "desc" to RpcValue.StringValue("Meterpreter"),
                                "info" to RpcValue.StringValue("Authorized lab"),
                                "workspace" to RpcValue.StringValue("default"),
                                "session_host" to RpcValue.StringValue("192.0.2.10"),
                                "session_port" to RpcValue.IntValue(445),
                                "routes" to RpcValue.StringValue("10.0.0.0/8, 192.168.0.0/16"),
                                "future_field" to RpcValue.StringValue("kept"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = service.sessions("token")

        val session = (result as AppResult.Success).value.single()
        assertThat(session.id).isEqualTo(7)
        assertThat(session.type).isEqualTo("meterpreter")
        assertThat(session.routes).containsExactly("10.0.0.0/8", "192.168.0.0/16").inOrder()
        assertThat(session.extraFields).containsKey("future_field")
    }

    @Test
    fun `unconfirmed job stop performs zero transport calls`() = runTest {
        val transport = FakeTransport(successResponse())

        val result = RpcOperationsService(transport)
            .stopJob("token", "4", userConfirmed = false)

        assertFailureCode(result, "RPC_JOB_CONFIRMATION_REQUIRED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `invalid job IDs perform zero transport calls`() = runTest {
        listOf("not-a-number", "-1", "9223372036854775808").forEach { id ->
            val transport = FakeTransport(successResponse())

            val result = RpcOperationsService(transport)
                .stopJob("token", id, userConfirmed = true)

            assertFailureCode(result, "RPC_JOB_ID_INVALID")
            assertThat(transport.calls).isEqualTo(0)
        }
    }

    @Test
    fun `unconfirmed session stop performs zero transport calls`() = runTest {
        val transport = FakeTransport(successResponse())

        val result = RpcOperationsService(transport)
            .stopSession("token", 7, userConfirmed = false)

        assertFailureCode(result, "RPC_SESSION_CONFIRMATION_REQUIRED")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `negative session ID performs zero transport calls`() = runTest {
        val transport = FakeTransport(successResponse())

        val result = RpcOperationsService(transport)
            .stopSession("token", -1, userConfirmed = true)

        assertFailureCode(result, "RPC_SESSION_ID_INVALID")
        assertThat(transport.calls).isEqualTo(0)
    }

    @Test
    fun `job stop sends one integer argument and accepts case insensitive success`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("SuCcEsS"))),
        )

        val result = RpcOperationsService(transport)
            .stopJob("token", "4", userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
        assertThat(transport.lastToken).isEqualTo("token")
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(4))
    }

    @Test
    fun `session stop sends one integer argument`() = runTest {
        val transport = FakeTransport(successResponse())

        val result = RpcOperationsService(transport)
            .stopSession("token", 7, userConfirmed = true)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.calls).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.SESSION_STOP)
        assertThat(transport.lastToken).isEqualTo("token")
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(7))
    }

    @Test
    fun `malformed stop responses fail closed`() = runTest {
        val malformed = listOf(
            RpcValue.StringValue("success"),
            RpcValue.MapValue(emptyMap()),
            RpcValue.MapValue(mapOf("result" to RpcValue.IntValue(1))),
        )

        malformed.forEach { response ->
            assertFailureCode(
                RpcOperationsService(FakeTransport(response))
                    .stopJob("token", "4", userConfirmed = true),
                "RPC_JOB_STOP_RESPONSE_INVALID",
            )
            assertFailureCode(
                RpcOperationsService(FakeTransport(response))
                    .stopSession("token", 7, userConfirmed = true),
                "RPC_SESSION_STOP_RESPONSE_INVALID",
            )
        }
    }

    @Test
    fun `non-success result fails without retry`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("failed"))),
        )

        val result = RpcOperationsService(transport)
            .stopJob("token", "4", userConfirmed = true)

        assertFailureCode(result, "RPC_JOB_STOP_FAILED")
        assertThat(transport.calls).isEqualTo(1)
    }

    @Test
    fun `transport failure is propagated without retry`() = runTest {
        val expected = AppResult.Failure(
            AppError(
                errorCode = "RPC_NETWORK_ERROR",
                userMessage = "無法連接本機 RPC",
            ),
        )
        val transport = FakeTransport(expected)

        val result = RpcOperationsService(transport)
            .stopSession("token", 7, userConfirmed = true)

        assertThat(result).isSameInstanceAs(expected)
        assertThat(transport.calls).isEqualTo(1)
    }

    private fun successResponse(): RpcValue = RpcValue.MapValue(
        mapOf("result" to RpcValue.StringValue("success")),
    )

    private fun assertFailureCode(result: AppResult<*>, code: String) {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.errorCode).isEqualTo(code)
    }

    private class FakeTransport(
        private val response: AppResult<RpcValue>,
    ) : RpcTransport {
        constructor(response: RpcValue) : this(AppResult.Success(response))

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
            return response
        }
    }
}
