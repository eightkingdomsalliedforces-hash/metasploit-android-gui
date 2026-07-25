package dev.mago.android.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RpcSecretRecordCodecTest {
    @Test
    fun `round trips versioned encrypted record`() {
        val record = RpcSecretRecord(ByteArray(12) { it.toByte() }, byteArrayOf(4, 5, 6, 7))
        val decoded = RpcSecretRecordCodec.decode(RpcSecretRecordCodec.encode(record))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.iv).isEqualTo(record.iv)
        assertThat(decoded.ciphertext).isEqualTo(record.ciphertext)
    }

    @Test
    fun `rejects unknown version and malformed data`() {
        assertThat(RpcSecretRecordCodec.decode("v2.AA==.AA==")).isNull()
        assertThat(RpcSecretRecordCodec.decode("v1.not-base64.bad")).isNull()
        assertThat(RpcSecretRecordCodec.decode("v1.AA==.AA==")).isNull()
    }
}
