package dev.mago.android.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.mago.android.database.entity.InstallationStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallationStateDao {
    @Query("SELECT * FROM installation_state WHERE singletonId = 1")
    fun observe(): Flow<InstallationStateEntity?>

    @Upsert
    suspend fun upsert(value: InstallationStateEntity)
}
