package dev.mago.android.installation

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.ServiceStatus
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
import dev.mago.android.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BootstrapCoordinatorImplTest {
    @Test
    fun `healthy installed components are skipped while missing services are completed`() = runTest {
        val gateway = RecordingTermuxGateway(
            healthData = completeHealth(
                databaseReady = false,
                rpcConfigured = false,
                rpcPortOpen = false,
            ),
        )
        val secrets = RecordingSecretStore()
        val metasploit = RecordingMetasploitRepository()
        val coordinator = coordinator(gateway, metasploit, secrets)

        coordinator.inspectEnvironment()

        assertThat(gateway.actions).containsExactly(
            BridgeAction.HEALTH_CHECK,
            BridgeAction.CONFIGURE_RPC,
            BridgeAction.START_SERVICES,
            BridgeAction.HEALTH_CHECK,
        ).inOrder()
        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.READY)
        assertThat(metasploit.loginUsernames).containsExactly("msf")
    }

    @Test
    fun `database failure stops the pipeline and is classified`() = runTest {
        val gateway = RecordingTermuxGateway(
            healthData = completeHealth(
                frameworkRepository = true,
                databaseInitialized = false,
                databaseConfig = false,
                databaseReady = false,
                rpcConfigured = false,
                rpcPortOpen = false,
            ),
            failureAction = BridgeAction.INITIALIZE_DATABASE,
        )
        val coordinator = coordinator(gateway, RecordingMetasploitRepository(), RecordingSecretStore())

        coordinator.inspectEnvironment()

        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.INITIALIZING_DATABASE)
        assertThat(coordinator.state.value.failureKind).isEqualTo(InstallationFailureKind.DATABASE_ERROR)
        assertThat(gateway.actions).doesNotContain(BridgeAction.CONFIGURE_RPC)
        assertThat(gateway.actions).doesNotContain(BridgeAction.START_SERVICES)
    }

    @Test
    fun `rpc credentials are stored and temporary password is cleared before login`() = runTest {
        val gateway = RecordingTermuxGateway(
            healthData = completeHealth(rpcConfigured = false, rpcPortOpen = false),
            rpcPassword = "a".repeat(64),
        )
        val secrets = RecordingSecretStore()
        val metasploit = RecordingMetasploitRepository()
        val coordinator = coordinator(gateway, metasploit, secrets)

        coordinator.inspectEnvironment()

        assertThat(secrets.savedPasswordSnapshot).isEqualTo("a".repeat(64))
        assertThat(secrets.savedPasswordReference?.all { it == '\u0000' }).isTrue()
        assertThat(metasploit.loginUsernames).containsExactly("msf")
        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.READY)
    }

    @Test
    fun `Termux private prefix is marked sensitive in diagnostics`() = runTest {
        val coordinator = coordinator(
            RecordingTermuxGateway(healthData = completeHealth() + ("prefix" to "/data/data/com.termux/files/usr")),
            RecordingMetasploitRepository(),
            RecordingSecretStore(initialPassword = "b".repeat(64)),
        )

        coordinator.inspectEnvironment()

        assertThat(coordinator.diagnostics.value.single { it.key == "bridge.prefix" }.sensitive).isTrue()
    }

    @Test
    fun `missing Termux stops at user required stage`() = runTest {
        val repository = InMemoryInstallationRepository()
        val coordinator = BootstrapCoordinatorImpl(
            termuxGateway = RecordingTermuxGateway(installed = false, permission = false),
            metasploitRepository = RecordingMetasploitRepository(),
            installationStateRepository = repository,
            secretStore = RecordingSecretStore(),
        )

        coordinator.inspectEnvironment()

        assertThat(coordinator.state.value.stage).isEqualTo(InstallationStage.TERMUX_REQUIRED)
        assertThat(coordinator.state.value.failureKind)
            .isEqualTo(InstallationFailureKind.WAITING_FOR_USER)
    }

    private fun coordinator(
        gateway: RecordingTermuxGateway,
        metasploit: RecordingMetasploitRepository,
        secrets: RecordingSecretStore,
    ) = BootstrapCoordinatorImpl(
        termuxGateway = gateway,
        metasploitRepository = metasploit,
        installationStateRepository = InMemoryInstallationRepository(),
        secretStore = secrets,
    )
}

