package org.multipaz.flow.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.util.Logger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation for [FlowNotifications] that also implements [FlowPoll] interface to handle
 * long poll method for notification propagation.
 */
class FlowNotificationsLocalPoll(private val cipher: SimpleCipher): FlowNotifications, FlowPoll {
    private val mutex = Mutex()
    private val notificationFlow = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var current = mutableMapOf<FlowNotificationKey, MutableSet<DataItem>>()
    private var last = mutableMapOf<FlowNotificationKey, MutableSet<DataItem>>()
    private var consumed = mutableMapOf<String, ConsumedItem>()
    private var lastConsumed = mutableMapOf<String, ConsumedItem>()
    private var lastRotation = Clock.System.now()

    companion object {
        private val TAG = "FlowNotificationsLocalPoll"
    }

    override suspend fun emit(flowName: String, state: DataItem, notification: DataItem) {
        val ref = FlowNotificationKey(flowName, state)
        val dropped = mutableSetOf<String>()
        mutex.withLock {
            maybeRotate(dropped)
            current.getOrPut(ref) { mutableSetOf() }.add(notification)
        }
        notificationFlow.tryEmit(true)
        logDropped(dropped)
    }

    @OptIn(FlowPreview::class, ExperimentalEncodingApi::class)
    override suspend fun poll(consumeToken: String, flows: List<FlowPoll.PollKey>): FlowPoll.PollResult {
        val dropped = mutableSetOf<String>()
        val start = Clock.System.now()
        Logger.i(TAG, "polling flows: ${flows.map { flowRef -> flowRef.flowName }.joinToString(",")}")
        do {
            var result: FlowPoll.PollResult? = null
            var notificationKey: FlowNotificationKey? = null
            mutex.withLock {
                val consumedItem = consumed.remove(consumeToken) ?: lastConsumed.remove(consumeToken)
                if (consumedItem != null) {
                    current[consumedItem.flowNotificationKey]?.remove(consumedItem.notification)
                    last[consumedItem.flowNotificationKey]?.remove(consumedItem.notification)
                }
                maybeRotate(dropped)
                for ((index, pollKey) in flows.withIndex()) {
                    val flowRef = FlowNotificationKey(
                        pollKey.flowName,
                        Cbor.decode(cipher.decrypt(pollKey.opaqueState.asBstr))
                    )
                    val currentList = current[flowRef]
                    val lastList = last[flowRef]
                    val notification = if (!currentList.isNullOrEmpty()) {
                        currentList.first()
                    } else if (!lastList.isNullOrEmpty()) {
                        lastList.first()
                    } else {
                        continue
                    }
                    Logger.i(TAG, "pushing notification for flow ${flowRef.flowName}")
                    result = FlowPoll.PollResult(
                        consumeToken = Base64.encode(Random.Default.nextBytes(15)),
                        index = index,
                        notification = notification
                    )
                    notificationKey = flowRef
                    break
                }
            }
            logDropped(dropped)
            if (result != null) {
                mutex.withLock {
                    lastConsumed.put(result!!.consumeToken, ConsumedItem(
                        flowNotificationKey = notificationKey!!,
                        notification = result!!.notification
                    ))
                }
                return result!!
            }
            try {
                notificationFlow.timeout(1.minutes).take(1).collect {}
            } catch(exception: TimeoutCancellationException) {
                // ignore
            }
        } while (Clock.System.now() - start < 3.minutes)
        throw FlowPoll.TimeoutException()
    }

    private fun maybeRotate(dropped: MutableSet<String>) {
        val now = Clock.System.now()
        if (now - lastRotation > 1.minutes) {
            for (key in last.keys) {
                dropped.add(key.flowName)
            }
            last.clear()
            val tmp = current
            current = last
            last = tmp
            lastRotation = now
            lastConsumed.clear()
            val tmpConsumed = consumed
            consumed = lastConsumed
            lastConsumed = tmpConsumed
        }
    }

    private fun logDropped(dropped: Set<String>) {
        for (flowName in dropped) {
            Logger.w(TAG, "dropped notification for $flowName")
        }
    }

    private data class ConsumedItem(
        val flowNotificationKey: FlowNotificationKey,
        val notification: DataItem
    )
}