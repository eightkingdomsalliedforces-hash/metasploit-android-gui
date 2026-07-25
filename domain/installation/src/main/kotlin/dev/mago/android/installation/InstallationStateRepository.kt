package dev.mago.android.installation

import dev.mago.android.common.AppResult
import kotlinx.coroutines.flow.Flow

interface InstallationStateRepository {
    val state: Flow<InstallationState?>
    suspend fun save(value: InstallationState): AppResult<Unit>
}
