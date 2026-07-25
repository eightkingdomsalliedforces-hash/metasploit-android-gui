package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitVersion
import dev.mago.android.model.ServiceStatus

interface MetasploitConnectionRepository {
    suspend fun login(username: String = "msf"): AppResult<Unit>
    suspend fun version(): AppResult<MetasploitVersion>
    suspend fun health(): AppResult<ServiceStatus>
    fun logout()
}
