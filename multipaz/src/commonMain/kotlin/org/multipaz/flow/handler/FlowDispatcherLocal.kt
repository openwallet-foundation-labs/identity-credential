package org.multipaz.flow.handler

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.toDataItem
import org.multipaz.flow.server.FlowEnvironment
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * [FlowDispatcher] implementation that dispatches flow method calls to
 * to local object calls. This is for use in server environment and for testing.
 *
 * Each flow state should be registered with [FlowDispatcherLocal.Builder] using
 * generated State.register method.
 */
class FlowDispatcherLocal private constructor(
    val environment: FlowEnvironment,
    private val cipher: SimpleCipher,
    private val flowMap: Map<String, FlowItem<*>>,
    override val exceptionMap: FlowExceptionMap
) : FlowDispatcher {
    private val stateMap = flowMap.toList().mapIndexed { _, pair ->
        Pair(pair.second.stateClass, pair.first)
    }.toMap()

    class Builder {
        private val flowMap = mutableMapOf<String, FlowItem<*>>()

        fun<StateT: Any> addFlow(
            flowName: String,
            stateClass: KClass<StateT>,
            stateSerializer: (StateT) -> DataItem,
            stateDeserializer: (DataItem) -> StateT,
            block: FlowBuilder<StateT>.() -> Unit) {
            val builder = FlowBuilder(stateClass, stateSerializer, stateDeserializer)
            builder.block()
            flowMap[flowName] = builder.build()
        }

        fun build(
            environment: FlowEnvironment,
            cipher: SimpleCipher,
            exceptionMap: FlowExceptionMap
        ): FlowDispatcherLocal {
            return FlowDispatcherLocal(environment, cipher, flowMap.toMap(), exceptionMap)
        }
    }

    class FlowBuilder<StateT: Any>(
        private val stateClass: KClass<StateT>,
        private val stateSerializer: (StateT) -> DataItem,
        private val stateDeserializer: (DataItem) -> StateT
    ) {
        private var creatable = false
        private val postMap = mutableMapOf<String, suspend (FlowDispatcherLocal, StateT, List<DataItem>) -> DataItem>()

        fun dispatch(method: String, impl: suspend (FlowDispatcherLocal, StateT, List<DataItem>) -> DataItem) {
            postMap[method] = impl
        }

        fun creatable() {
            this.creatable = true
        }

        internal fun build(): FlowItem<StateT> {
            return FlowItem(stateClass, creatable, postMap.toMap(), stateSerializer,
                stateDeserializer)
        }
    }

    override suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem> {
        val flowItem = flowMap[flow] ?:
            throw UnsupportedOperationException("flow $flow not found")
        return flowItem.dispatch(this, method, args)
    }

    fun decodeStateParameter(stateParameter: DataItem): Any {
        val stateArray = stateParameter.asArray
        val flowItem = flowMap[stateArray[0].asTstr]!!
        return (flowItem.stateDeserializer)(Cbor.decode(cipher.decrypt(stateArray[1].asBstr)))
    }

    fun encodeStateResult(result: Any, joinName: String?): DataItem {
        val flowName = stateMap[result::class]
            ?: throw IllegalStateException("${result::class.qualifiedName} was not registered")
        return CborArray(mutableListOf(
            Tstr(flowName),
            Tstr(joinName ?: ""),
            Bstr(cipher.encrypt(Cbor.encode(flowMap[flowName]!!.serialize(result))))
        ))
    }

    internal class FlowItem<StateT: Any>(
        internal val stateClass: KClass<StateT>,
        private val creatable: Boolean,
        private val handlerMap: Map<String, suspend (FlowDispatcherLocal, StateT, List<DataItem>) -> DataItem>,
        internal val stateSerializer: (StateT) -> DataItem,
        internal val stateDeserializer: (DataItem) -> StateT
    ) {
        suspend fun dispatch(
            owner: FlowDispatcherLocal,
            method: String,
            args: List<DataItem>
        ): List<DataItem> {
            val handler = handlerMap[method]
                ?: throw UnsupportedOperationException("operation $method not found")
            val stateBlob = args[0].asBstr
            val cipher = owner.cipher
            val decryptedState = if (stateBlob.isEmpty()) {
                byteArrayOf()
            } else {
                cipher.decrypt(stateBlob)
            }
            val state = stateDeserializer(
                if (stateBlob.isEmpty()) {
                    if (creatable) {
                        args[0]  // empty bstr
                    } else {
                        throw IllegalStateException("this flow is not creatable")
                    }
                } else {
                    Cbor.decode(decryptedState)
                })
            try {
                val result = handler(owner, state, args.subList(1, args.size))
                val newStateBlob = stateDataItem(cipher, args[0], decryptedState, state)
                return listOf(newStateBlob, FlowReturnCode.RESULT.ordinal.toDataItem(), result)
            } catch (err: Throwable) {
                val newStateBlob = stateDataItem(cipher, args[0], decryptedState, state)
                return owner.exceptionMap.exceptionReturn(newStateBlob, err)
            }
        }

        private fun stateDataItem(
            cipher: SimpleCipher,
            previous: DataItem,
            previousDecryptedState: ByteArray,
            newState: StateT
        ): DataItem {
            val newDecryptedState = Cbor.encode(stateSerializer(newState))
            // Don't re-encrypt if the state did not change
            return if (newDecryptedState contentEquals previousDecryptedState) {
                previous
            } else {
                Bstr(cipher.encrypt(newDecryptedState))
            }
        }

        internal fun serialize(value: Any): DataItem {
            return stateSerializer(stateClass.cast(value))
        }
    }
}