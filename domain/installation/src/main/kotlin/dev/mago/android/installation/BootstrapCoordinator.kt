package dev.mago.android.installation

import dev.mago.android.common.AppResult
import dev.mago.android.model.DiagnosticEntry
import dev.mago.android.model.MetasploitVersion
import kotlinx.coroutines.flow.StateFlow

interface BootstrapCoordinator {
    val state: StateFlow<InstallationState>
    val environment: StateFlow<TermuxEnvironment?>
    val metasploitVersion: StateFlow<MetasploitVersion?>
    val diagnostics: StateFlow<List<DiagnosticEntry>>

    suspend fun inspectEnvironment()
    suspend fun retryCurrentStage()
    fun openTermux(): AppResult<Unit>
}
