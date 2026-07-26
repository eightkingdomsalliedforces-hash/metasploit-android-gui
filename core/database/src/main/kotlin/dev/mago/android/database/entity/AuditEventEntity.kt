package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_event",
    indices = [Index("correlationId"), Index("createdAtEpochMillis")],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val correlationId: String,
    val category: String,
    val action: String,
    val moduleName: String,
    val result: String,
    val redactedOptions: String,
    val createdAtEpochMillis: Long,
)
