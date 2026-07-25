package dev.mago.android.termux

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import org.junit.Test

class TermuxBootstrapCommandFactoryTest {
    @Test
    fun `rejects a bridge bundle with a different digest`() {
        val result = TermuxBootstrapCommandFactory(expectedSha256 = "00".repeat(32))
            .create("changed".encodeToByteArray())

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.errorCode)
            .isEqualTo("BRIDGE_BUNDLE_DIGEST_MISMATCH")
    }
}
