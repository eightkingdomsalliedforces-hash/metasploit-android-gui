package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.metasploit.ModuleExecutionRecord
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import org.junit.Test

class ModuleDatabaseMapperTest {
    @Test
    fun `execution round trip keeps redacted summary and identifiers`() {
        val mapper = ModuleDatabaseMapper()
        val record = ModuleExecutionRecord(
            correlationId = "corr-1",
            action = MetasploitModuleRunAction.CHECK,
            type = MetasploitModuleType.EXPLOIT,
            name = "windows/example",
            status = MetasploitModuleRunStatus.RUNNING,
            jobId = 7,
            uuid = "uuid-1",
            redactedOptions = mapOf("PASSWORD" to "••••••••", "RHOSTS" to "192.0.2.10"),
            resultSummary = null,
            error = null,
            createdAtEpochMillis = 10,
            updatedAtEpochMillis = 20,
        )

        val restored = mapper.execution(mapper.execution(record))

        assertThat(restored).isEqualTo(record)
    }
}
