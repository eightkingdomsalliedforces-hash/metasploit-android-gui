package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcJobServiceTest {
    @Test
    fun `list and info parse official Job shapes`() = runTest {
        val listTransport = FakeTransport(
            RpcValue.MapValue(
                linkedMapOf(
                    "2" to RpcValue.StringValue("Exploit: example/two"),
                    "0" to RpcValue.StringValue("Auxiliary: example/zero"),
                ),
            ),
        )
        val jobs = RpcJobService(listTransport).list("token")

        assertThat((jobs as AppResult.Success).value.map { it.id }).containsExactly("0", "2").inOrder()
        assertThat(listTransport.lastMethod).isEqualTo(RpcMethod.JOB_LIST)

        val infoTransport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "jid" to RpcValue.IntValue(2),
                    "name" to RpcValue.StringValue("Exploit: example/two"),
                    "start_time" to RpcValue.IntValue(1234),
                    "datastore" to RpcValue.MapValue(
                        mapOf("RHOST" to RpcValue.StringValue("192.0.2.10")),
                    ),
                    "future" to RpcValue.StringValue("kept"),
                ),
            ),
        )
        val info = RpcJobService(infoTransport).info("token", "2")
        val parsed = (info as AppResult.Success).value

        assertThat(parsed.id).isEqualTo("2")
        assertThat(parsed.startTimeEpochSeconds).isEqualTo(1234)
        assertThat(parsed.datastore["RHOST"]).isEqualTo("192.0.2.10")
        assertThat(parsed.extraFields).containsKey("future")
    }

    @Test
    fun `unconfirmed stop is rejected before transport and confirmed stop is single call`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("success"))),
        )
        val service = RpcJobService(transport)

        val rejected = service.stop("token", "3", userConfirmed = false)

        assertThat(rejected).isInstanceOf(AppResult.Failure::class.java)
        assertThat(transport.callCount).isEqualTo(0)

        val stopped = service.stop("token", "3", userConfirmed = true)

        assertThat(stopped).isInstanceOf(AppResult.Success::class.java)
        assertThat(transport.callCount).isEqualTo(1)
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.JOB_STOP)
        assertThat(transport.lastArguments).containsExactly(RpcValue.IntValue(3))
    }

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
