package dev.mago.android.reporting

import android.content.ContentResolver
import android.net.Uri
import dev.mago.android.common.AppResult
import dev.mago.android.common.DispatcherProvider
import dev.mago.android.model.AppError
import java.io.OutputStream
import kotlinx.coroutines.withContext

class ReportStreamWriter {
    fun write(output: OutputStream, document: ReportDocument) {
        output.write(document.bytes)
        output.flush()
    }
}

class SafReportWriter(
    private val contentResolver: ContentResolver,
    private val dispatcherProvider: DispatcherProvider,
    private val streamWriter: ReportStreamWriter = ReportStreamWriter(),
) {
    suspend fun write(uri: Uri, document: ReportDocument): AppResult<Unit> = withContext(dispatcherProvider.io) {
        try {
            val output = contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext failure("REPORT_OUTPUT_UNAVAILABLE", "無法開啟所選報告檔案")
            output.use { streamWriter.write(it, document) }
            AppResult.Success(Unit)
        } catch (exception: Exception) {
            failure(
                code = "REPORT_WRITE_FAILED",
                message = "報告寫入失敗",
                technicalMessage = exception.message,
            )
        }
    }

    private fun failure(
        code: String,
        message: String,
        technicalMessage: String? = null,
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = message,
            technicalMessage = technicalMessage,
            retryable = true,
        ),
    )
}
