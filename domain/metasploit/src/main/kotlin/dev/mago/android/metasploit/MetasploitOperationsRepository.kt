package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionInfo

interface MetasploitOperationsRepository {
    suspend fun jobs(): AppResult<List<MetasploitJobSummary>>
    suspend fun jobInfo(jobId: Int): AppResult<MetasploitJobInfo>
    suspend fun sessions(): AppResult<List<MetasploitSessionInfo>>
}
