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

        val MODULE_EXPLOITS = RpcMethod("module.exploits")
        val MODULE_AUXILIARY = RpcMethod("module.auxiliary")
        val MODULE_POST = RpcMethod("module.post")
        val MODULE_PAYLOADS = RpcMethod("module.payloads")
        val MODULE_ENCODERS = RpcMethod("module.encoders")
        val MODULE_NOPS = RpcMethod("module.nops")
        val MODULE_EVASION = RpcMethod("module.evasion")
        val MODULE_INFO = RpcMethod("module.info")

        val CONSOLE_CREATE = RpcMethod("console.create")
        val CONSOLE_READ = RpcMethod("console.read")
        val CONSOLE_WRITE = RpcMethod("console.write")
        val CONSOLE_DESTROY = RpcMethod("console.destroy")
    }
}
