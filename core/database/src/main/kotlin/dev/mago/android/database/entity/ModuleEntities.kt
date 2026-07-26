package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "module_index",
    primaryKeys = ["type", "name"],
    indices = [Index(value = ["refreshedAtEpochMillis"])],
)
data class ModuleIndexEntity(
    val type: String,
    val name: String,
    val displayName: String,
    val description: String,
    val rank: String?,
    val platformsText: String,
    val architecturesText: String,
    val authorsText: String,
    val refreshedAtEpochMillis: Long,
)

@Fts4
@Entity(tableName = "module_search_fts")
data class ModuleSearchFtsEntity(
    val type: String,
    val name: String,
    val displayName: String,
    val description: String,
    val platformsText: String,
    val architecturesText: String,
    val authorsText: String,
)

@Entity(tableName = "module_favorite", primaryKeys = ["type", "name"])
data class ModuleFavoriteEntity(
    val type: String,
    val name: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "module_recent",
    primaryKeys = ["type", "name"],
    indices = [Index(value = ["lastOpenedAtEpochMillis"])],
)
data class ModuleRecentEntity(
    val type: String,
    val name: String,
    val lastOpenedAtEpochMillis: Long,
)

@Entity(
    tableName = "module_execution",
    indices = [
        Index(value = ["createdAtEpochMillis"]),
        Index(value = ["uuid"]),
    ],
)
data class ModuleExecutionEntity(
    @PrimaryKey val correlationId: String,
    val action: String,
    val type: String,
    val name: String,
    val workspace: String?,
    val status: String,
    val jobId: Long?,
    val uuid: String?,
    val redactedParameters: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "audit_event",
    indices = [
        Index(value = ["correlationId"]),
        Index(value = ["createdAtEpochMillis"]),
    ],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val correlationId: String,
    val category: String,
    val action: String,
    val moduleName: String?,
    val workspace: String?,
    val result: String,
    val redactedParameters: String,
    val createdAtEpochMillis: Long,
)
