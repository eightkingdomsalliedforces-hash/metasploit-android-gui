package dev.mago.android.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitConsoleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TerminalUiState(
    val consoleId: String? = null,
    val prompt: String = "msf > ",
    val output: String = "",
    val input: String = "",
    val busy: Boolean = false,
    val connecting: Boolean = false,
    val errorMessage: String? = null,
)

class TerminalViewModel(
    private val repository: MetasploitConsoleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(connecting = true, errorMessage = null) }
            when (val result = repository.ensureConsole()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(connecting = false, errorMessage = result.error.userMessage)
                }
                is AppResult.Success -> applySnapshot(result.value.id, result.value.prompt, result.value.busy, result.value.output)
            }
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                readOnce()
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setInput(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun send() {
        val command = _uiState.value.input
        if (command.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.write(command)) {
                is AppResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.userMessage) }
                is AppResult.Success -> {
                    _uiState.update { it.copy(input = "", errorMessage = null) }
                    readOnce()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { readOnce() }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "") }
    }

    private suspend fun readOnce() {
        when (val result = repository.read()) {
            is AppResult.Failure -> _uiState.update {
                it.copy(connecting = false, errorMessage = result.error.userMessage)
            }
            is AppResult.Success -> applySnapshot(
                result.value.id,
                result.value.prompt,
                result.value.busy,
                result.value.output,
            )
        }
    }

    private fun applySnapshot(id: String, prompt: String, busy: Boolean, chunk: String) {
        _uiState.update { current ->
            val combined = (current.output + chunk).takeLast(MAX_OUTPUT_CHARS)
            current.copy(
                consoleId = id,
                prompt = prompt,
                output = combined,
                busy = busy,
                connecting = false,
                errorMessage = null,
            )
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 750L
        private const val MAX_OUTPUT_CHARS = 200_000

        fun factory(repository: MetasploitConsoleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TerminalViewModel(repository) as T
            }
    }
}
