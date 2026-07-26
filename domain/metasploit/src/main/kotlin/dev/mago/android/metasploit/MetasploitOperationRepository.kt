package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitJobInfo
import dev.mago.android.model.MetasploitJobSummary
import dev.mago.android.model.MetasploitSessionRead
import dev.mago.android.model.MetasploitSessionSummary

interface MetasploitJobRepository {
    suspend fun list(): AppResult<List<MetasploitJobSummary>>
    suspend fun info(id: String): AppResult<MetasploitJobInfo>
    suspend fun stop(id: String, userConfirmed: Boolean): AppResult<Unit>
}

interface MetasploitSessionRepository {
    suspend fun list(): AppResult<List<MetasploitSessionSummary>>
    suspend fun stop(id: Int, userConfirmed: Boolean): AppResult<Unit>
    suspend fun read(id: Int): AppResult<MetasploitSessionRead>
    suspend fun write(id: Int, input: String, userConfirmed: Boolean): AppResult<Unit>
}
