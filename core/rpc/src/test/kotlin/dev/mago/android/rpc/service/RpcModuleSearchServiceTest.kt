package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleType
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcModuleSearchServiceTest {
    @Test
    fun `search parses official module metadata and preserves unknown fields`() = runTest {
        val transport = RecordingTransport(
            RpcValue.ArrayValue(
                listOf(
                    RpcValue.MapValue(
                        mapOf(
                            "type" to RpcValue.StringValue("exploit"),
                            "name" to RpcValue.StringValue("Example SMB Module"),
                            "fullname" to RpcValue.StringValue("exploit/windows/smb/example"),
                            "rank" to RpcValue.StringValue("excellent"),
                            "disclosuredate" to RpcValue.StringValue("2026-01-02"),
                            "future_field" to RpcValue.StringValue("preserved"),
                        ),
                    ),
                ),
            ),
        )
        val service = RpcModuleService(transport)

        val result = service.search("token", "smb type:exploit")

        val module = (result as AppResult.Success).value.single()
        assertThat(module.type).isEqualTo(MetasploitModuleType.EXPLOIT)
        assertThat(module.name).isEqualTo("windows/smb/example")
        assertThat(module.displayName).isEqualTo("Example SMB Module")
        assertThat(module.rank).isEqualTo("excellent")
        assertThat(module.disclosureDate).isEqualTo("2026-01-02")
        assertThat(module.extraFields).containsEntry("future_field", RpcValue.StringValue("preserved"))
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.MODULE_SEARCH)
        assertThat(transport.lastArguments).containsExactly(RpcValue.StringValue("smb type:exploit"))
    }

    @Test
    fun `target compatible payloads use official method and argument order`() = runTest {
        val transport = RecordingTransport(
            RpcValue.MapValue(
                mapOf(
                    "payloads" to RpcValue.ArrayValue(
                        listOf(RpcValue.StringValue("windows/x64/example")),
                    ),
                ),
            ),
        )
        val service = RpcModuleService(transport)

        val result = service.compatiblePayloads(
            token = "token",
            type = MetasploitModuleType.EXPLOIT,
            name = "windows/smb/example",
            target = 2,
        )

        assertThat((result as AppResult.Success).value).containsExactly("windows/x64/example")
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.MODULE_TARGET_COMPATIBLE_PAYLOADS)
        assertThat(transport.lastArguments).containsExactly(
            RpcValue.StringValue("windows/smb/example"),
            RpcValue.IntValue(2),
        ).inOrder()
    }

    private class RecordingTransport(private val response: RpcValue) : RpcTransport {
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
