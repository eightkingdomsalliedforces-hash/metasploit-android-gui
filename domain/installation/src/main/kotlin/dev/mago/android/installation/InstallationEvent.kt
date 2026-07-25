package dev.mago.android.installation

import dev.mago.android.model.AppError

sealed interface InstallationEvent {
    data class StageStarted(
        val stage: InstallationStage,
        val operationId: String? = null,
        val nowEpochMillis: Long = System.currentTimeMillis(),
    ) : InstallationEvent

    data class ProgressUpdated(
        val stage: InstallationStage,
        val progress: Int,
        val nowEpochMillis: Long = System.currentTimeMillis(),
    ) : InstallationEvent

    data class StageSucceeded(
        val stage: InstallationStage,
        val nowEpochMillis: Long = System.currentTimeMillis(),
    ) : InstallationEvent

    data class StageFailed(
        val stage: InstallationStage,
        val error: AppError,
        val failureKind: InstallationFailureKind = if (error.retryable) {
            InstallationFailureKind.RECOVERABLE_ERROR
        } else {
            InstallationFailureKind.FATAL_ERROR
        },
        val nowEpochMillis: Long = System.currentTimeMillis(),
    ) : InstallationEvent

    data class WaitingForUser(
        val stage: InstallationStage,
        val error: AppError? = null,
        val nowEpochMillis: Long = System.currentTimeMillis(),
    ) : InstallationEvent

    data class Reset(val nowEpochMillis: Long = System.currentTimeMillis()) : InstallationEvent
}
