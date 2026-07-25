package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
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

    private class FakeTransport(private val response: RpcValue) : RpcTransport {
        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> = AppResult.Success(response)
    }
}
