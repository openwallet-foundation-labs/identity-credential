package org.multipaz.flow.handler

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * An object that serializes and deserializes exceptions marked with FlowException annotations.
 * All exceptions must be registered using generated ExceptionName.register method both on the
 * client and on the server.
 */
class FlowExceptionMap private constructor(
    private val byClass: Map<KClass<out Throwable>, Item<*>>,
    private val byId: Map<String, Item<*>>
) {
    class Builder {
        private val byClass = mutableMapOf<KClass<out Throwable>, Item<*>>()
        private val byId = mutableMapOf<String, Item<*>>()

        init {
            // This exception is the part of the framework and is always supported.
            // TODO: consider adding some Kotlin exceptions (they'd need cbor serialization).
            InvalidRequestException.register(this)
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

        fun build(): FlowExceptionMap {
            return FlowExceptionMap(
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

    fun exceptionReturn(state: DataItem, exception: Throwable): List<DataItem> {
        val item = byClass[exception::class] ?: throw exception
        return listOf(
            state,
            FlowReturnCode.EXCEPTION.ordinal.toDataItem(),
            item.exceptionId.toDataItem(),
            item.serialize(exception)
        )
    }

    fun handleExceptionReturn(returnList: List<DataItem>) {
        throw (byId[returnList[2].asTstr]!!.deserializer)(returnList[3])
    }
}