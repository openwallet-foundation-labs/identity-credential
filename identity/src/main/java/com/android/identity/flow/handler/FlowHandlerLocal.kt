package com.android.identity.flow.handler

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MajorType
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.flow.environment.FlowEnvironment
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.random.Random

/**
 * [FlowHandler] implementation that dispatches flow getters and methods
 * to local object calls. This is for use in server environment and for testing.
 *
 * Each flow state should be registered with [FlowHandlerLocal.Builder] using
 * generated State.register method.
 *
 * [handleGet] and [handlePost] methods are useful to hook this object to
 * an HTTP server GET and POST handlers.
 */
class FlowHandlerLocal(
    private val environment: FlowEnvironment,
    private val cipher: SimpleCipher,
    private val flowMap: Map<String, FlowItem<*>>
) : FlowHandler {

    interface SimpleCipher {
        fun encrypt(plaintext: ByteArray): ByteArray
        fun decrypt(ciphertext: ByteArray): ByteArray
    }

    class Builder {
        private val flowMap = mutableMapOf<String, FlowItem<*>>()

        fun<StateT> addFlow(
            flow: String,
            stateSerializer: (StateT) -> DataItem,
            stateDeserializer: (DataItem) -> StateT,
            block: FlowBuilder<StateT>.() -> Unit) {
            val builder = FlowBuilder(stateSerializer, stateDeserializer)
            builder.block()
            flowMap[flow] = builder.build()
        }

        fun build(
            cipher: SimpleCipher,
            environment: FlowEnvironment = FlowEnvironment.EMPTY
        ): FlowHandlerLocal {
            return FlowHandlerLocal(environment, cipher, flowMap.toMap())
        }

        fun build(
            key: ByteArray,
            environment: FlowEnvironment = FlowEnvironment.EMPTY
        ): FlowHandlerLocal {
            return FlowHandlerLocal(environment, AesGcmCipher(key), flowMap.toMap())
        }
    }

    class FlowBuilder<StateT>(
        private val stateSerializer: (StateT) -> DataItem,
        private val stateDeserializer: (DataItem) -> StateT
    ) {
        private val getMap = mutableMapOf<String, suspend (FlowEnvironment) -> DataItem>()
        private val postMap = mutableMapOf<String, suspend (FlowEnvironment, SimpleCipher, StateT, List<DataItem>) -> DataItem>()

        fun get(method: String, impl: suspend (FlowEnvironment) -> DataItem) {
            getMap[method] = impl
        }

        fun post(method: String, impl: suspend (FlowEnvironment, SimpleCipher, StateT, List<DataItem>) -> DataItem) {
            postMap[method] = impl
        }

        internal fun build(): FlowItem<StateT> {
            return FlowItem(getMap.toMap(), postMap.toMap(), stateSerializer, stateDeserializer)
        }
    }

    override suspend fun get(flow: String, method: String): DataItem {
        val flowItem = flowMap[flow] ?: throw NotFoundException("flow $flow not found")
        return flowItem.handleGet(environment, method)
    }

    override suspend fun post(flow: String, method: String, args: List<DataItem>): List<DataItem> {
        val flowItem = flowMap[flow] ?: throw NotFoundException("flow $flow not found")
        return flowItem.handlePost(this, method, args)
    }

    suspend fun handleGet(flow: String, method: String): ByteString {
        return ByteString(Cbor.encode(get(flow, method)))
    }

    suspend fun handlePost(flow: String, method: String, data: ByteString): ByteString {
        val result = post(flow, method, Cbor.decode(data.toByteArray()).asArray)
        val builder = ByteStringBuilder()
        Cbor.encodeLength(builder, MajorType.ARRAY, result.size)
        result.forEach { it.encode(builder) }
        return builder.toByteString()
    }

    class FlowItem<StateT>(
        private val getMap: Map<String, suspend (FlowEnvironment) -> DataItem>,
        private val postMap: Map<String, suspend (FlowEnvironment, SimpleCipher, StateT, List<DataItem>) -> DataItem>,
        private val stateSerializer: (StateT) -> DataItem,
        private val stateDeserializer: (DataItem) -> StateT
    ) {
        internal suspend fun handleGet(environment: FlowEnvironment, method: String): DataItem {
            val handler = getMap[method] ?: throw NotFoundException("getter $method not found")
            return handler(environment)
        }

        internal suspend fun handlePost(
            owner: FlowHandlerLocal, method: String, args: List<DataItem>): List<DataItem> {
            val handler = postMap[method] ?: throw Exception("operation $method not found")
            val stateBlob = args[0].asBstr
            val state = stateDeserializer(
                if (stateBlob.isEmpty()) {
                    args[0]  // empty bstr
                } else {
                    Cbor.decode(owner.cipher.decrypt(stateBlob))
                })
            val result = handler(owner.environment, owner.cipher, state, args.subList(1, args.size))
            return listOf(Bstr(owner.cipher.encrypt(Cbor.encode(stateSerializer(state)))), result)
        }
    }

    internal class AesGcmCipher(val key: ByteArray) : SimpleCipher {
        private val alg = when (key.size) {
            16 -> Algorithm.A128GCM
            24 -> Algorithm.A192GCM
            32 -> Algorithm.A256GCM
            else -> throw IllegalArgumentException("key length must be 16, 24, or 32 bytes")
        }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            val ciphertext = ByteStringBuilder()
            val iv = Random.Default.nextBytes(12)
            ciphertext.append(iv)
            ciphertext.append(Crypto.encrypt(alg, key, iv, plaintext))
            return ciphertext.toByteString().toByteArray()
        }

        override fun decrypt(ciphertext: ByteArray): ByteArray {
            val iv = ByteArray(12)
            ciphertext.copyInto(iv, endIndex = iv.size)
            try {
                return Crypto.decrypt(
                    alg, key, iv,
                    ciphertext.sliceArray(iv.size..ciphertext.lastIndex)
                )
            } catch (ex: IllegalStateException) {
                throw StateTamperedException()
            }
        }
    }

    class NotFoundException(message: String) : RuntimeException(message)

    class StateTamperedException : RuntimeException()
}