package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.database.dao.ModuleHistoryDao
import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import dev.mago.android.metasploit.ModuleExecutionStatus
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunAction
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleRunStatus
import dev.mago.android.model.MetasploitModuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomModuleExecutionHistoryRepositoryTest {
    @Test
    fun `unmasked credential is rejected before any database write`() = runTest {
        val dao = FakeHistoryDao()
        val repository = RoomModuleExecutionHistoryRepository(
            dao = dao,
            clock = { 10 },
            idFactory = { "correlation-1" },
        )

        val result = repository.begin(
            action = MetasploitModuleRunAction.EXECUTE,
            request = request(),
            workspace = "Lab",
            redactedParameters = mapOf("PASSWORD" to "plaintext"),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(dao.executions).isEmpty()
        assertThat(dao.audits).isEmpty()
    }

    @Test
    fun `confirmed redacted execution records requested running and completed states`() = runTest {
        var now = 100L
        val dao = FakeHistoryDao()
        val repository = RoomModuleExecutionHistoryRepository(
            dao = dao,
            clock = { now++ },
            idFactory = { "correlation-1" },
        )

        val begin = repository.begin(
            action = MetasploitModuleRunAction.CHECK,
            request = request(),
            workspace = "Lab",
            redactedParameters = mapOf(
                "RHOSTS" to "192.0.2.10",
                "PASSWORD" to "••••••••",
            ),
        )
        repository.markLaunched("correlation-1", MetasploitModuleLaunch(jobId = 7, uuid = "uuid-1"))
        repository.markResult(
            "correlation-1",
            MetasploitModuleRunResult(status = MetasploitModuleRunStatus.COMPLETED),
        )

        assertThat((begin as AppResult.Success).value).isEqualTo("correlation-1")
        assertThat(dao.executions.getValue("correlation-1").status)
            .isEqualTo(ModuleExecutionStatus.COMPLETED.name)
        assertThat(dao.executions.getValue("correlation-1").jobId).isEqualTo(7)
        assertThat(dao.executions.getValue("correlation-1").uuid).isEqualTo("uuid-1")
        assertThat(dao.audits.map { it.result })
            .containsExactly("REQUESTED", "RUNNING", "COMPLETED").inOrder()
        val record = repository.observe().first().single()
        assertThat(record.redactedParameters["PASSWORD"]).isEqualTo("••••••••")
    }

    private fun request() = MetasploitModuleRequest(
        type = MetasploitModuleType.EXPLOIT,
        name = "windows/example",
        options = mapOf("RHOSTS" to "192.0.2.10"),
        userConfirmed = true,
    )
}

private class FakeHistoryDao : ModuleHistoryDao {
    val executions = linkedMapOf<String, ModuleExecutionEntity>()
    val audits = mutableListOf<AuditEventEntity>()
    private val executionFlow = MutableStateFlow<List<ModuleExecutionEntity>>(emptyList())
    private val auditFlow = MutableStateFlow<List<AuditEventEntity>>(emptyList())

    override suspend fun upsertExecution(value: ModuleExecutionEntity) {
        executions[value.correlationId] = value
        executionFlow.value = executions.values.sortedByDescending { it.createdAtEpochMillis }
    }

    override suspend fun findExecution(correlationId: String): ModuleExecutionEntity? = executions[correlationId]

    override fun observeExecutions(limit: Int): Flow<List<ModuleExecutionEntity>> = executionFlow

    override suspend fun insertAudit(value: AuditEventEntity): Long {
        val stored = value.copy(id = (audits.size + 1).toLong())
        audits += stored
        auditFlow.value = audits.toList()
        return stored.id
    }

    override fun observeAudit(limit: Int): Flow<List<AuditEventEntity>> = auditFlow
}
