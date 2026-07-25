package dev.mago.android.installation

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.SuggestedAction
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
import dev.mago.android.security.SecretStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BootstrapCoordinatorImpl(
    private val termuxGateway: TermuxGateway,
    private val metasploitRepository: MetasploitConnectionRepository,
    private val installationStateRepository: InstallationStateRepository,
    private val secretStore: SecretStore,
) : BootstrapCoordinator {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(InstallationState.initial())
    private val _environment = MutableStateFlow<TermuxEnvironment?>(null)
    private val _metasploitVersion = MutableStateFlow<MetasploitVersion?>(null)
    private val _diagnostics = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    private var restored = false

    override val state = _state.asStateFlow()
    override val environment = _environment.asStateFlow()
    override val metasploitVersion = _metasploitVersion.asStateFlow()
    override val diagnostics = _diagnostics.asStateFlow()

    override suspend fun inspectEnvironment() = mutex.withLock {
        restoreOnce()
        setStage(InstallationStage.CHECKING_DEVICE, 0)

        val environmentResult = termuxGateway.inspect()
        if (environmentResult is AppResult.Failure) {
            failAt(InstallationStage.CHECKING_DEVICE, environmentResult.error)
            return@withLock
        }
        val environment = (environmentResult as AppResult.Success).value
        _environment.value = environment
        if (!environment.installed) {
            waitForUser(
                InstallationStage.TERMUX_REQUIRED,
                AppError(
                    errorCode = "TERMUX_NOT_INSTALLED",
                    userMessage = "尚未安裝 Termux",
                    suggestedAction = SuggestedAction.OPEN_TERMUX,
                ),
            )
            return@withLock
        }
        if (!environment.runCommandPermissionGranted) {
            waitForUser(
                InstallationStage.PERMISSION_REQUIRED,
                AppError(
                    errorCode = "TERMUX_RUN_COMMAND_DENIED",
                    userMessage = "尚未取得 Termux 命令權限",
                    suggestedAction = SuggestedAction.GRANT_PERMISSION,
                ),
            )
            return@withLock
        }

        setStage(InstallationStage.DEPLOYING_BRIDGE, 5)
        var health = executeRaw(BridgeAction.HEALTH_CHECK, "health")
        if (health == null) {
            when (val deployment = termuxGateway.deployBridge()) {
                is AppResult.Failure -> {
                    failAt(InstallationStage.DEPLOYING_BRIDGE, deployment.error)
                    return@withLock
                }
                is AppResult.Success -> Unit
            }
            health = executeRaw(BridgeAction.HEALTH_CHECK, "health")
        }
        if (health == null) return@withLock
        updateDiagnostics(health)
        markStageSucceeded(InstallationStage.DEPLOYING_BRIDGE)

        val initialHealth = health.data
        if (!initialHealth.isTrue("frameworkRepository")) {
            if (!initialHealth.dependenciesReady()) {
                if (runStage(InstallationStage.UPDATING_PACKAGES, 15, BridgeAction.UPDATE_PACKAGES) == null) {
                    return@withLock
                }
                if (runStage(
                        InstallationStage.INSTALLING_DEPENDENCIES,
                        30,
                        BridgeAction.INSTALL_DEPENDENCIES,
                    ) == null
                ) {
                    return@withLock
                }
            }
            if (runStage(InstallationStage.INSTALLING_METASPLOIT, 55, BridgeAction.INSTALL_METASPLOIT) == null) {
                return@withLock
            }
        } else if (!initialHealth.isTrue("msfconsole")) {
            if (runStage(InstallationStage.INSTALLING_METASPLOIT, 55, BridgeAction.REPAIR_METASPLOIT) == null) {
                return@withLock
            }
        }

        if (!initialHealth.isTrue("databaseInitialized") || !initialHealth.isTrue("databaseConfig")) {
            if (runStage(
                    InstallationStage.INITIALIZING_DATABASE,
                    70,
                    BridgeAction.INITIALIZE_DATABASE,
                ) == null
            ) {
                return@withLock
            }
        }

        val localCredentialAvailable = hasStoredRpcPassword() ?: return@withLock
        var rpcUsername = DEFAULT_RPC_USERNAME
        if (!initialHealth.isTrue("rpcConfigured") || !localCredentialAvailable) {
            val credentialResponse = runStage(
                InstallationStage.CONFIGURING_RPC,
                80,
                BridgeAction.CONFIGURE_RPC,
            ) ?: return@withLock
            rpcUsername = storeRpcCredentials(credentialResponse) ?: return@withLock
        }

        val servicesReady = initialHealth.isTrue("databaseReady") &&
            initialHealth.isTrue("rpcProcessRunning") &&
            initialHealth.isTrue("rpcPortOpen")
        if (!servicesReady) {
            if (runStage(InstallationStage.STARTING_SERVICES, 90, BridgeAction.START_SERVICES) == null) {
                return@withLock
            }
        }

        setStage(InstallationStage.VERIFYING, 95)
        val finalHealth = executeRaw(BridgeAction.HEALTH_CHECK, "verify") ?: return@withLock
        updateDiagnostics(finalHealth)
        val verificationError = validateFinalHealth(finalHealth.data)
        if (verificationError != null) {
            failAt(InstallationStage.VERIFYING, verificationError, InstallationFailureKind.RPC_ERROR)
            return@withLock
        }

        metasploitRepository.logout()
        when (val login = metasploitRepository.login(rpcUsername)) {
            is AppResult.Failure -> {
                failAt(InstallationStage.VERIFYING, login.error, InstallationFailureKind.RPC_ERROR)
                return@withLock
            }
            is AppResult.Success -> Unit
        }
        when (val rpcHealth = metasploitRepository.health()) {
            is AppResult.Failure -> {
                failAt(InstallationStage.VERIFYING, rpcHealth.error, InstallationFailureKind.RPC_ERROR)
                return@withLock
            }
            is AppResult.Success -> Unit
        }
        when (val version = metasploitRepository.version()) {
            is AppResult.Failure -> {
                failAt(InstallationStage.VERIFYING, version.error, InstallationFailureKind.RPC_ERROR)
                return@withLock
            }
            is AppResult.Success -> _metasploitVersion.value = version.value
        }

        setState(
            _state.value.copy(
                stage = InstallationStage.READY,
                progress = 100,
                operationId = null,
                lastSuccessfulStage = InstallationStage.VERIFYING,
                retryCount = 0,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun retryCurrentStage() {
        inspectEnvironment()
    }

    override fun openTermux(): AppResult<Unit> = termuxGateway.openTermux()

    private suspend fun runStage(
        stage: InstallationStage,
        progress: Int,
        action: BridgeAction,
    ): BridgeResponse? {
        setStage(stage, progress)
        val result = executeRaw(action, action.name.lowercase()) ?: return null
        markStageSucceeded(stage)
        return result
    }

    private suspend fun executeRaw(action: BridgeAction, prefix: String): BridgeResponse? {
        val operationId = newOperationId(prefix)
        setState(_state.value.copy(operationId = operationId, updatedAtEpochMillis = System.currentTimeMillis()))
        return when (val result = termuxGateway.execute(action, operationId)) {
            is AppResult.Failure -> {
                failAt(_state.value.stage, result.error, classifyFailure(_state.value.stage))
                null
            }
            is AppResult.Success -> result.value
        }
    }

    private suspend fun hasStoredRpcPassword(): Boolean? = when (val stored = secretStore.readRpcPassword()) {
        is AppResult.Failure -> {
            failAt(InstallationStage.CONFIGURING_RPC, stored.error, InstallationFailureKind.RPC_ERROR)
            null
        }
        is AppResult.Success -> {
            val password = stored.value
            try {
                password != null && password.isNotEmpty()
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    private suspend fun storeRpcCredentials(response: BridgeResponse): String? {
        val rpcUser = response.data["rpcUser"]
        val rawPassword = response.data["rpcPassword"]
        if (rpcUser != DEFAULT_RPC_USERNAME || rawPassword.isNullOrBlank()) {
            failAt(
                InstallationStage.CONFIGURING_RPC,
                AppError(
                    errorCode = "RPC_CREDENTIAL_RESPONSE_INVALID",
                    userMessage = "Bridge 沒有回傳有效的 RPC 帳密",
                    retryable = true,
                ),
                InstallationFailureKind.RPC_ERROR,
            )
            return null
        }
        val password = rawPassword.toCharArray()
        return try {
            when (val saved = secretStore.saveRpcPassword(password)) {
                is AppResult.Failure -> {
                    failAt(InstallationStage.CONFIGURING_RPC, saved.error, InstallationFailureKind.RPC_ERROR)
                    null
                }
                is AppResult.Success -> rpcUser
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun validateFinalHealth(data: Map<String, String>): AppError? {
        val valid = data.isTrue("frameworkRepository") &&
            data.isTrue("databaseInitialized") &&
            data.isTrue("databaseConfig") &&
            data.isTrue("databaseReady") &&
            data.isTrue("rpcConfigured") &&
            data.isTrue("rpcProcessRunning") &&
            data.isTrue("rpcPortOpen") &&
            data["rpcHost"] == "127.0.0.1" &&
            data["rpcPort"] == "55552"
        return if (valid) null else AppError(
            errorCode = "INSTALLATION_VERIFICATION_FAILED",
            userMessage = "Metasploit 服務尚未全部就緒",
            suggestedAction = SuggestedAction.RETRY,
            retryable = true,
            diagnosticData = data.filterKeys { !it.contains("password", ignoreCase = true) },
        )
    }

    private fun updateDiagnostics(response: BridgeResponse) {
        _diagnostics.value = response.data
            .filterKeys { !it.contains("password", ignoreCase = true) && !it.contains("token", ignoreCase = true) }
            .toSortedMap()
            .map { (key, value) ->
                DiagnosticEntry(
                    key = "bridge.$key",
                    label = key,
                    value = value,
                    sensitive = key.contains("path", ignoreCase = true) ||
                        key.equals("prefix", ignoreCase = true) ||
                        key.contains("secret", ignoreCase = true),
                )
            }
    }

    private suspend fun restoreOnce() {
        if (restored) return
        installationStateRepository.state.first()?.let { _state.value = it }
        restored = true
    }

    private suspend fun setStage(stage: InstallationStage, progress: Int) {
        setState(
            _state.value.copy(
                stage = stage,
                progress = progress.coerceIn(0, 100),
                operationId = null,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun markStageSucceeded(stage: InstallationStage) {
        setState(
            _state.value.copy(
                progress = 100,
                operationId = null,
                lastSuccessfulStage = stage,
                retryCount = 0,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun waitForUser(stage: InstallationStage, error: AppError) {
        setState(
            _state.value.copy(
                stage = stage,
                progress = 0,
                operationId = null,
                lastError = error,
                failureKind = InstallationFailureKind.WAITING_FOR_USER,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun failAt(
        stage: InstallationStage,
        error: AppError,
        kind: InstallationFailureKind = if (error.retryable) {
            InstallationFailureKind.RECOVERABLE_ERROR
        } else {
            InstallationFailureKind.FATAL_ERROR
        },
    ) {
        setState(
            _state.value.copy(
                stage = stage,
                operationId = null,
                retryCount = _state.value.retryCount + 1,
                lastError = error,
                failureKind = kind,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun classifyFailure(stage: InstallationStage): InstallationFailureKind = when (stage) {
        InstallationStage.UPDATING_PACKAGES,
        InstallationStage.INSTALLING_DEPENDENCIES,
        -> InstallationFailureKind.PACKAGE_CONFLICT
        InstallationStage.INITIALIZING_DATABASE -> InstallationFailureKind.DATABASE_ERROR
        InstallationStage.CONFIGURING_RPC,
        InstallationStage.STARTING_SERVICES,
        InstallationStage.VERIFYING,
        -> InstallationFailureKind.RPC_ERROR
        else -> InstallationFailureKind.RECOVERABLE_ERROR
    }

    private suspend fun setState(value: InstallationState) {
        _state.value = value
        installationStateRepository.save(value)
    }

    private fun newOperationId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun Map<String, String>.isTrue(key: String): Boolean = this[key].equals("true", ignoreCase = true)

    private fun Map<String, String>.dependenciesReady(): Boolean =
        listOf("git", "ruby", "gem", "psql", "initdb", "pgCtl", "openssl", "ss").all { key -> isTrue(key) }

    private companion object {
        const val DEFAULT_RPC_USERNAME = "msf"
    }
}
