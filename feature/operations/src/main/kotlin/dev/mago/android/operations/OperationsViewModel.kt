package dev.mago.android.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitJobRepository
import dev.mago.android.metasploit.MetasploitSessionRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OperationsTab {
    JOBS,
    SESSIONS,
}

sealed interface OperationStopConfirmation {
    data class Job(val job: MetasploitJobSummary) : OperationStopConfirmation
    data class Session(val session: MetasploitSessionSummary) : OperationStopConfirmation
}

data class SessionInteractionState(
    val session: MetasploitSessionSummary,
    val input: String = "",
    val output: String = "",
    val busy: Boolean = false,
    val errorMessage: String? = null,
)

data class OperationsUiState(
    val tab: OperationsTab = OperationsTab.JOBS,
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val sessions: List<MetasploitSessionSummary> = emptyList(),
    val selectedSession: MetasploitSessionSummary? = null,
    val jobsLoading: Boolean = false,
    val sessionsLoading: Boolean = false,
    val actionLoading: Boolean = false,
    val errorMessage: String? = null,
    val stopConfirmation: OperationStopConfirmation? = null,
    val interactionCandidate: MetasploitSessionSummary? = null,
    val interactionAuthorizationConfirmed: Boolean = false,
    val interaction: SessionInteractionState? = null,
)

