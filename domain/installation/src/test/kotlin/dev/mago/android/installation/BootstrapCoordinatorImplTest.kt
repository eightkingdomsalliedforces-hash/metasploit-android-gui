package dev.mago.android.installation

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
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
    fun `missing Termux stops at user required stage`() = runTest {
        val coordinator = coordinator(FakeTermuxGateway(installed = false, permission = false))
        coordinator.inspectEnvironment()
        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.TERMUX_REQUIRED)
        assertThat(coordinator.state.value.failureKind).isEqualTo(InstallationFailureKind.WAITING_FOR_USER)
    }

    @Test
    fun `fresh environment runs lifecycle and stores rpc password`() = runTest {
        val gateway = FakeTermuxGateway(installed = true, permission = true)
        var stored = ""
        val coordinator = coordinator(gateway) { chars ->
            stored = chars.concatToString()
            chars.fill('\u0000')
            AppResult.Success(Unit)
        }

        coordinator.inspectEnvironment()

        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.READY)
        assertThat(stored).isEqualTo(RPC_PASSWORD)
        assertThat(gateway.actions).containsAtLeast(
            BridgeAction.UPDATE_PACKAGES,
            BridgeAction.INSTALL_DEPENDENCIES,
            BridgeAction.INSTALL_METASPLOIT,
            BridgeAction.INITIALIZE_DATABASE,
            BridgeAction.CONFIGURE_RPC,
            BridgeAction.START_SERVICES,
            BridgeAction.START_RPC,
        ).inOrder()
        assertThat(coordinator.diagnostics.value.single { it.key == "bridge.prefix" }.sensitive).isTrue()
        assertThat(coordinator.diagnostics.value.none { it.key.contains("password", true) }).isTrue()
    }

    @Test
    fun `database bridge failure is classified`() = runTest {
        val gateway = FakeTermuxGateway(
            installed = true,
            permission = true,
            failureAction = BridgeAction.INITIALIZE_DATABASE,
        )
        val coordinator = coordinator(gateway)

        coordinator.inspectEnvironment()

        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.INITIALIZING_DATABASE)
        assertThat(coordinator.state.value.failureKind).isEqualTo(InstallationFailureKind.DATABASE_ERROR)
    }

    private fun coordinator(
        gateway: TermuxGateway,
        save: suspend (CharArray) -> AppResult<Unit> = { chars ->
            chars.fill('\u0000')
            AppResult.Success(Unit)
        },
    ) = BootstrapCoordinatorImpl(
        termuxGateway = gateway,
        metasploitRepository = OnlineMetasploitRepository,
        installationStateRepository = InMemoryInstallationRepository(),
        saveRpcPassword = save,
    )

    private companion object {
        const val RPC_PASSWORD = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
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
        private val failureAction: BridgeAction? = null,
    ) : TermuxGateway {
        val actions = mutableListOf<BridgeAction>()
        private var healthCalls = 0

        override suspend fun inspect() = AppResult.Success(TermuxEnvironment(installed, permission))
        override suspend fun deployBridge(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun execute(action: BridgeAction, operationId: String): AppResult<BridgeResponse> {
            actions += action
            if (action == failureAction) {
                return AppResult.Failure(AppError("BRIDGE_${action.name}_FAILED", "failed", retryable = true))
            }
            val data = when (action) {
                BridgeAction.HEALTH_CHECK -> {
                    healthCalls += 1
                    if (healthCalls == 1) initialHealth else finalHealth
                }
                BridgeAction.CONFIGURE_RPC -> mapOf(
                    "rpcUser" to "msf",
                    "rpcPassword" to RPC_PASSWORD,
                    "credentialsCreated" to "true",
                )
                else -> emptyMap()
            }
            return AppResult.Success(BridgeResponse(2, operationId, action, true, 0, "ok", 100, data))
        }

        override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)

        private val initialHealth = mapOf(
            "prefix" to "/data/data/com.termux/files/usr",
            "ruby" to "false",
            "psql" to "false",
            "msfconsole" to "false",
            "metasploitRepository" to "false",
            "databaseConfigured" to "false",
            "databaseRunning" to "false",
            "rpcPortOpen" to "false",
        )
        private val finalHealth = initialHealth + mapOf(
            "ruby" to "true",
            "psql" to "true",
            "msfconsole" to "true",
            "metasploitRepository" to "true",
            "databaseConfigured" to "true",
            "databaseRunning" to "true",
            "rpcPortOpen" to "true",
        )
    }

    private data object OnlineMetasploitRepository : MetasploitConnectionRepository {
        override suspend fun login(username: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun version(): AppResult<MetasploitVersion> =
            AppResult.Success(MetasploitVersion("test", "test", "test", emptyMap()))
        override suspend fun health(): AppResult<ServiceStatus> = AppResult.Success(ServiceStatus.RUNNING)
        override fun logout() = Unit
    }
}
