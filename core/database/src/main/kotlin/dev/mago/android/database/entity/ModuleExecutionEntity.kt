package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "module_execution",
    indices = [Index("uuid"), Index("updatedAtEpochMillis")],
)
data class ModuleExecutionEntity(
    @PrimaryKey val correlationId: String,
    val action: String,
    val type: String,
    val name: String,
    val status: String,
    val jobId: Long?,
    val uuid: String?,
    val redactedOptions: String,
    val resultSummary: String?,
    val error: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
