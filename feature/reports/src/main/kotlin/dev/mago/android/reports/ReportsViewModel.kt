package dev.mago.android.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.reporting.ReportDocument
import dev.mago.android.reporting.ReportDocumentBuilder
import dev.mago.android.reporting.ReportFormat
import dev.mago.android.reporting.ReportSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val format: ReportFormat = ReportFormat.JSON,
    val loading: Boolean = false,
    val activeWorkspace: String? = null,
    val pendingDocument: ReportDocument? = null,
    val errorMessage: String? = null,
    val saveMessage: String? = null,
)

class ReportsViewModel(
    private val inventoryRepository: MetasploitInventoryRepository,
    private val moduleLocalStore: ModuleLocalStore,
    private val documentBuilder: ReportDocumentBuilder,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState = _uiState.asStateFlow()

    fun selectFormat(format: ReportFormat) {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(format = format, errorMessage = null, saveMessage = null) }
    }

    fun requestExport() {
        if (_uiState.value.loading) return
        val format = _uiState.value.format
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    pendingDocument = null,
                    errorMessage = null,
                    saveMessage = null,
                )
            }

            val workspace = when (val result = inventoryRepository.currentWorkspace()) {
                is AppResult.Failure -> {
                    fail(result.error.userMessage)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            val hosts = when (val result = inventoryRepository.hosts(workspace.name, RECORD_LIMIT, 0)) {
                is AppResult.Failure -> {
                    fail(result.error.userMessage)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            val services = when (val result = inventoryRepository.services(workspace.name, RECORD_LIMIT, 0)) {
                is AppResult.Failure -> {
                    fail(result.error.userMessage)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            val vulnerabilities = when (
                val result = inventoryRepository.vulnerabilities(workspace.name, RECORD_LIMIT, 0)
            ) {
                is AppResult.Failure -> {
                    fail(result.error.userMessage)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            val executions = try {
                moduleLocalStore.executionHistory(RECORD_LIMIT)
            } catch (exception: Exception) {
                fail(exception.message ?: "無法讀取模組執行紀錄")
                return@launch
            }

            val document = try {
                documentBuilder.build(
                    ReportSnapshot(
                        generatedAtEpochMillis = clock(),
                        workspace = workspace,
                        hosts = hosts,
                        services = services,
                        vulnerabilities = vulnerabilities,
                        executions = executions,
                    ),
                    format,
                )
            } catch (exception: Exception) {
                fail(exception.message ?: "無法產生報告")
                return@launch
            }

            _uiState.update {
                it.copy(
                    loading = false,
                    activeWorkspace = workspace.name,
                    pendingDocument = document,
                    errorMessage = null,
                )
            }
        }
    }

    fun consumePendingDocument(id: String) {
        _uiState.update { current ->
            if (current.pendingDocument?.id == id) current.copy(pendingDocument = null) else current
        }
    }

    fun onSaveCompleted(fileName: String) {
        _uiState.update {
            it.copy(
                saveMessage = "已儲存 $fileName",
                errorMessage = null,
            )
        }
    }

    fun onSaveFailed(message: String) {
        _uiState.update { it.copy(errorMessage = message, saveMessage = null) }
    }

    fun onPickerCancelled() {
        _uiState.update { it.copy(saveMessage = "已取消儲存", errorMessage = null) }
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(
                loading = false,
                pendingDocument = null,
                errorMessage = message,
                saveMessage = null,
            )
        }
    }

    companion object {
        const val RECORD_LIMIT = 100

        fun factory(
            inventoryRepository: MetasploitInventoryRepository,
            moduleLocalStore: ModuleLocalStore,
            documentBuilder: ReportDocumentBuilder,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReportsViewModel(inventoryRepository, moduleLocalStore, documentBuilder) as T
        }
    }
}
