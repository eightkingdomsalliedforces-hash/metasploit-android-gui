package dev.mago.android.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RpcEndpointPolicyTest {
    private val policy = RpcEndpointPolicy()

    @Test
    fun `accepts the fixed local endpoint`() {
        assertThat(policy.validate("http://127.0.0.1:55552/api")).isNotNull()
    }

    @Test
    fun `rejects hostname and non-loopback address`() {
        assertThat(policy.validate("http://localhost:55552/api")).isNull()
        assertThat(policy.validate("http://192.168.1.2:55552/api")).isNull()
        assertThat(policy.validate("https://example.com/api")).isNull()
    }

    @Test
    fun `rejects query fragments and alternate paths`() {
        assertThat(policy.validate("http://127.0.0.1:55552/api?x=1")).isNull()
        assertThat(policy.validate("http://127.0.0.1:55552/api#x")).isNull()
        assertThat(policy.validate("http://127.0.0.1:55552/api/")).isNull()
    }
}
