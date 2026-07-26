package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "module_catalog",
    primaryKeys = ["type", "name"],
    indices = [Index("type")],
)
data class ModuleCatalogEntity(
    val type: String,
    val name: String,
    val displayName: String,
    val description: String,
    val rank: String?,
    val refreshedAtEpochMillis: Long,
)
