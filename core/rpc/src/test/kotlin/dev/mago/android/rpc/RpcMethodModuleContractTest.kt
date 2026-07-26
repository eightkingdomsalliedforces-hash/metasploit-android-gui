package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RpcMethodModuleContractTest {
    @Test
    fun `module operation methods require authentication`() {
        val methods = listOf(
            RpcMethod.MODULE_SEARCH,
            RpcMethod.MODULE_OPTIONS,
            RpcMethod.MODULE_COMPATIBLE_PAYLOADS,
            RpcMethod.MODULE_TARGET_COMPATIBLE_PAYLOADS,
            RpcMethod.MODULE_CHECK,
            RpcMethod.MODULE_EXECUTE,
            RpcMethod.MODULE_RESULTS,
            RpcMethod.MODULE_RUNNING_STATS,
            RpcMethod.MODULE_ACK,
        )

        assertThat(methods.map(RpcMethod::value)).containsExactly(
            "module.search",
            "module.options",
            "module.compatible_payloads",
            "module.target_compatible_payloads",
            "module.check",
            "module.execute",
            "module.results",
            "module.running_stats",
            "module.ack",
        ).inOrder()
        assertThat(methods.all(RpcMethod::requiresToken)).isTrue()
    }
}
