package dev.mago.android.termux

import android.content.Context
import android.content.Intent
import dev.mago.android.common.AppResult
import dev.mago.android.core.termux.R
import dev.mago.android.common.DispatcherProvider
import dev.mago.android.installation.TermuxEnvironment
import dev.mago.android.installation.TermuxGateway
import dev.mago.android.model.AppError
import dev.mago.android.model.SuggestedAction
import dev.mago.android.model.bridge.BridgeAction
import dev.mago.android.model.bridge.BridgeResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class TermuxGatewayImpl(
    context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val packageInspector: TermuxPackageInspector = TermuxPackageInspector(context),
    private val permissionInspector: TermuxPermissionInspector = TermuxPermissionInspector(context),
    private val commandClient: TermuxRunCommandClient = TermuxRunCommandClient(context),
    private val bridgeBundleProvider: () -> ByteArray = {
        context.resources.openRawResource(R.raw.mago_bridge_v1).use { it.readBytes() }
    },
    private val bootstrapFactory: TermuxBootstrapCommandFactory =
        TermuxBootstrapCommandFactory(BridgeBundleMetadata.SHA256),
) : TermuxGateway {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = false }

    override suspend fun inspect(): AppResult<TermuxEnvironment> = withContext(dispatcherProvider.io) {
        AppResult.Success(
            TermuxEnvironment(
                installed = packageInspector.isInstalled(),
                runCommandPermissionGranted = permissionInspector.isRunCommandGranted(),
            ),
        )
    }

    override suspend fun deployBridge(): AppResult<Unit> = withContext(dispatcherProvider.io) {
        when (val command = bootstrapFactory.create(bridgeBundleProvider())) {
            is AppResult.Failure -> command
            is AppResult.Success -> when (val result = commandClient.execute(command.value)) {
                is AppResult.Failure -> result
                is AppResult.Success -> {
                    if (result.value.exitCode == 0) AppResult.Success(Unit)
                    else AppResult.Failure(
                        AppError(
                            errorCode = "BRIDGE_DEPLOY_FAILED",
                            userMessage = "無法部署 Termux Bridge",
                            technicalMessage = result.value.stderr,
                            suggestedAction = SuggestedAction.RETRY,
                            retryable = true,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun execute(
        action: BridgeAction,
        operationId: String,
    ): AppResult<BridgeResponse> = withContext(dispatcherProvider.io) {
        if (!operationId.matches(Regex("^[A-Za-z0-9._-]{1,128}${'$'}"))) {
            return@withContext AppResult.Failure(
                AppError(
                    errorCode = "BRIDGE_OPERATION_ID_INVALID",
                    userMessage = "Bridge 工作識別碼格式不正確",
                    retryable = false,
                ),
            )
        }
        val command = TermuxCommand(
            executable = TermuxRunCommandContract.BASH,
            arguments = arrayOf(
                TermuxRunCommandContract.BRIDGE_DISPATCH,
                action.name,
                operationId,
            ),
            workingDirectory = TermuxRunCommandContract.BRIDGE_HOME,
        )
        when (val commandResult = commandClient.execute(command, timeoutFor(action))) {
            is AppResult.Failure -> commandResult
            is AppResult.Success -> {
                val result = commandResult.value
                if (result.stdout.isBlank()) {
                    return@withContext AppResult.Failure(
                        AppError(
                            errorCode = "TERMUX_RESULT_INVALID",
                            userMessage = "Termux 沒有回傳 Bridge 結果",
                            technicalMessage = result.stderr,
                            suggestedAction = SuggestedAction.RETRY,
                            retryable = true,
                        ),
                    )
                }
                try {
                    val response = json.decodeFromString<BridgeResponse>(result.stdout.trim())
                    if (response.schemaVersion != BridgeBundleMetadata.VERSION ||
                        response.operationId != operationId || response.action != action
                    ) {
                        AppResult.Failure(
                            AppError(
                                errorCode = "TERMUX_RESULT_INVALID",
                                userMessage = "Bridge 回傳資料與要求不一致",
                                technicalMessage = "schema=${response.schemaVersion}, operation=${response.operationId}, action=${response.action}",
                                retryable = true,
                            ),
                        )
                    } else if (!response.success || result.exitCode != 0) {
                        AppResult.Failure(
                            AppError(
                                errorCode = "BRIDGE_${action.name}_FAILED",
                                userMessage = response.message,
                                technicalMessage = "bridgeExit=${response.exitCode}; termuxExit=${result.exitCode}; ${result.stderr}",
                                suggestedAction = SuggestedAction.RETRY,
                                retryable = response.exitCode !in setOf(64, 65),
                                diagnosticData = response.data.filterKeys {
                                    !it.contains("password", ignoreCase = true) &&
                                        !it.contains("token", ignoreCase = true)
                                },
                            ),
                        )
                    } else {
                        AppResult.Success(response)
                    }
                } catch (error: SerializationException) {
                    AppResult.Failure(
                        AppError(
                            errorCode = "TERMUX_RESULT_INVALID",
                            userMessage = "Bridge 回傳格式無法解析",
                            technicalMessage = error.message,
                            suggestedAction = SuggestedAction.RETRY,
                            retryable = true,
                        ),
                    )
                }
            }
        }
    }

    private fun timeoutFor(action: BridgeAction): Long = when (action) {
        BridgeAction.UPDATE_PACKAGES -> 15 * 60_000L
        BridgeAction.INSTALL_DEPENDENCIES -> 20 * 60_000L
        BridgeAction.INSTALL_METASPLOIT,
        BridgeAction.REPAIR_METASPLOIT,
        BridgeAction.UPDATE_METASPLOIT -> 45 * 60_000L
        BridgeAction.INITIALIZE_DATABASE -> 20 * 60_000L
        BridgeAction.START_SERVICES,
        BridgeAction.STOP_SERVICES,
        BridgeAction.START_RPC,
        BridgeAction.STOP_RPC -> 3 * 60_000L
        else -> 60_000L
    }

    override fun openTermux(): AppResult<Unit> {
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(TermuxRunCommandContract.PACKAGE)
            ?: return AppResult.Failure(
                AppError(
                    errorCode = "TERMUX_NOT_INSTALLED",
                    userMessage = "尚未安裝 Termux",
                    suggestedAction = SuggestedAction.OPEN_TERMUX,
                    retryable = false,
                ),
            )
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(launchIntent)
            AppResult.Success(Unit)
        } catch (error: RuntimeException) {
            AppResult.Failure(
                AppError(
                    errorCode = "TERMUX_OPEN_FAILED",
                    userMessage = "無法開啟 Termux",
                    technicalMessage = error.message,
                    suggestedAction = SuggestedAction.RETRY,
                    retryable = true,
                ),
            )
        }
    }
}
