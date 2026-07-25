package dev.mago.android.model

data class AppError(
    val errorCode: String,
    val userMessage: String,
    val technicalMessage: String? = null,
    val suggestedAction: SuggestedAction? = null,
    val retryable: Boolean = false,
    val diagnosticData: Map<String, String> = emptyMap(),
)

enum class SuggestedAction {
    RETRY,
    OPEN_TERMUX,
    GRANT_PERMISSION,
    RESTART_RPC,
    RUN_HEALTH_CHECK,
    VIEW_TECHNICAL_DETAILS,
}
