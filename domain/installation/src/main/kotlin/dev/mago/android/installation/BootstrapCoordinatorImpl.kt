package dev.mago.android.installation

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.SuggestedAction
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
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
    private val saveRpcPassword: suspend (CharArray) -> AppResult<Unit>,
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
        val initialHealth = ensureBridgeAndReadHealth() ?: return@withLock
        updateDiagnostics(initialHealth)

        val hasRuby = initialHealth.flag("ruby")
        val hasPostgres = initialHealth.flag("psql")
        val hasRepository = initialHealth.flag("metasploitRepository")
        val hasConsole = initialHealth.flag("msfconsole")
        val databaseConfigured = initialHealth.flag("databaseConfigured")

        if (!hasRuby || !hasPostgres || !hasRepository) {
            if (runStage(InstallationStage.UPDATING_PACKAGES, BridgeAction.UPDATE_PACKAGES, 15) == null) return@withLock
        }
        if (!hasRuby || !hasPostgres) {
            if (runStage(InstallationStage.INSTALLING_DEPENDENCIES, BridgeAction.INSTALL_DEPENDENCIES, 30) == null) return@withLock
        }
        if (!hasRepository) {
            if (runStage(InstallationStage.INSTALLING_METASPLOIT, BridgeAction.INSTALL_METASPLOIT, 50) == null) return@withLock
        } else if (!hasConsole) {
            if (runStage(InstallationStage.INSTALLING_METASPLOIT, BridgeAction.REPAIR_METASPLOIT, 50) == null) return@withLock
        }
        if (!databaseConfigured) {
            if (runStage(InstallationStage.INITIALIZING_DATABASE, BridgeAction.INITIALIZE_DATABASE, 68) == null) return@withLock
        }

        val credentials = runStage(InstallationStage.CONFIGURING_RPC, BridgeAction.CONFIGURE_RPC, 78)
            ?: return@withLock
        if (!storeRpcCredentials(credentials)) return@withLock

        if (runStage(InstallationStage.STARTING_SERVICES, BridgeAction.START_SERVICES, 86) == null) return@withLock
        if (runStage(InstallationStage.STARTING_SERVICES, BridgeAction.START_RPC, 92) == null) return@withLock

        setStage(InstallationStage.VERIFYING, 96)
        val finalHealth = executeBridge(BridgeAction.HEALTH_CHECK, InstallationStage.VERIFYING)
            ?: return@withLock
        updateDiagnostics(finalHealth)
        if (!finalHealth.flag("databaseRunning") || !finalHealth.flag("rpcPortOpen")) {
            failAt(
                InstallationStage.VERIFYING,
                AppError(
                    errorCode = "INSTALLATION_HEALTH_CHECK_FAILED",
                    userMessage = "Metasploit 服務未通過最終檢查",
                    technicalMessage = finalHealth.data.toSortedMap().toString(),
                    suggestedAction = SuggestedAction.RUN_HEALTH_CHECK,
                    retryable = true,
                ),
                InstallationFailureKind.RPC_ERROR,
            )
            return@withLock
        }

        metasploitRepository.logout()
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

    override suspend fun retryCurrentStage() = inspectEnvironment()

    override fun openTermux(): AppResult<Unit> = termuxGateway.openTermux()

    private suspend fun ensureBridgeAndReadHealth(): BridgeResponse? {
        var health = termuxGateway.execute(BridgeAction.HEALTH_CHECK, newOperationId("health"))
        if (health is AppResult.Failure) {
            when (val deployment = termuxGateway.deployBridge()) {
                is AppResult.Failure -> {
                    failAt(InstallationStage.DEPLOYING_BRIDGE, deployment.error)
                    return null
                }
                is AppResult.Success -> Unit
            }
            health = termuxGateway.execute(BridgeAction.HEALTH_CHECK, newOperationId("health"))
        }
        return when (health) {
            is AppResult.Failure -> {
                failAt(InstallationStage.DEPLOYING_BRIDGE, health.error)
                null
            }
            is AppResult.Success -> health.value
        }
    }

    private suspend fun runStage(
        stage: InstallationStage,
        action: BridgeAction,
        progress: Int,
    ): BridgeResponse? {
        setStage(stage, progress)
        val response = executeBridge(action, stage) ?: return null
        setState(
            _state.value.copy(
                progress = progress,
                lastSuccessfulStage = stage,
                operationId = null,
                retryCount = 0,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return response
    }

    private suspend fun executeBridge(action: BridgeAction, stage: InstallationStage): BridgeResponse? {
        val operationId = newOperationId(action.name.lowercase())
        setState(_state.value.copy(operationId = operationId, updatedAtEpochMillis = System.currentTimeMillis()))
        return when (val result = termuxGateway.execute(action, operationId)) {
            is AppResult.Failure -> {
                failAt(stage, result.error, failureKindFor(action))
                null
            }
            is AppResult.Success -> result.value
        }
    }

    private suspend fun storeRpcCredentials(response: BridgeResponse): Boolean {
        val user = response.data["rpcUser"]
        val password = response.data["rpcPassword"]
        if (user != "msf" || password.isNullOrBlank()) {
            failAt(
                InstallationStage.CONFIGURING_RPC,
                AppError(
                    errorCode = "RPC_CREDENTIAL_RESPONSE_INVALID",
                    userMessage = "Bridge 未回傳有效 RPC 帳密",
                    retryable = true,
                ),
                InstallationFailureKind.RPC_ERROR,
            )
            return false
        }
        val chars = password.toCharArray()
        return try {
            when (val saved = saveRpcPassword(chars)) {
                is AppResult.Failure -> {
                    failAt(InstallationStage.CONFIGURING_RPC, saved.error, InstallationFailureKind.RPC_ERROR)
                    false
                }
                is AppResult.Success -> true
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun BridgeResponse.flag(name: String): Boolean = data[name].equals("true", ignoreCase = true)

    private fun updateDiagnostics(response: BridgeResponse) {
        _diagnostics.value = response.data
            .filterKeys { !it.contains("password", ignoreCase = true) && !it.contains("token", ignoreCase = true) }
            .toSortedMap()
            .map { (key, value) ->
                DiagnosticEntry(
                    key = "bridge.$key",
                    label = key,
                    value = value,
                    sensitive = key.contains("path", ignoreCase = true) || key.equals("prefix", ignoreCase = true),
                )
            }
    }

    private fun failureKindFor(action: BridgeAction): InstallationFailureKind = when (action) {
        BridgeAction.INITIALIZE_DATABASE,
        BridgeAction.START_SERVICES,
        BridgeAction.STOP_SERVICES -> InstallationFailureKind.DATABASE_ERROR
        BridgeAction.CONFIGURE_RPC,
        BridgeAction.START_RPC,
        BridgeAction.STOP_RPC -> InstallationFailureKind.RPC_ERROR
        BridgeAction.UPDATE_PACKAGES,
        BridgeAction.INSTALL_DEPENDENCIES,
        BridgeAction.INSTALL_METASPLOIT,
        BridgeAction.REPAIR_METASPLOIT,
        BridgeAction.UPDATE_METASPLOIT -> InstallationFailureKind.RECOVERABLE_ERROR
        else -> InstallationFailureKind.RECOVERABLE_ERROR
    }

    private suspend fun setStage(stage: InstallationStage, progress: Int) {
        setState(
            _state.value.copy(
                stage = stage,
                progress = progress,
                operationId = null,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun restoreOnce() {
        if (restored) return
        installationStateRepository.state.first()?.let { _state.value = it }
        restored = true
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
                retryCount = _state.value.retryCount + 1,
                lastError = error,
                failureKind = kind,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun setState(value: InstallationState) {
        _state.value = value
        installationStateRepository.save(value)
    }

    private fun newOperationId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}
