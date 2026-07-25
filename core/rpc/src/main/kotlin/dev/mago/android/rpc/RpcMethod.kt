package dev.mago.android.rpc

@JvmInline
value class RpcMethod(val value: String) {
    init {
        require(value.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+${'$'}"))) {
            "Invalid RPC method: $value"
        }
    }

    val requiresToken: Boolean
        get() = this != AUTH_LOGIN && this != HEALTH_CHECK

    companion object {
        val AUTH_LOGIN = RpcMethod("auth.login")
        val AUTH_LOGOUT = RpcMethod("auth.logout")
        val HEALTH_CHECK = RpcMethod("health.check")
        val CORE_VERSION = RpcMethod("core.version")
    }
}