class OperationsViewModel(
    private val jobRepository: MetasploitJobRepository,
    private val sessionRepository: MetasploitSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState = _uiState.asStateFlow()

    fun selectTab(tab: OperationsTab) {
        _uiState.update { it.copy(tab = tab, errorMessage = null) }
    }

    fun refreshJobs() {
        if (_uiState.value.jobsLoading) return
        _uiState.update { it.copy(jobsLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = jobRepository.list()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(jobsLoading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update { current ->
                    val selected = current.selectedJob?.takeIf { info -> result.value.any { it.id == info.id } }
                    current.copy(jobsLoading = false, jobs = result.value, selectedJob = selected)
                }
            }
        }
    }

    fun selectJob(job: MetasploitJobSummary) {
        if (_uiState.value.actionLoading) return
        _uiState.update { it.copy(actionLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = jobRepository.info(job.id)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(actionLoading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(actionLoading = false, selectedJob = result.value)
                }
            }
        }
    }

    fun refreshSessions() {
        if (_uiState.value.sessionsLoading) return
        _uiState.update { it.copy(sessionsLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.list()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(sessionsLoading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update { current ->
                    val selected = current.selectedSession?.takeIf { selected ->
                        result.value.any { it.id == selected.id }
                    }
                    current.copy(sessionsLoading = false, sessions = result.value, selectedSession = selected)
                }
            }
        }
    }

    fun selectSession(session: MetasploitSessionSummary) {
        _uiState.update { it.copy(selectedSession = session, errorMessage = null) }
    }

    fun requestStopJob(job: MetasploitJobSummary) {
        _uiState.update { it.copy(stopConfirmation = OperationStopConfirmation.Job(job), errorMessage = null) }
    }

    fun requestStopSession(session: MetasploitSessionSummary) {
        _uiState.update { it.copy(stopConfirmation = OperationStopConfirmation.Session(session), errorMessage = null) }
    }

    fun cancelStop() {
        _uiState.update { it.copy(stopConfirmation = null) }
    }

    fun confirmStop() {
        val confirmation = _uiState.value.stopConfirmation ?: return
        if (_uiState.value.actionLoading) return
        _uiState.update { it.copy(stopConfirmation = null, actionLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = when (confirmation) {
                is OperationStopConfirmation.Job -> jobRepository.stop(confirmation.job.id, userConfirmed = true)
                is OperationStopConfirmation.Session -> sessionRepository.stop(
                    confirmation.session.id,
                    userConfirmed = true,
                )
            }
            when (result) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(actionLoading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> _uiState.update { current ->
                    when (confirmation) {
                        is OperationStopConfirmation.Job -> current.copy(
                            actionLoading = false,
                            jobs = current.jobs.filterNot { it.id == confirmation.job.id },
                            selectedJob = current.selectedJob?.takeUnless { it.id == confirmation.job.id },
                        )
                        is OperationStopConfirmation.Session -> current.copy(
                            actionLoading = false,
                            sessions = current.sessions.filterNot { it.id == confirmation.session.id },
                            selectedSession = current.selectedSession?.takeUnless { it.id == confirmation.session.id },
                            interaction = current.interaction?.takeUnless { it.session.id == confirmation.session.id },
                        )
                    }
                }
            }
        }
    }

    fun requestInteraction(session: MetasploitSessionSummary) {
        _uiState.update {
            it.copy(
                interactionCandidate = session,
                interactionAuthorizationConfirmed = false,
                errorMessage = null,
            )
        }
    }

    fun setInteractionAuthorizationConfirmed(confirmed: Boolean) {
        _uiState.update { current ->
            if (current.interactionCandidate == null) current.copy(interactionAuthorizationConfirmed = false)
            else current.copy(interactionAuthorizationConfirmed = confirmed, errorMessage = null)
        }
    }

    fun cancelInteractionRequest() {
        _uiState.update {
            it.copy(interactionCandidate = null, interactionAuthorizationConfirmed = false)
        }
    }

    fun openInteraction() {
        val current = _uiState.value
        val candidate = current.interactionCandidate ?: return
        if (!current.interactionAuthorizationConfirmed) {
            _uiState.update { it.copy(errorMessage = "請先確認僅在授權環境互動") }
            return
        }
        _uiState.update {
            it.copy(
                interactionCandidate = null,
                interactionAuthorizationConfirmed = false,
                interaction = SessionInteractionState(session = candidate),
                errorMessage = null,
            )
        }
    }

    fun setSessionInput(value: String) {
        _uiState.update { current ->
            current.copy(interaction = current.interaction?.copy(input = value, errorMessage = null))
        }
    }

    fun sendSessionInput() {
        val interaction = _uiState.value.interaction ?: return
        if (interaction.busy || interaction.input.isBlank()) return
        _uiState.update { current ->
            current.copy(interaction = current.interaction?.copy(busy = true, errorMessage = null))
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.write(
                    interaction.session.id,
                    interaction.input,
                    userConfirmed = true,
                )
            ) {
                is AppResult.Failure -> _uiState.update { current ->
                    current.copy(
                        interaction = current.interaction?.copy(
                            busy = false,
                            errorMessage = result.error.userMessage,
                        ),
                    )
                }
                is AppResult.Success -> _uiState.update { current ->
                    current.copy(interaction = current.interaction?.copy(input = "", busy = false))
                }
            }
        }
    }

    fun readSessionOutput() {
        val interaction = _uiState.value.interaction ?: return
        if (interaction.busy) return
        _uiState.update { current ->
            current.copy(interaction = current.interaction?.copy(busy = true, errorMessage = null))
        }
        viewModelScope.launch {
            when (val result = sessionRepository.read(interaction.session.id)) {
                is AppResult.Failure -> _uiState.update { current ->
                    current.copy(
                        interaction = current.interaction?.copy(
                            busy = false,
                            errorMessage = result.error.userMessage,
                        ),
                    )
                }
                is AppResult.Success -> _uiState.update { current ->
                    val active = current.interaction ?: return@update current
                    current.copy(
                        interaction = active.copy(
                            busy = false,
                            output = (active.output + result.value.data).takeLast(MAX_OUTPUT_CHARS),
                        ),
                    )
                }
            }
        }
    }

    fun clearSessionOutput() {
        _uiState.update { current ->
            current.copy(interaction = current.interaction?.copy(output = ""))
        }
    }

    fun closeInteraction() {
        _uiState.update {
            it.copy(
                interactionCandidate = null,
                interactionAuthorizationConfirmed = false,
                interaction = null,
            )
        }
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 200_000

        fun factory(
            jobRepository: MetasploitJobRepository,
            sessionRepository: MetasploitSessionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OperationsViewModel(jobRepository, sessionRepository) as T
        }
    }
}
