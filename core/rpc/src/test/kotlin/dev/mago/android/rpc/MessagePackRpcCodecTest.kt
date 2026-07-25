package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.rpc.RpcValue
import org.junit.Test

class MessagePackRpcCodecTest {
    private val codec = MessagePackRpcCodec()

    @Test
    fun `decodes core version fixture and preserves unknown fields`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/core-version-response.msgpack")!!.readBytes()
        val value = codec.decode(bytes) as RpcValue.MapValue
        assertThat((value.value["version"] as RpcValue.StringValue).value).isNotEmpty()
        assertThat(value.value).containsKey("ruby")
        assertThat(value.value).containsKey("future_field")
    }

    @Test
    fun `encodes authenticated request with method before token`() {
        val bytes = codec.encodeRequest(
            method = RpcMethod.CORE_VERSION,
            token = "token-1",
            arguments = emptyList(),
        )
        val decoded = codec.decode(bytes) as RpcValue.ArrayValue
        assertThat((decoded.value[0] as RpcValue.StringValue).value).isEqualTo("core.version")
        assertThat((decoded.value[1] as RpcValue.StringValue).value).isEqualTo("token-1")
    }
}
