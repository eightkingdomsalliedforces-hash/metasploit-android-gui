package dev.mago.android.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.mago.android.database.entity.AuditEventEntity
import dev.mago.android.database.entity.ModuleExecutionEntity
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleRecentEntity

@Dao
interface ModuleHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entry: ModuleRecentEntity)

    @Query("SELECT * FROM module_recent ORDER BY lastOpenedAtEpochMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ModuleRecentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entry: ModuleFavoriteEntity)

    @Delete
    suspend fun deleteFavorite(entry: ModuleFavoriteEntity)

    @Query("SELECT * FROM module_favorite ORDER BY createdAtEpochMillis DESC")
    suspend fun favorites(): List<ModuleFavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecution(entry: ModuleExecutionEntity)

    @Query("SELECT * FROM module_execution WHERE uuid = :uuid LIMIT 1")
    suspend fun executionByUuid(uuid: String): ModuleExecutionEntity?

    @Query("SELECT * FROM module_execution ORDER BY updatedAtEpochMillis DESC LIMIT :limit")
    suspend fun executionHistory(limit: Int): List<ModuleExecutionEntity>

    @Insert
    suspend fun insertAudit(entry: AuditEventEntity)
}
