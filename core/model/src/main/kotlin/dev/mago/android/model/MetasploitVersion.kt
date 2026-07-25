package dev.mago.android.model

import dev.mago.android.model.rpc.RpcValue

data class MetasploitVersion(
    val frameworkVersion: String,
    val rubyVersion: String?,
    val apiVersion: String?,
    val extraFields: Map<String, RpcValue>,
)
