package dev.mago.android.metasploit

import dev.mago.android.common.AppResult
import dev.mago.android.model.MetasploitConsoleSnapshot

interface MetasploitConsoleRepository {
    suspend fun ensureConsole(): AppResult<MetasploitConsoleSnapshot>
    suspend fun read(): AppResult<MetasploitConsoleSnapshot>
    suspend fun write(command: String): AppResult<Unit>
    suspend fun destroy(): AppResult<Unit>
}