private class InMemoryInstallationRepository(
    initial: InstallationState? = null,
) : InstallationStateRepository {
    private val mutable = MutableStateFlow(initial)
    override val state: Flow<InstallationState?> = mutable
    override suspend fun save(value: InstallationState): AppResult<Unit> {
        mutable.value = value
        return AppResult.Success(Unit)
    }
}

private class RecordingTermuxGateway(
    private val installed: Boolean = true,
    private val permission: Boolean = true,
    private val healthData: Map<String, String> = completeHealth(),
    private val failureAction: BridgeAction? = null,
    private val rpcPassword: String = "c".repeat(64),
) : TermuxGateway {
    val actions = mutableListOf<BridgeAction>()
    private var healthChecks = 0

    override suspend fun inspect() = AppResult.Success(TermuxEnvironment(installed, permission))
    override suspend fun deployBridge(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun execute(action: BridgeAction, operationId: String): AppResult<BridgeResponse> {
        actions += action
        if (action == failureAction) {
            return AppResult.Failure(
                AppError(
                    errorCode = "BRIDGE_ACTION_FAILED",
                    userMessage = "failed $action",
                    retryable = true,
                ),
            )
        }
        val data = when (action) {
            BridgeAction.HEALTH_CHECK -> {
                healthChecks += 1
                if (healthChecks == 1) healthData else completeHealth()
            }
            BridgeAction.CONFIGURE_RPC -> mapOf(
                "rpcUser" to "msf",
                "rpcPassword" to rpcPassword,
                "credentialsCreated" to "true",
            )
            else -> emptyMap()
        }
        return AppResult.Success(BridgeResponse(1, operationId, action, true, 0, "ok", 100, data))
    }

    override fun openTermux(): AppResult<Unit> = AppResult.Success(Unit)
}

private class RecordingSecretStore(
    initialPassword: String? = null,
) : SecretStore {
    private var stored = initialPassword?.toCharArray()
    var savedPasswordSnapshot: String? = null
    var savedPasswordReference: CharArray? = null

    override suspend fun saveRpcPassword(value: CharArray): AppResult<Unit> {
        savedPasswordSnapshot = value.concatToString()
        savedPasswordReference = value
        stored = value.copyOf()
        return AppResult.Success(Unit)
    }

    override suspend fun readRpcPassword(): AppResult<CharArray?> = AppResult.Success(stored?.copyOf())

    override suspend fun clearRpcPassword(): AppResult<Unit> {
        stored?.fill('\u0000')
        stored = null
        return AppResult.Success(Unit)
    }
}

private class RecordingMetasploitRepository : MetasploitConnectionRepository {
    val loginUsernames = mutableListOf<String>()

    override suspend fun login(username: String): AppResult<Unit> {
        loginUsernames += username
        return AppResult.Success(Unit)
    }

    override suspend fun version(): AppResult<MetasploitVersion> = AppResult.Success(
        MetasploitVersion(
            frameworkVersion = "test",
            rubyVersion = "test",
            apiVersion = "test",
            extraFields = emptyMap(),
        ),
    )

    override suspend fun health(): AppResult<ServiceStatus> = AppResult.Success(ServiceStatus.RUNNING)
    override fun logout() = Unit
}

private fun completeHealth(
    ruby: Boolean = true,
    psql: Boolean = true,
    frameworkRepository: Boolean = true,
    databaseInitialized: Boolean = true,
    databaseConfig: Boolean = true,
    databaseReady: Boolean = true,
    rpcConfigured: Boolean = true,
    rpcPortOpen: Boolean = true,
): Map<String, String> = mapOf(
    "git" to ruby.toString(),
    "ruby" to ruby.toString(),
    "gem" to ruby.toString(),
    "psql" to psql.toString(),
    "initdb" to psql.toString(),
    "pgCtl" to psql.toString(),
    "openssl" to ruby.toString(),
    "ss" to ruby.toString(),
    "frameworkRepository" to frameworkRepository.toString(),
    "msfconsole" to frameworkRepository.toString(),
    "databaseInitialized" to databaseInitialized.toString(),
    "databaseConfig" to databaseConfig.toString(),
    "databaseReady" to databaseReady.toString(),
    "rpcConfigured" to rpcConfigured.toString(),
    "rpcProcessRunning" to rpcPortOpen.toString(),
    "rpcPortOpen" to rpcPortOpen.toString(),
    "rpcHost" to "127.0.0.1",
    "rpcPort" to "55552",
    "prefix" to "/data/data/com.termux/files/usr",
    "bridgeVersion" to "2",
)
