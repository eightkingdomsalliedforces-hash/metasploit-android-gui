package dev.mago.android.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read-only status tabs. This feature cannot send commands or stop operations. */
enum class OperationsTab { JOBS, SESSIONS }

data class OperationsUiState(
    val tab: OperationsTab = OperationsTab.JOBS,
    val jobs: List<MetasploitJobSummary> = emptyList(),
    val sessions: List<MetasploitSessionInfo> = emptyList(),
    val selectedJob: MetasploitJobInfo? = null,
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
    val errorMessage: String? = null,
    val refreshedAtEpochMillis: Long? = null,
)

class OperationsViewModel(
    private val repository: MetasploitOperationsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OperationsUiState())
    val uiState = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: OperationsTab) {
        mutableState.update { it.copy(tab = tab, errorMessage = null) }
    }

    fun refresh() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val jobsResult = repository.jobs()
            val sessionsResult = repository.sessions()
            val jobs = (jobsResult as? AppResult.Success)?.value
            val sessions = (sessionsResult as? AppResult.Success)?.value
            val error = when {
                jobsResult is AppResult.Failure -> jobsResult.error.userMessage
                sessionsResult is AppResult.Failure -> sessionsResult.error.userMessage
                else -> null
            }
            mutableState.update { current ->
                current.copy(
                    jobs = jobs ?: current.jobs,
                    sessions = sessions ?: current.sessions,
                    selectedJob = current.selectedJob?.takeIf { selected ->
                        (jobs ?: current.jobs).any { it.id == selected.id }
                    },
                    loading = false,
                    errorMessage = error,
                    refreshedAtEpochMillis = if (error == null) clock() else current.refreshedAtEpochMillis,
                )
            }
        }
    }

    fun selectJob(job: MetasploitJobSummary) {
        if (mutableState.value.detailLoading) return
        mutableState.update { it.copy(detailLoading = true, errorMessage = null, selectedJob = null) }
        viewModelScope.launch {
            when (val result = repository.jobInfo(job.id)) {
                is AppResult.Failure -> mutableState.update {
                    it.copy(detailLoading = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> mutableState.update {
                    it.copy(detailLoading = false, selectedJob = result.value)
                }
            }
        }
    }

    fun clearJobSelection() {
        mutableState.update { it.copy(selectedJob = null) }
    }

    companion object {
        fun factory(repository: MetasploitOperationsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OperationsViewModel(repository) as T
            }
    }
}
