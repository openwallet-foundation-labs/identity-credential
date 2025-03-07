package org.multipaz.flow.handler

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.util.Logger
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
    private val coroutineScope: CoroutineScope,
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
        // Update: it seems that we start listening for notifications a bit later than
        // they are emitted.
        coroutineScope.launch {
            delay(200.milliseconds)
            val flow = lock.withLock { flowMap[ref] };
            if (flow == null) {
                Logger.i(TAG, "Notification has been dropped for $flowName [$notification]}")
            } else {
                Logger.i(TAG, "Notification is being emitted for $flowName [$notification]")
                flow.emit(notification)
            }
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
            flow.emit(deserializer(dataItem))
        }
    }
}