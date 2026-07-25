package dev.mago.android.rpc

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.security.InMemoryRpcTokenStore
import dev.mago.android.rpc.service.RpcAuthService
import dev.mago.android.rpc.service.RpcCoreService
import dev.mago.android.security.SecretStore
import dev.mago.android.security.UnconfiguredSecretStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MetasploitConnectionRepositoryImplTest {
    @Test
    fun `does not call transport when RPC credentials are unconfigured`() = runTest {
        val transport = RecordingRpcTransport()
        val repository = createRepository(
            transport = transport,
            secretStore = UnconfiguredSecretStore(),
        )

        val result = repository.login()

        assertThat((result as AppResult.Failure).error.errorCode)
            .isEqualTo("RPC_CREDENTIALS_NOT_CONFIGURED")
        assertThat(transport.calls).isEmpty()
    }

    @Test
    fun `successful login stores only the returned token`() = runTest {
        val tokenStore = InMemoryRpcTokenStore()
        val repository = createRepository(
            transport = FixedRpcTransport(
                RpcValue.MapValue(
                    mapOf(
                        "result" to RpcValue.StringValue("success"),
                        "token" to RpcValue.StringValue("token-1"),
                    ),
                ),
            ),
            secretStore = FixedSecretStore("password".toCharArray()),
            tokenStore = tokenStore,
        )

        assertThat(repository.login()).isEqualTo(AppResult.Success(Unit))
        assertThat(tokenStore.get()).isEqualTo("token-1")
    }

    private fun createRepository(
        transport: RpcTransport,
        secretStore: SecretStore,
        tokenStore: InMemoryRpcTokenStore = InMemoryRpcTokenStore(),
    ) = MetasploitConnectionRepositoryImpl(
        authService = RpcAuthService(transport),
        coreService = RpcCoreService(transport),
        secretStore = secretStore,
        tokenStore = tokenStore,
    )
}

private class RecordingRpcTransport : RpcTransport {
    val calls = mutableListOf<RpcMethod>()
    override suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue>,
    ): AppResult<RpcValue> {
        calls += method
        return AppResult.Failure(error("UNEXPECTED_CALL"))
    }
}

private class FixedRpcTransport(private val value: RpcValue) : RpcTransport {
    override suspend fun call(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue>,
    ): AppResult<RpcValue> = AppResult.Success(value)
}

private class FixedSecretStore(private val password: CharArray) : SecretStore {
    override suspend fun saveRpcPassword(value: CharArray): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun readRpcPassword(): AppResult<CharArray?> = AppResult.Success(password.copyOf())
    override suspend fun clearRpcPassword(): AppResult<Unit> = AppResult.Success(Unit)
}

private fun error(code: String) = dev.mago.android.model.AppError(code, code)
