package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitModuleInfo
import dev.mago.android.model.MetasploitModuleSummary
import dev.mago.android.model.MetasploitModuleType

interface MetasploitModuleRepository {
    suspend fun list(type: MetasploitModuleType): AppResult<List<MetasploitModuleSummary>>
    suspend fun info(type: MetasploitModuleType, name: String): AppResult<MetasploitModuleInfo>
}
