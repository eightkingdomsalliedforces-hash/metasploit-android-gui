package dev.mago.android.termux

import dev.mago.android.model.bridge.BridgeAction

class BridgeActionTimeoutPolicy {
    fun timeoutMillis(action: BridgeAction): Long = when (action) {
        BridgeAction.UPDATE_PACKAGES,
        BridgeAction.INSTALL_DEPENDENCIES,
        BridgeAction.INSTALL_METASPLOIT,
        BridgeAction.REPAIR_METASPLOIT,
        BridgeAction.UPDATE_METASPLOIT,
        -> INSTALL_TIMEOUT_MILLIS

        BridgeAction.INITIALIZE_DATABASE,
        BridgeAction.START_SERVICES,
        BridgeAction.STOP_SERVICES,
        BridgeAction.START_RPC,
        BridgeAction.STOP_RPC,
        -> SERVICE_TIMEOUT_MILLIS

        else -> DEFAULT_TIMEOUT_MILLIS
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val SERVICE_TIMEOUT_MILLIS = 5L * 60L * 1000L
        const val INSTALL_TIMEOUT_MILLIS = 45L * 60L * 1000L
    }
}
