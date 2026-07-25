package dev.mago.android.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.SuggestedAction
import kotlinx.coroutines.withTimeoutOrNull

class TermuxRunCommandClient(
    private val context: Context,
    private val timeoutMillis: Long = 60_000L,
) {
    suspend fun execute(command: TermuxCommand): AppResult<TermuxCommandResult> {
        val pending = TermuxCommandResultRegistry.register()
        val callback = PendingIntent.getService(
            context,
            pending.executionId,
            Intent(context, TermuxCommandResultService::class.java)
                .putExtra(TermuxRunCommandContract.EXTRA_EXECUTION_ID, pending.executionId),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
        )
        val intent = Intent(TermuxRunCommandContract.ACTION).apply {
            component = ComponentName(
                TermuxRunCommandContract.PACKAGE,
                TermuxRunCommandContract.SERVICE,
            )
            putExtra(TermuxRunCommandContract.EXTRA_PATH, command.executable)
            putExtra(TermuxRunCommandContract.EXTRA_ARGUMENTS, command.arguments)
            putExtra(TermuxRunCommandContract.EXTRA_WORKDIR, command.workingDirectory)
            @Suppress("DEPRECATION")
            putExtra(TermuxRunCommandContract.EXTRA_BACKGROUND, true)
            putExtra(TermuxRunCommandContract.EXTRA_PENDING_INTENT, callback)
        }

        return try {
            val started = context.startService(intent)
                ?: return failure(
                    code = "TERMUX_SERVICE_UNAVAILABLE",
                    user = "Termux 命令服務無法使用",
                    action = SuggestedAction.OPEN_TERMUX,
                )
            if (started.packageName != TermuxRunCommandContract.PACKAGE) {
                return failure(
                    code = "TERMUX_SERVICE_UNAVAILABLE",
                    user = "Termux 命令服務回應不正確",
                    action = SuggestedAction.OPEN_TERMUX,
                )
            }

            val result = withTimeoutOrNull(timeoutMillis) { pending.deferred.await() }
                ?: return failure(
                    code = "TERMUX_RESULT_TIMEOUT",
                    user = "等待 Termux 執行結果逾時",
                    action = SuggestedAction.RETRY,
                )

            if (result.internalErrorCode != 0) {
                val disabled = result.internalErrorMessage.contains("allow-external-apps", ignoreCase = true)
                return failure(
                    code = if (disabled) {
                        "TERMUX_EXTERNAL_APPS_DISABLED"
                    } else {
                        "TERMUX_RESULT_INVALID"
                    },
                    user = if (disabled) {
                        "請在 Termux 設定 allow-external-apps=true"
                    } else {
                        "Termux 無法執行管理命令"
                    },
                    technical = result.internalErrorMessage,
                    action = if (disabled) SuggestedAction.OPEN_TERMUX else SuggestedAction.RETRY,
                )
            }

            AppResult.Success(result)
        } catch (error: SecurityException) {
            failure(
                code = "TERMUX_RUN_COMMAND_DENIED",
                user = "尚未取得 Termux 命令權限",
                technical = error.message,
                action = SuggestedAction.GRANT_PERMISSION,
            )
        } catch (error: IllegalStateException) {
            failure(
                code = "TERMUX_SERVICE_UNAVAILABLE",
                user = "Android 阻止啟動 Termux 命令服務",
                technical = error.message,
                action = SuggestedAction.OPEN_TERMUX,
            )
        } finally {
            TermuxCommandResultRegistry.remove(pending.executionId)
        }
    }

    private fun failure(
        code: String,
        user: String,
        technical: String? = null,
        action: SuggestedAction,
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = user,
            technicalMessage = technical,
            suggestedAction = action,
            retryable = action == SuggestedAction.RETRY,
        ),
    )
}
