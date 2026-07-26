package dev.mago.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.mago.android.database.entity.ModuleCatalogEntity

@Dao
abstract class ModuleCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entries: List<ModuleCatalogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: ModuleCatalogEntity)

    @Query("DELETE FROM module_catalog WHERE type = :type")
    protected abstract suspend fun deleteType(type: String)

    @Query("SELECT * FROM module_catalog WHERE type = :type ORDER BY name")
    abstract suspend fun listByType(type: String): List<ModuleCatalogEntity>

    @Transaction
    open suspend fun replaceType(type: String, entries: List<ModuleCatalogEntity>) {
        deleteType(type)
        if (entries.isNotEmpty()) upsertAll(entries)
    }
}
