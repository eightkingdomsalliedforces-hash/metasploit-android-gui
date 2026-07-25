package dev.mago.android.security

import java.util.concurrent.atomic.AtomicReference

class InMemoryRpcTokenStore : RpcTokenStore {
    private val token = AtomicReference<String?>(null)

    override fun get(): String? = token.get()

    override fun set(token: String) {
        require(token.isNotBlank()) { "RPC token must not be blank" }
        this.token.set(token)
    }

    override fun clear() {
        token.set(null)
    }
}
