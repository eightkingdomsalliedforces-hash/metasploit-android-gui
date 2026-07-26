package dev.mago.android.reporting

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Test

class ReportStreamWriterTest {
    @Test
    fun `writes exact bytes and does not close caller owned stream`() {
        val output = TrackingOutputStream()
        val document = ReportDocument(
            id = "id",
            format = ReportFormat.JSON,
            fileName = "report.json",
            mimeType = "application/json",
            bytes = byteArrayOf(0, 1, 2, 3, 127),
        )

        ReportStreamWriter().write(output, document)

        assertThat(output.toByteArray().asList()).containsExactly(
            0.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 127.toByte(),
        ).inOrder()
        assertThat(output.flushed).isTrue()
        assertThat(output.closed).isFalse()
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var flushed = false
        var closed = false

        override fun flush() {
            flushed = true
            super.flush()
        }

        override fun close() {
            closed = true
            super.close()
        }
    }
}
