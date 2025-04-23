package org.multipaz.rpc.handler

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.toDataItem
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * An object that serializes and deserializes exceptions marked with FlowException annotations.
 * All exceptions must be registered using generated ExceptionName.register method both on the
 * client and on the server.
 */
class RpcExceptionMap private constructor(
    private val byClass: Map<KClass<out Throwable>, Item<*>>,
    private val byId: Map<String, Item<*>>
) {
    class Builder {
        private val byClass = mutableMapOf<KClass<out Throwable>, Item<*>>()
        private val byId = mutableMapOf<String, Item<*>>()

        init {
            // These exceptions are the part of the framework and are always supported.
            // TODO: consider adding some Kotlin exceptions (they'd need cbor serialization).
            InvalidRequestException.register(this)
            RpcAuthException.register(this)
        }

        fun <ExceptionT : Throwable> addException(
            exceptionId: String,
            serializer: (ExceptionT) -> DataItem,
            deserializer: (DataItem) -> ExceptionT,
            exceptionClasses: List<KClass<out ExceptionT>>,
        ) {
            val item = Item(
                exceptionId = exceptionId,
                exceptionClass = exceptionClasses[0],
                serializer = serializer,
                deserializer = deserializer
            )
            check(!byId.contains(exceptionId))
            byId[exceptionId] = item
            for (exceptionClass in exceptionClasses) {
                check(!byClass.contains(exceptionClass))
                byClass[exceptionClass] = item
            }
        }

        fun build(): RpcExceptionMap {
            return RpcExceptionMap(
                byClass = byClass.toMap(),
                byId = byId.toMap()
            )
        }
    }

    internal class Item<ExceptionT : Throwable>(
        val exceptionId: String,
        val exceptionClass: KClass<out ExceptionT>,
        val serializer: (ExceptionT) -> DataItem,
        val deserializer: (DataItem) -> ExceptionT
    ) {
        fun serialize(exception: Throwable): DataItem {
            return serializer(exceptionClass.cast(exception))
        }
    }

    fun exceptionReturn(
        state: DataItem,
        exception: Throwable,
        nonce: ByteString? = null
    ): List<DataItem> {
        val item = byClass[exception::class] ?: throw exception
        return listOf(
            state,
            if (nonce == null) Simple.NULL else Bstr(nonce.toByteArray()),
            RpcReturnCode.EXCEPTION.ordinal.toDataItem(),
            item.exceptionId.toDataItem(),
            item.serialize(exception)
        )
    }

    fun handleExceptionReturn(returnList: List<DataItem>) {
        val code = returnList[2].asNumber
        if (code != RpcReturnCode.EXCEPTION.ordinal.toLong()) {
            if (code == RpcReturnCode.NONCE_RETRY.ordinal.toLong()) {
                throw RpcAuthException(
                    "RPC authorization is absent or mismatched between the client and the back-end",
                    RpcAuthError.CONFIG
                )
            }
            throw IllegalStateException("Unknown RPC result code: $code")
        }
        throw (byId[returnList[3].asTstr]!!.deserializer)(returnList[4])
    }
}