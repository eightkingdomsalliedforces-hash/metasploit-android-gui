package dev.mago.android.dashboard

import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary

sealed interface OperationStopTarget {
    val displayId: String

    data class Job(
        val id: String,
        val name: String,
    ) : OperationStopTarget {
        override val displayId: String = id
    }

    data class Session(
        val id: Int,
        val description: String,
        val sourceModule: String?,
    ) : OperationStopTarget {
        override val displayId: String = id.toString()
    }
}

data class OperationStopError(
    val title: String,
    val userMessage: String?,
)

internal fun OperationStopTarget.existsIn(
    jobs: List<MetasploitJobSummary>,
    sessions: List<MetasploitSessionSummary>,
): Boolean = when (this) {
    is OperationStopTarget.Job -> jobs.any { it.id == id }
    is OperationStopTarget.Session -> sessions.any { it.id == id }
}
