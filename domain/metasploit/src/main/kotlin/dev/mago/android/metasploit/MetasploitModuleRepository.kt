package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleLaunch
import dev.mago.android.model.MetasploitModuleRequest
import dev.mago.android.model.MetasploitModuleRunResult
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

interface MetasploitModuleRepository {
    suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>>
    suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo>
    suspend fun compatiblePayloads(type: MetasploitModuleType, name: String): AppResult<List<String>>
    suspend fun check(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch>
    suspend fun execute(request: MetasploitModuleRequest): AppResult<MetasploitModuleLaunch>
    suspend fun result(uuid: String): AppResult<MetasploitModuleRunResult>
}
