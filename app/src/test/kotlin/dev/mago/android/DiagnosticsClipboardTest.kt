package dev.mago.android

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsClipboardTest {
    @Test
    fun `clipboard helper returns true when writer succeeds`() {
        assertThat(tryWriteDiagnosticsClipboard {}).isTrue()
    }

    @Test
    fun `clipboard helper returns false when writer throws`() {
        assertThat(
            tryWriteDiagnosticsClipboard { error("RAW_CLIPBOARD_EXCEPTION") },
        ).isFalse()
    }
}