package dev.mago.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleHistoryDao {
    @Upsert
    suspend fun upsertExecution(value: ModuleExecutionEntity)

    @Query("SELECT * FROM module_execution WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findExecution(correlationId: String): ModuleExecutionEntity?

    @Query("SELECT * FROM module_execution ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeExecutions(limit: Int): Flow<List<ModuleExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(value: AuditEventEntity): Long

    @Query("SELECT * FROM audit_event ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeAudit(limit: Int): Flow<List<AuditEventEntity>>
}
