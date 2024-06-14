package com.android.identity.flow.handler

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation for [FlowNotifications] that can also serve as [FlowNotifier] for
 * testing/developing in local environment.
 */
class FlowNotificationsLocal(
    private val cipher: SimpleCipher
): FlowNotifications, FlowNotifier {
    private val lock = Mutex()
    private val flowMap = mutableMapOf<FlowNotificationKey, Target<*>>()

    companion object {
        const val TAG = "FlowNotificationsLocal"
    }

    override suspend fun emit(flowName: String, state: DataItem, notification: DataItem) {
        val ref = FlowNotificationKey(flowName, state)
        // TODO: it is not quite clear if we should delay notifications. It might make
        // sense to actually remove this and ensure that everything works as expected,
        // as the need for the delay is an indication of a race condition somewhere
        CoroutineScope(Dispatchers.IO).launch {
            delay(200.milliseconds)
            Logger.i(TAG, "Notification emitted for $flowName")
            lock.withLock { flowMap[ref] }?.emit(notification)
        }
    }

    override suspend fun<NotificationT: Any> register(
        flowName: String,
        opaqueState: DataItem,
        notifications: MutableSharedFlow<NotificationT>,
        deserializer: (DataItem) -> NotificationT
    ) {
        val ref = FlowNotificationKey(flowName, Cbor.decode(cipher.decrypt(opaqueState.asBstr)))
        lock.withLock {
            flowMap[ref] = Target(notifications, deserializer)
        }
    }

    override suspend fun unregister(flowName: String, opaqueState: DataItem) {
        val ref = FlowNotificationKey(flowName, Cbor.decode(cipher.decrypt(opaqueState.asBstr)))
        lock.withLock {
            flowMap.remove(ref)
        }
    }

    private class Target<NotificationT : Any>(
        val flow: MutableSharedFlow<NotificationT>,
        val deserializer: (DataItem) -> NotificationT
    ) {
        suspend fun emit(dataItem: DataItem) {
            Logger.i(TAG, "Dispatch notification to the listener")
            flow.emit(deserializer(dataItem))
        }
    }
}