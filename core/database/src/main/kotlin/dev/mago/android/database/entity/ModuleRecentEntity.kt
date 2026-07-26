package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "module_recent",
    primaryKeys = ["type", "name"],
    indices = [Index("lastOpenedAtEpochMillis")],
)
data class ModuleRecentEntity(
    val type: String,
    val name: String,
    val lastOpenedAtEpochMillis: Long,
)
