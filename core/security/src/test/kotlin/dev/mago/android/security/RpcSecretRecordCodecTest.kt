package dev.mago.android.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RpcSecretRecordCodecTest {
    private val codec = RpcSecretRecordCodec()

    @Test
    fun `versioned record round trips binary data`() {
        val record = codec.decode(codec.encode(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))
        assertThat(record?.iv?.toList()).containsExactly(1, 2, 3).inOrder()
        assertThat(record?.ciphertext?.toList()).containsExactly(4, 5, 6).inOrder()
    }

    @Test
    fun `unknown or malformed record is rejected`() {
        assertThat(codec.decode("v2.a.b")).isNull()
        assertThat(codec.decode("v1.***.***")).isNull()
    }
}
