package dev.mago.android

import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import dev.mago.android.operations.OperationsTab

data class OperationsActions(
    val onTabSelected: (OperationsTab) -> Unit,
    val onRefreshJobs: () -> Unit,
    val onRefreshSessions: () -> Unit,
    val onJobSelected: (MetasploitJobSummary) -> Unit,
    val onSessionSelected: (MetasploitSessionSummary) -> Unit,
    val onRequestStopJob: (MetasploitJobSummary) -> Unit,
    val onRequestStopSession: (MetasploitSessionSummary) -> Unit,
    val onCancelStop: () -> Unit,
    val onConfirmStop: () -> Unit,
    val onRequestInteraction: (MetasploitSessionSummary) -> Unit,
    val onInteractionAuthorizationChanged: (Boolean) -> Unit,
    val onCancelInteractionRequest: () -> Unit,
    val onOpenInteraction: () -> Unit,
    val onSessionInputChanged: (String) -> Unit,
    val onSendSessionInput: () -> Unit,
    val onReadSessionOutput: () -> Unit,
    val onClearSessionOutput: () -> Unit,
    val onCloseInteraction: () -> Unit,
)
