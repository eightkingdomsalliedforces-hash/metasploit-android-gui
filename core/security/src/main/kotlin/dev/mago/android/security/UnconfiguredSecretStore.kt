package dev.mago.android.security

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.SuggestedAction

class UnconfiguredSecretStore : SecretStore {
    private fun unavailable(): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = "RPC_CREDENTIALS_NOT_CONFIGURED",
            userMessage = "RPC 尚未完成設定",
            suggestedAction = SuggestedAction.RUN_HEALTH_CHECK,
            retryable = false,
        ),
    )

    override suspend fun saveRpcPassword(value: CharArray): AppResult<Unit> = unavailable()
    override suspend fun readRpcPassword(): AppResult<CharArray?> = unavailable()
    override suspend fun clearRpcPassword(): AppResult<Unit> = AppResult.Success(Unit)
}
