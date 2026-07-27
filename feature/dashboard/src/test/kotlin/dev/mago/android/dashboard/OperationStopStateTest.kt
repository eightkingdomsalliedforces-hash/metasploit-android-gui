package dev.mago.android.dashboard

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import org.junit.Test

class OperationStopStateTest {
    @Test
    fun `job target requires matching current ID`() {
        val target = OperationStopTarget.Job("2", "Example Job")

        assertThat(
            target.existsIn(
                jobs = listOf(MetasploitJobSummary("2", "Example Job")),
                sessions = emptyList(),
            ),
        ).isTrue()
        assertThat(target.existsIn(emptyList(), emptyList())).isFalse()
    }

    @Test
    fun `session target requires matching current ID`() {
        val target = OperationStopTarget.Session(7, "Meterpreter", "exploit/multi/handler")

        assertThat(target.existsIn(emptyList(), listOf(session(7)))).isTrue()
        assertThat(target.existsIn(emptyList(), listOf(session(8)))).isFalse()
    }

    private fun session(id: Int) = MetasploitSessionSummary(
        id = id,
        type = "meterpreter",
        description = "Meterpreter",
        info = "Authorized lab",
        workspace = "default",
        sessionHost = "192.0.2.10",
        sessionPort = 445,
        targetHost = null,
        username = null,
        uuid = null,
        exploitUuid = null,
        viaExploit = "exploit/multi/handler",
        viaPayload = null,
        architecture = "x64",
        platform = "windows",
        tunnelLocal = null,
        tunnelPeer = null,
        routes = emptyList(),
        extraFields = emptyMap(),
    )
}
