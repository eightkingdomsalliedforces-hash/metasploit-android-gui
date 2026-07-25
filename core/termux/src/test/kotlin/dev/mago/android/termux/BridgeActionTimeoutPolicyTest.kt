package dev.mago.android.termux

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.bridge.BridgeAction
import org.junit.Test

class BridgeActionTimeoutPolicyTest {
    private val policy = BridgeActionTimeoutPolicy()

    @Test
    fun `long installation actions receive extended timeouts`() {
        assertThat(policy.timeoutMillis(BridgeAction.HEALTH_CHECK)).isEqualTo(60_000L)
        assertThat(policy.timeoutMillis(BridgeAction.UPDATE_PACKAGES)).isEqualTo(45L * 60L * 1000L)
        assertThat(policy.timeoutMillis(BridgeAction.INSTALL_METASPLOIT)).isEqualTo(45L * 60L * 1000L)
        assertThat(policy.timeoutMillis(BridgeAction.INITIALIZE_DATABASE)).isEqualTo(5L * 60L * 1000L)
        assertThat(policy.timeoutMillis(BridgeAction.START_SERVICES)).isEqualTo(5L * 60L * 1000L)
    }
}
