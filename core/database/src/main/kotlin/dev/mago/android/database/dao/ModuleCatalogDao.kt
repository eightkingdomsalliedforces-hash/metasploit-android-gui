package dev.mago.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.mago.android.database.entity.ModuleFavoriteEntity
import dev.mago.android.database.entity.ModuleIndexEntity
import dev.mago.android.database.entity.ModuleRecentEntity
import dev.mago.android.database.entity.ModuleSearchFtsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleCatalogDao {
    @Query("SELECT * FROM module_index WHERE type = :type ORDER BY name LIMIT :limit")
    suspend fun listByType(type: String, limit: Int): List<ModuleIndexEntity>

    @Query(
        """
        SELECT module_index.* FROM module_index
        INNER JOIN module_search_fts
          ON module_index.type = module_search_fts.type
         AND module_index.name = module_search_fts.name
        WHERE module_search_fts MATCH :matchQuery
          AND (:type IS NULL OR module_index.type = :type)
        ORDER BY module_index.name
        LIMIT :limit
        """,
    )
    suspend fun search(matchQuery: String, type: String?, limit: Int): List<ModuleIndexEntity>

    @Query("SELECT * FROM module_index WHERE type = :type AND name = :name LIMIT 1")
    suspend fun find(type: String, name: String): ModuleIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndex(values: List<ModuleIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(values: List<ModuleSearchFtsEntity>)

    @Query("DELETE FROM module_search_fts WHERE type = :type")
    suspend fun deleteSearchByType(type: String)

    @Query("DELETE FROM module_index WHERE type = :type")
    suspend fun deleteIndexByType(type: String)

    @Transaction
    suspend fun replaceType(
        type: String,
        indexValues: List<ModuleIndexEntity>,
        searchValues: List<ModuleSearchFtsEntity>,
    ) {
        deleteSearchByType(type)
        deleteIndexByType(type)
        if (indexValues.isNotEmpty()) insertIndex(indexValues)
        if (searchValues.isNotEmpty()) insertSearch(searchValues)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM module_favorite WHERE type = :type AND name = :name)")
    suspend fun isFavorite(type: String, name: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(value: ModuleFavoriteEntity)

    @Query("DELETE FROM module_favorite WHERE type = :type AND name = :name")
    suspend fun removeFavorite(type: String, name: String)

    @Query("SELECT * FROM module_favorite ORDER BY createdAtEpochMillis DESC")
    fun observeFavorites(): Flow<List<ModuleFavoriteEntity>>

    @Upsert
    suspend fun upsertRecent(value: ModuleRecentEntity)

    @Query("SELECT * FROM module_recent ORDER BY lastOpenedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ModuleRecentEntity>>
}
