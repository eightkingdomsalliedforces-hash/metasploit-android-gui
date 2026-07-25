package dev.mago.android.installation

import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.model.AppError
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.SuggestedAction
import dev.mago.android.model.bridge.BridgeAction
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
        setState(
            _state.value.copy(
                stage = InstallationStage.CHECKING_DEVICE,
                progress = 0,
                operationId = null,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

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

        setState(
            _state.value.copy(
                stage = InstallationStage.DEPLOYING_BRIDGE,
                progress = 10,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        var operationId = newOperationId("health")
        var health = termuxGateway.execute(BridgeAction.HEALTH_CHECK, operationId)
        if (health is AppResult.Failure) {
            when (val deployment = termuxGateway.deployBridge()) {
                is AppResult.Failure -> {
                    failAt(InstallationStage.DEPLOYING_BRIDGE, deployment.error)
                    return@withLock
                }
                is AppResult.Success -> Unit
            }
            operationId = newOperationId("health")
            health = termuxGateway.execute(BridgeAction.HEALTH_CHECK, operationId)
        }
        if (health is AppResult.Failure) {
            failAt(InstallationStage.DEPLOYING_BRIDGE, health.error)
            return@withLock
        }
        val healthResponse = (health as AppResult.Success).value
        _diagnostics.value = healthResponse.data.toSortedMap().map { (key, value) ->
            DiagnosticEntry(
                key = "bridge.$key",
                label = key,
                value = value,
                sensitive = key.contains("path", ignoreCase = true) || key.equals("prefix", ignoreCase = true),
            )
        }

        // Phase 1 deliberately does not run package installation or service-start actions.
        // The next meaningful boundary is RPC configuration/health.
        setState(
            _state.value.copy(
                stage = InstallationStage.CONFIGURING_RPC,
                progress = 90,
                lastSuccessfulStage = InstallationStage.DEPLOYING_BRIDGE,
                lastError = null,
                failureKind = null,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        when (val rpcHealth = metasploitRepository.health()) {
            is AppResult.Failure -> {
                setState(
                    _state.value.copy(
                        lastError = rpcHealth.error,
                        failureKind = InstallationFailureKind.RPC_ERROR,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                return@withLock
            }
            is AppResult.Success -> Unit
        }
        when (val version = metasploitRepository.version()) {
            is AppResult.Failure -> {
                setState(
                    _state.value.copy(
                        lastError = version.error,
                        failureKind = InstallationFailureKind.RPC_ERROR,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                return@withLock
            }
            is AppResult.Success -> _metasploitVersion.value = version.value
        }
        setState(
            _state.value.copy(
                stage = InstallationStage.READY,
                progress = 100,
                lastSuccessfulStage = InstallationStage.VERIFYING,
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
                lastError = error,
                failureKind = InstallationFailureKind.WAITING_FOR_USER,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun failAt(stage: InstallationStage, error: AppError) {
        setState(
            _state.value.copy(
                stage = stage,
                retryCount = _state.value.retryCount + 1,
                lastError = error,
                failureKind = if (error.retryable) {
                    InstallationFailureKind.RECOVERABLE_ERROR
                } else {
                    InstallationFailureKind.FATAL_ERROR
                },
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
