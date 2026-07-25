package dev.mago.android.installation

import dev.mago.android.model.AppError

class InstallationReducer {
    fun reduce(current: InstallationState, event: InstallationEvent): InstallationState = when (event) {
        is InstallationEvent.StageStarted -> {
            if (event.stage != current.stage) invalidEvent(current, event.stage, event.nowEpochMillis)
            else current.copy(
                operationId = event.operationId,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = event.nowEpochMillis,
            )
        }

        is InstallationEvent.ProgressUpdated -> {
            if (event.stage != current.stage) invalidEvent(current, event.stage, event.nowEpochMillis)
            else current.copy(
                progress = event.progress.coerceIn(0, 100),
                updatedAtEpochMillis = event.nowEpochMillis,
            )
        }

        is InstallationEvent.StageSucceeded -> {
            if (event.stage != current.stage) invalidEvent(current, event.stage, event.nowEpochMillis)
            else current.copy(
                stage = nextStage[current.stage] ?: current.stage,
                progress = if (current.stage == InstallationStage.READY) 100 else 0,
                operationId = null,
                lastSuccessfulStage = current.stage,
                retryCount = 0,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = event.nowEpochMillis,
            )
        }

        is InstallationEvent.StageFailed -> {
            if (event.stage != current.stage) invalidEvent(current, event.stage, event.nowEpochMillis)
            else current.copy(
                retryCount = current.retryCount + 1,
                lastError = event.error,
                failureKind = event.failureKind,
                updatedAtEpochMillis = event.nowEpochMillis,
            )
        }

        is InstallationEvent.WaitingForUser -> {
            if (event.stage != current.stage) invalidEvent(current, event.stage, event.nowEpochMillis)
            else current.copy(
                lastError = event.error,
                failureKind = InstallationFailureKind.WAITING_FOR_USER,
                updatedAtEpochMillis = event.nowEpochMillis,
            )
        }

        is InstallationEvent.Reset -> InstallationState.initial(event.nowEpochMillis)
    }

    private fun invalidEvent(
        current: InstallationState,
        eventStage: InstallationStage,
        nowEpochMillis: Long,
    ): InstallationState = current.copy(
        lastError = AppError(
            errorCode = "INVALID_STAGE_EVENT",
            userMessage = "安裝流程狀態不一致",
            technicalMessage = "Current=${current.stage}, event=$eventStage",
            retryable = false,
        ),
        updatedAtEpochMillis = nowEpochMillis,
    )

    private companion object {
        val nextStage = mapOf(
            InstallationStage.NOT_STARTED to InstallationStage.CHECKING_DEVICE,
            InstallationStage.CHECKING_DEVICE to InstallationStage.TERMUX_REQUIRED,
            InstallationStage.TERMUX_REQUIRED to InstallationStage.TERMUX_INITIALIZATION_REQUIRED,
            InstallationStage.TERMUX_INITIALIZATION_REQUIRED to InstallationStage.PERMISSION_REQUIRED,
            InstallationStage.PERMISSION_REQUIRED to InstallationStage.DEPLOYING_BRIDGE,
            InstallationStage.DEPLOYING_BRIDGE to InstallationStage.UPDATING_PACKAGES,
            InstallationStage.UPDATING_PACKAGES to InstallationStage.INSTALLING_DEPENDENCIES,
            InstallationStage.INSTALLING_DEPENDENCIES to InstallationStage.INSTALLING_METASPLOIT,
            InstallationStage.INSTALLING_METASPLOIT to InstallationStage.INITIALIZING_DATABASE,
            InstallationStage.INITIALIZING_DATABASE to InstallationStage.CONFIGURING_RPC,
            InstallationStage.CONFIGURING_RPC to InstallationStage.STARTING_SERVICES,
            InstallationStage.STARTING_SERVICES to InstallationStage.VERIFYING,
            InstallationStage.VERIFYING to InstallationStage.READY,
        )
    }
}
