package dev.mago.android.termux

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TermuxCommandResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val executionId = intent?.getIntExtra(TermuxRunCommandContract.EXTRA_EXECUTION_ID, -1) ?: -1
        val resultBundle = intent?.getBundleExtra(TermuxRunCommandContract.EXTRA_RESULT_BUNDLE)
        if (executionId >= 0 && resultBundle != null) {
            TermuxCommandResultRegistry.complete(
                executionId,
                TermuxCommandResult(
                    stdout = resultBundle.getString(TermuxRunCommandContract.RESULT_STDOUT).orEmpty(),
                    stderr = resultBundle.getString(TermuxRunCommandContract.RESULT_STDERR).orEmpty(),
                    exitCode = resultBundle.getInt(TermuxRunCommandContract.RESULT_EXIT_CODE, -1),
                    internalErrorCode = resultBundle.getInt(TermuxRunCommandContract.RESULT_ERROR, 0),
                    internalErrorMessage = resultBundle
                        .getString(TermuxRunCommandContract.RESULT_ERROR_MESSAGE)
                        .orEmpty(),
                ),
            )
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
