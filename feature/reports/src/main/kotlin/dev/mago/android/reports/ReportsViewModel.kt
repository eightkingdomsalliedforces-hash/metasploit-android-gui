package dev.mago.android.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.common.AppResult
import dev.mago.android.metasploit.MetasploitInventoryRepository
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.model.AppError
import dev.mago.android.reporting.ReportDocument
import dev.mago.android.reporting.ReportDocumentBuilder
import dev.mago.android.reporting.ReportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val format: ReportFormat = ReportFormat.JSON,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val previewSnapshot: ReportPreviewSnapshot? = null,
    val pendingDocument: ReportDocument? = null,
    val refreshErrorMessage: String? = null,
    val exportErrorMessage: String? = null,
    val saveMessage: String? = null,
) {
    val loading: Boolean
        get() = initialLoading || refreshing || exporting

    val activeWorkspace: String?
        get() = previewSnapshot?.workspace?.name

    val errorMessage: String?
        get() = exportErrorMessage ?: refreshErrorMessage
}

class ReportsViewModel(
    private val inventoryRepository: MetasploitInventoryRepository,
    private val moduleLocalStore: ModuleLocalStore,
    private val documentBuilder: ReportDocumentBuilder,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState = _uiState.asStateFlow()

    fun ensurePreviewLoaded() {
        val current = _uiState.value
        if (current.previewSnapshot != null || current.initialLoading || current.refreshing) return
        beginPreviewLoad(initial = true)
    }

    fun refreshPreview() {
        val current = _uiState.value
        if (current.initialLoading || current.refreshing || current.exporting) return
        beginPreviewLoad(initial = current.previewSnapshot == null)
    }

    fun selectFormat(format: ReportFormat) {
        if (_uiState.value.exporting) return
        _uiState.update {
            it.copy(
                format = format,
                exportErrorMessage = null,
                saveMessage = null,
            )
        }
    }

    fun requestExport() {
        val current = _uiState.value
        if (current.exporting || current.initialLoading || current.refreshing) return
        val preview = current.previewSnapshot
        if (preview == null) {
            _uiState.update {
                it.copy(
                    exportErrorMessage = "請先載入報告預覽",
                    saveMessage = null,
                )
            }
            return
        }
        val format = current.format
        _uiState.update {
            it.copy(
                exporting = true,
                pendingDocument = null,
                exportErrorMessage = null,
                saveMessage = null,
            )
        }
        viewModelScope.launch {
            val document = try {
                documentBuilder.build(preview.toSafeReportSnapshot(), format)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        exporting = false,
                        exportErrorMessage = "無法產生報告",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    exporting = false,
                    pendingDocument = document,
                    exportErrorMessage = null,
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
                exportErrorMessage = null,
            )
        }
    }

    fun onSaveFailed(message: String) {
        _uiState.update {
            it.copy(
                exportErrorMessage = message,
                saveMessage = null,
            )
        }
    }

    fun onPickerCancelled() {
        _uiState.update {
            it.copy(
                saveMessage = "已取消儲存",
                exportErrorMessage = null,
            )
        }
    }

    private fun beginPreviewLoad(initial: Boolean) {
        _uiState.update {
            it.copy(
                initialLoading = initial,
                refreshing = !initial,
                refreshErrorMessage = null,
                saveMessage = null,
            )
        }
        viewModelScope.launch {
            when (val result = readPreviewSnapshot()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        initialLoading = false,
                        refreshing = false,
                        previewSnapshot = result.value,
                        refreshErrorMessage = null,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        initialLoading = false,
                        refreshing = false,
                        refreshErrorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    private suspend fun readPreviewSnapshot(): AppResult<ReportPreviewSnapshot> {
        val workspace = when (val result = inventoryRepository.currentWorkspace()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val hosts = when (
            val result = inventoryRepository.hosts(workspace.name, RECORD_LIMIT, 0)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val services = when (
            val result = inventoryRepository.services(workspace.name, RECORD_LIMIT, 0)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val vulnerabilities = when (
            val result = inventoryRepository.vulnerabilities(workspace.name, RECORD_LIMIT, 0)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val executions = try {
            moduleLocalStore.executionHistory(RECORD_LIMIT)
        } catch (_: Exception) {
            return AppResult.Failure(
                AppError(
                    errorCode = "EXECUTION_HISTORY_UNAVAILABLE",
                    userMessage = "無法讀取模組執行紀錄",
                ),
            )
        }
        return AppResult.Success(
            ReportPreviewSnapshot(
                generatedAtEpochMillis = clock(),
                workspace = workspace,
                hosts = hosts,
                services = services,
                vulnerabilities = vulnerabilities,
                executions = executions,
            ),
        )
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
