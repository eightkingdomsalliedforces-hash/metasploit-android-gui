package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcModuleServiceTest {
    @Test
    fun `module info parses required options and preserves unknown fields`() = runTest {
        val response = RpcValue.MapValue(
            mapOf(
                "name" to RpcValue.StringValue("Example Module"),
                "description" to RpcValue.StringValue("Description"),
                "rank" to RpcValue.StringValue("Excellent"),
                "check" to RpcValue.Bool(true),
                "arch" to RpcValue.ArrayValue(listOf(RpcValue.StringValue("x64"))),
                "platform" to RpcValue.ArrayValue(listOf(RpcValue.StringValue("Windows"))),
                "authors" to RpcValue.ArrayValue(listOf(RpcValue.StringValue("Author"))),
                "options" to RpcValue.MapValue(
                    mapOf(
                        "RHOSTS" to RpcValue.MapValue(
                            mapOf(
                                "type" to RpcValue.StringValue("address_range"),
                                "required" to RpcValue.Bool(true),
                                "advanced" to RpcValue.Bool(false),
                                "desc" to RpcValue.StringValue("Targets"),
                            ),
                        ),
                    ),
                ),
                "future_field" to RpcValue.StringValue("kept"),
            ),
        )
        val service = RpcModuleService(FakeTransport(response))

        val result = service.info("token", MetasploitModuleType.EXPLOIT, "windows/example")

        val info = (result as AppResult.Success).value
        assertThat(info.displayName).isEqualTo("Example Module")
        assertThat(info.options.single().name).isEqualTo("RHOSTS")
        assertThat(info.options.single().required).isTrue()
        assertThat(info.extraFields).containsKey("future_field")
    }

    @Test
    fun `execute sends type name and option map in official argument order`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "job_id" to RpcValue.IntValue(17),
                    "uuid" to RpcValue.StringValue("run-uuid"),
                ),
            ),
        )
        val service = RpcModuleService(transport)

        val result = service.execute(
            "token",
            MetasploitModuleRequest(
                type = MetasploitModuleType.EXPLOIT,
                name = "multi/handler",
                options = linkedMapOf("LHOST" to "127.0.0.1", "LPORT" to "4444"),
            ),
        )

        val launch = (result as AppResult.Success).value
        assertThat(launch.jobId).isEqualTo(17)
        assertThat(launch.uuid).isEqualTo("run-uuid")
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.MODULE_EXECUTE)
        assertThat(transport.lastArguments[0]).isEqualTo(RpcValue.StringValue("exploit"))
        assertThat(transport.lastArguments[1]).isEqualTo(RpcValue.StringValue("multi/handler"))
        val options = (transport.lastArguments[2] as RpcValue.MapValue).value
        assertThat(options["LHOST"]).isEqualTo(RpcValue.StringValue("127.0.0.1"))
        assertThat(options["LPORT"]).isEqualTo(RpcValue.StringValue("4444"))
    }

    @Test
    fun `compatible payloads are sorted and result status preserves payload`() = runTest {
        val payloadTransport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "payloads" to RpcValue.ArrayValue(
                        listOf(
                            RpcValue.StringValue("windows/x64/z"),
                            RpcValue.StringValue("windows/x64/a"),
                        ),
                    ),
                ),
            ),
        )
        val service = RpcModuleService(payloadTransport)
        val payloads = service.compatiblePayloads("token", MetasploitModuleType.EXPLOIT, "windows/example")

        assertThat((payloads as AppResult.Success).value)
            .containsExactly("windows/x64/a", "windows/x64/z")
            .inOrder()
        assertThat(payloadTransport.lastMethod).isEqualTo(RpcMethod.MODULE_COMPATIBLE_PAYLOADS)

        val resultService = RpcModuleService(
            FakeTransport(
                RpcValue.MapValue(
                    mapOf(
                        "status" to RpcValue.StringValue("completed"),
                        "result" to RpcValue.StringValue("safe-result"),
                    ),
                ),
            ),
        )
        val runResult = resultService.result("token", "run-uuid")
        val parsed = (runResult as AppResult.Success).value
        assertThat(parsed.status).isEqualTo(MetasploitModuleRunStatus.COMPLETED)
        assertThat(parsed.result).isEqualTo(RpcValue.StringValue("safe-result"))
    }

    private class FakeTransport(private val response: RpcValue) : RpcTransport {
        var lastMethod: RpcMethod? = null
        var lastArguments: List<RpcValue> = emptyList()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            lastMethod = method
            lastArguments = arguments
            return AppResult.Success(response)
        }
    }
}
