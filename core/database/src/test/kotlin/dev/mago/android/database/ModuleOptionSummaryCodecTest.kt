package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleOptionSummaryCodecTest {
    @Test
    fun `round trip preserves unicode delimiters and masked values`() {
        val values = linkedMapOf(
            "PASSWORD" to "••••••••",
            "RHOSTS" to "192.0.2.0/24",
            "NOTE" to "測試:一\n二",
        )

        val encoded = ModuleOptionSummaryCodec.encode(values)
        val decoded = ModuleOptionSummaryCodec.decode(encoded)

        assertThat(decoded).containsExactlyEntriesIn(values)
        assertThat(encoded).doesNotContain("192.0.2.0/24")
        assertThat(encoded).doesNotContain("測試")
    }
}
