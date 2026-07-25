package dev.mago.android.database

import dev.mago.android.database.entity.InstallationStateEntity
import dev.mago.android.installation.InstallationFailureKind
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.InstallationState
import dev.mago.android.model.AppError

class InstallationStateMapper {
    fun toEntity(value: InstallationState): InstallationStateEntity = InstallationStateEntity(
        stage = value.stage.name,
        progress = value.progress,
        operationId = value.operationId,
        lastSuccessfulStage = value.lastSuccessfulStage?.name,
        retryCount = value.retryCount,
        failureKind = value.failureKind?.name,
        errorCode = value.lastError?.errorCode,
        errorUserMessage = value.lastError?.userMessage,
        errorTechnicalMessage = value.lastError?.technicalMessage,
        errorRetryable = value.lastError?.retryable ?: false,
        updatedAtEpochMillis = value.updatedAtEpochMillis,
    )

    fun toDomain(value: InstallationStateEntity): InstallationState = InstallationState(
        stage = enumOrDefault(value.stage, InstallationStage.NOT_STARTED),
        progress = value.progress.coerceIn(0, 100),
        operationId = value.operationId,
        lastSuccessfulStage = value.lastSuccessfulStage?.let {
            enumOrNull<InstallationStage>(it)
        },
        retryCount = value.retryCount.coerceAtLeast(0),
        lastError = if (value.errorCode != null && value.errorUserMessage != null) {
            AppError(
                errorCode = value.errorCode,
                userMessage = value.errorUserMessage,
                technicalMessage = value.errorTechnicalMessage,
                retryable = value.errorRetryable,
            )
        } else {
            null
        },
        failureKind = value.failureKind?.let {
            enumOrDefault(it, InstallationFailureKind.FATAL_ERROR)
        },
        updatedAtEpochMillis = value.updatedAtEpochMillis,
    )

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
        enumOrNull<T>(name) ?: fallback
}
