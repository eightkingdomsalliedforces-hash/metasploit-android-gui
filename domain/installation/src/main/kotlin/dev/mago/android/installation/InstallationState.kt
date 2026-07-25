package dev.mago.android.installation

import dev.mago.android.model.AppError

data class InstallationState(
    val stage: InstallationStage = InstallationStage.CHECKING_DEVICE,
    val progress: Int = 0,
    val operationId: String? = null,
    val lastSuccessfulStage: InstallationStage? = null,
    val retryCount: Int = 0,
    val lastError: AppError? = null,
    val failureKind: InstallationFailureKind? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        fun initial(nowEpochMillis: Long = System.currentTimeMillis()): InstallationState =
            InstallationState(updatedAtEpochMillis = nowEpochMillis)
    }
}
