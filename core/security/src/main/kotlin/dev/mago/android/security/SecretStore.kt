package dev.mago.android.security

import dev.mago.android.common.AppResult

interface SecretStore {
    suspend fun saveRpcPassword(value: CharArray): AppResult<Unit>
    suspend fun readRpcPassword(): AppResult<CharArray?>
    suspend fun clearRpcPassword(): AppResult<Unit>
}

interface RpcTokenStore {
    fun get(): String?
    fun set(token: String)
    fun clear()
}
