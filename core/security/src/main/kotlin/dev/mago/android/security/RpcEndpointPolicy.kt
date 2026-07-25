package dev.mago.android.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class RpcEndpointPolicy {
    fun validate(raw: String): HttpUrl? {
        val url = raw.toHttpUrlOrNull() ?: return null
        return url.takeIf {
            it.scheme == "http" &&
                it.host == "127.0.0.1" &&
                it.port == 55552 &&
                it.encodedPath == "/api" &&
                it.encodedQuery == null &&
                it.fragment == null &&
                it.username.isEmpty() &&
                it.password.isEmpty()
        }
    }
}
