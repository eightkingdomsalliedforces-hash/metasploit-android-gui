package dev.mago.android.database

import android.database.sqlite.SQLiteException
import dev.mago.android.common.AppResult
import dev.mago.android.database.dao.InstallationStateDao
import dev.mago.android.installation.InstallationState
import dev.mago.android.installation.InstallationStateRepository
import dev.mago.android.model.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInstallationStateRepository(
    private val dao: InstallationStateDao,
    private val mapper: InstallationStateMapper,
) : InstallationStateRepository {
    override val state: Flow<InstallationState?> =
        dao.observe().map { entity -> entity?.let(mapper::toDomain) }

    override suspend fun save(value: InstallationState): AppResult<Unit> = try {
        dao.upsert(mapper.toEntity(value))
        AppResult.Success(Unit)
    } catch (error: SQLiteException) {
        AppResult.Failure(
            AppError(
                errorCode = "INSTALLATION_STATE_SAVE_FAILED",
                userMessage = "無法儲存安裝進度",
                technicalMessage = error.message,
                retryable = true,
            ),
        )
    }
}
