package dev.mago.android.database.entity

import androidx.room.Entity

@Entity(
    tableName = "module_favorite",
    primaryKeys = ["type", "name"],
)
data class ModuleFavoriteEntity(
    val type: String,
    val name: String,
    val createdAtEpochMillis: Long,
)
