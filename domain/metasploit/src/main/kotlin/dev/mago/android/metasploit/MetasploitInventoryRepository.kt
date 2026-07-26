package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitHostRecord
import dev.mago.android.model.MetasploitServiceRecord
import dev.mago.android.model.MetasploitVulnerabilityRecord
import dev.mago.android.model.MetasploitWorkspaceSummary

interface MetasploitInventoryRepository {
    suspend fun workspaces(): AppResult<List<MetasploitWorkspaceSummary>>

    suspend fun hosts(
        workspace: String,
        limit: Int = 100,
        offset: Int = 0,
    ): AppResult<List<MetasploitHostRecord>>

    suspend fun services(
        workspace: String,
        limit: Int = 100,
        offset: Int = 0,
    ): AppResult<List<MetasploitServiceRecord>>

    suspend fun vulnerabilities(
        workspace: String,
        limit: Int = 100,
        offset: Int = 0,
    ): AppResult<List<MetasploitVulnerabilityRecord>>
}
