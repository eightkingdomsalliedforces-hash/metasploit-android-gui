package dev.mago.android.installation

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.ServiceStatus
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BootstrapCoordinatorImplTest {
    @Test
    fun `Termux private prefix is marked sensitive in diagnostics`() = runTest {
        val repository = InMemoryInstallationRepository()
        val coordinator = BootstrapCoordinatorImpl(
            termuxGateway = FakeTermuxGateway(
                installed = true,
                permission = true,
                data = mapOf("prefix" to "/data/data/com.termux/files/usr"),
            ),
            metasploitRepository = OfflineMetasploitRepository,
            installationStateRepository = repository,
        )

        coordinator.inspectEnvironment()

        assertThat(coordinator.diagnostics.value.single { it.key == "bridge.prefix" }.sensitive).isTrue()
    }

    @Test
    fun `missing Termux stops at user required stage`() = runTest {
        val repository = InMemoryInstallationRepository()
        val coordinator = BootstrapCoordinatorImpl(
            termuxGateway = FakeTermuxGateway(installed = false, permission = false),
            metasploitRepository = OfflineMetasploitRepository,
            installationStateRepository = repository,
        )

        coordinator.inspectEnvironment()

        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.TERMUX_REQUIRED)
        assertThat(coordinator.state.value.failureKind)
            .isEqualTo(InstallationFailureKind.WAITING_FOR_USER)
    }
}

private class InMemoryInstallationRepository : InstallationStateRepository {
    private val mutable = MutableStateFlow<InstallationState?>(null)
    override val state: Flow<InstallationState?> = mutable
    override suspend fun save(value: InstallationState): AppResult<Unit> {
        mutable.value = value
        return AppResult.Success(Unit)
    }
}

private class FakeTermuxGateway(
    private val installed: Boolean,
    private val permission: Boolean,
    private val data: Map<String, String> = emptyMap(),
) : TermuxGateway {
    override suspend fun inspect() = AppResult.Success(TermuxEnvironment(installed, permission))
    override suspend fun deployBridge(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun execute(action: BridgeAction, operationId: String) =
        AppResult.Success(BridgeResponse(1, operationId, action, true, 0, "ok", 100, data))
    override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)
}

private data object OfflineMetasploitRepository : MetasploitConnectionRepository {
    override suspend fun login(username: String): AppResult<Unit> = error("not used")
    override suspend fun version(): AppResult<MetasploitVersion> = error("not used")
    override suspend fun health(): AppResult<ServiceStatus> =
        AppResult.Failure(dev.mago.android.model.AppError("NO_RPC", "RPC unavailable"))
    override fun logout() = Unit
}
