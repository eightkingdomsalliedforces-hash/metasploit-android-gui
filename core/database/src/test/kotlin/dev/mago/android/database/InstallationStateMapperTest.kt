package dev.mago.android.database

import com.google.common.truth.Truth.assertThat
import dev.mago.android.installation.InstallationFailureKind
import dev.mago.android.database.entity.InstallationStateEntity
import dev.mago.android.installation.InstallationStage
import dev.mago.android.installation.InstallationState
import dev.mago.android.model.AppError
import org.junit.Test

class InstallationStateMapperTest {
    private val mapper = InstallationStateMapper()

    @Test
    fun `installation state mapper preserves recovery fields`() {
        val original = InstallationState(
            stage = InstallationStage.STARTING_SERVICES,
            progress = 82,
            operationId = "op-1",
            lastSuccessfulStage = InstallationStage.CONFIGURING_RPC,
            retryCount = 2,
            failureKind = InstallationFailureKind.RECOVERABLE_ERROR,
            lastError = AppError(
                errorCode = "RPC_START_FAILED",
                userMessage = "RPC 無法啟動",
                technicalMessage = "connection refused",
                retryable = true,
            ),
            updatedAtEpochMillis = 1234L,
        )

        val restored = mapper.toDomain(mapper.toEntity(original))

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `unknown enum values fall back without throwing`() {
        val restored = mapper.toDomain(
            InstallationStateEntity(
                stage = "FUTURE_STAGE",
                progress = 0,
                operationId = null,
                lastSuccessfulStage = "FUTURE_STAGE",
                retryCount = 0,
                failureKind = "FUTURE_FAILURE",
                errorCode = null,
                errorUserMessage = null,
                errorTechnicalMessage = null,
                errorRetryable = false,
                updatedAtEpochMillis = 1,
            ),
        )
        assertThat(restored.stage).isEqualTo(InstallationStage.NOT_STARTED)
        assertThat(restored.failureKind).isEqualTo(InstallationFailureKind.FATAL_ERROR)
    }
}
