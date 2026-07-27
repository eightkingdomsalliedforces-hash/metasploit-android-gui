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
        val MODULE_OPTIONS = RpcMethod("module.options")
        val MODULE_SEARCH = RpcMethod("module.search")
        val MODULE_COMPATIBLE_PAYLOADS = RpcMethod("module.compatible_payloads")
        val MODULE_TARGET_COMPATIBLE_PAYLOADS = RpcMethod("module.target_compatible_payloads")
        val MODULE_COMPATIBLE_EVASION_PAYLOADS = RpcMethod("module.compatible_evasion_payloads")
        val MODULE_TARGET_COMPATIBLE_EVASION_PAYLOADS = RpcMethod("module.target_compatible_evasion_payloads")
        val MODULE_CHECK = RpcMethod("module.check")
        val MODULE_EXECUTE = RpcMethod("module.execute")
        val MODULE_RESULTS = RpcMethod("module.results")
        val MODULE_RUNNING_STATS = RpcMethod("module.running_stats")
        val MODULE_ACK = RpcMethod("module.ack")

        val JOB_LIST = RpcMethod("job.list")
        val JOB_INFO = RpcMethod("job.info")
        val JOB_STOP = RpcMethod("job.stop")
        val SESSION_LIST = RpcMethod("session.list")
        val SESSION_STOP = RpcMethod("session.stop")

        val DB_WORKSPACES = RpcMethod("db.workspaces")
        val DB_CURRENT_WORKSPACE = RpcMethod("db.current_workspace")
        val DB_ADD_WORKSPACE = RpcMethod("db.add_workspace")
        val DB_SET_WORKSPACE = RpcMethod("db.set_workspace")
        val DB_HOSTS = RpcMethod("db.hosts")
        val DB_SERVICES = RpcMethod("db.services")
        val DB_VULNS = RpcMethod("db.vulns")

        val CONSOLE_CREATE = RpcMethod("console.create")
        val CONSOLE_READ = RpcMethod("console.read")
        val CONSOLE_WRITE = RpcMethod("console.write")
        val CONSOLE_DESTROY = RpcMethod("console.destroy")
    }
}
