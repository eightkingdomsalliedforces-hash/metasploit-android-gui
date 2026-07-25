package dev.mago.android.installation

import com.google.common.truth.Truth.assertThat
import dev.mago.android.model.AppError
import org.junit.Test

class InstallationReducerTest {
    private val reducer = InstallationReducer()

    @Test
    fun `device check success advances to termux requirement`() {
        val result = reducer.reduce(
            InstallationState.initial(),
            InstallationEvent.StageSucceeded(InstallationStage.CHECKING_DEVICE),
        )
        assertThat(result.stage).isEqualTo(InstallationStage.TERMUX_REQUIRED)
        assertThat(result.lastSuccessfulStage).isEqualTo(InstallationStage.CHECKING_DEVICE)
    }

    @Test
    fun `recoverable failure keeps last successful stage`() {
        val current = InstallationState(
            stage = InstallationStage.STARTING_SERVICES,
            lastSuccessfulStage = InstallationStage.CONFIGURING_RPC,
        )
        val result = reducer.reduce(
            current,
            InstallationEvent.StageFailed(
                stage = InstallationStage.STARTING_SERVICES,
                error = AppError("RPC_START_FAILED", "RPC 無法啟動", retryable = true),
            ),
        )
        assertThat(result.failureKind).isEqualTo(InstallationFailureKind.RECOVERABLE_ERROR)
        assertThat(result.lastSuccessfulStage).isEqualTo(InstallationStage.CONFIGURING_RPC)
    }

    @Test
    fun `stage success for a different current stage is rejected`() {
        val current = InstallationState.initial()
        val result = reducer.reduce(
            current,
            InstallationEvent.StageSucceeded(InstallationStage.STARTING_SERVICES),
        )
        assertThat(result.stage).isEqualTo(InstallationStage.CHECKING_DEVICE)
        assertThat(result.lastError?.errorCode).isEqualTo("INVALID_STAGE_EVENT")
    }
}
