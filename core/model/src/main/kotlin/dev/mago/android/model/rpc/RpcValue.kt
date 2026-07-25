package dev.mago.android.model.rpc

sealed interface RpcValue {
    data object Nil : RpcValue
    data class Bool(val value: Boolean) : RpcValue
    data class IntValue(val value: Long) : RpcValue
    data class FloatValue(val value: Double) : RpcValue
    data class StringValue(val value: String) : RpcValue
    data class BinaryValue(val value: ByteArray) : RpcValue
    data class ArrayValue(val value: List<RpcValue>) : RpcValue
    data class MapValue(val value: Map<String, RpcValue>) : RpcValue
}
