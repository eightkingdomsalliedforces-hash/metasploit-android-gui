package dev.mago.android.rpc

import dev.mago.android.model.rpc.RpcValue
import java.io.IOException
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.value.Value
import org.msgpack.value.ValueType

class RpcCodecException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class MessagePackRpcCodec {
    fun encodeRequest(
        method: RpcMethod,
        token: String?,
        arguments: List<RpcValue> = emptyList(),
    ): ByteArray {
        if (method.requiresToken && token.isNullOrBlank()) {
            throw RpcCodecException("RPC_TOKEN_REQUIRED", "RPC method ${method.value} requires a token")
        }
        return MessagePack.newDefaultBufferPacker().use { packer ->
            val count = 1 + arguments.size + if (method.requiresToken) 1 else 0
            packer.packArrayHeader(count)
            packer.packString(method.value)
            if (method.requiresToken) packer.packString(requireNotNull(token))
            arguments.forEach { argument -> packer.packRpcValue(argument) }
            packer.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): RpcValue = try {
        MessagePack.newDefaultUnpacker(bytes).use { unpacker ->
            val value = convert(unpacker.unpackValue())
            if (unpacker.hasNext()) {
                throw RpcCodecException("RPC_TRAILING_DATA", "MessagePack response contains trailing values")
            }
            value
        }
    } catch (error: RpcCodecException) {
        throw error
    } catch (error: Exception) {
        throw RpcCodecException("RPC_DECODE_FAILED", "Unable to decode MessagePack response", error)
    }

    private fun MessagePacker.packRpcValue(value: RpcValue) {
        when (value) {
            RpcValue.Nil -> packNil()
            is RpcValue.Bool -> packBoolean(value.value)
            is RpcValue.IntValue -> packLong(value.value)
            is RpcValue.FloatValue -> packDouble(value.value)
            is RpcValue.StringValue -> packString(value.value)
            is RpcValue.BinaryValue -> {
                packBinaryHeader(value.value.size)
                writePayload(value.value)
            }
            is RpcValue.ArrayValue -> {
                packArrayHeader(value.value.size)
                value.value.forEach { item -> packRpcValue(item) }
            }
            is RpcValue.MapValue -> {
                packMapHeader(value.value.size)
                value.value.forEach { (key, item) ->
                    packString(key)
                    packRpcValue(item)
                }
            }
        }
    }

    private fun convert(value: Value): RpcValue = when (value.valueType) {
        ValueType.NIL -> RpcValue.Nil
        ValueType.BOOLEAN -> RpcValue.Bool(value.asBooleanValue().getBoolean())
        ValueType.INTEGER -> RpcValue.IntValue(value.asIntegerValue().toLong())
        ValueType.FLOAT -> RpcValue.FloatValue(value.asFloatValue().toDouble())
        ValueType.STRING -> RpcValue.StringValue(value.asStringValue().asString())
        ValueType.BINARY -> RpcValue.BinaryValue(value.asBinaryValue().asByteArray())
        ValueType.ARRAY -> RpcValue.ArrayValue(value.asArrayValue().list().map(::convert))
        ValueType.MAP -> {
            val converted = LinkedHashMap<String, RpcValue>()
            value.asMapValue().map().forEach { (key, item) ->
                converted[key.mapKey()] = convert(item)
            }
            RpcValue.MapValue(converted)
        }
        ValueType.EXTENSION -> throw RpcCodecException(
            "RPC_UNSUPPORTED_VALUE",
            "MessagePack extension values are not supported",
        )
    }

    private fun Value.mapKey(): String = when {
        isStringValue -> asStringValue().asString()
        isIntegerValue -> asIntegerValue().toLong().toString()
        else -> throw RpcCodecException(
            "RPC_UNSUPPORTED_MAP_KEY",
            "RPC response map keys must be strings or integers",
        )
    }
}
