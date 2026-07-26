package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

interface MetasploitModuleRepository {
    suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>>

    suspend fun search(query: String): AppResult<List<MetasploitModuleSummary>> = AppResult.Failure(
        AppError(
            errorCode = "MODULE_SEARCH_NOT_SUPPORTED",
            userMessage = "目前的模組來源不支援搜尋",
            retryable = false,
        ),
    )

    suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo>

    suspend fun compatiblePayloads(type: MetasploitModuleType, name: String): AppResult<List<String>>

    suspend fun compatiblePayloads(
        type: MetasploitModuleType,
        name: String,
        target: Int,
    ): AppResult<List<String>> = compatiblePayloads(type, name)

    suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch>
    suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch>
    suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult>
}
