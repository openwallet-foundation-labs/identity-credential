package org.multipaz.rpc.handler

import org.multipaz.cbor.DataItem
import kotlinx.coroutines.flow.MutableSharedFlow

/** A low-level interface to register and unregister for notifications on the client. */
interface RpcNotifier {
    suspend fun<NotificationT: Any> register(
        target: String,
        opaqueState: DataItem,
        notifications: MutableSharedFlow<NotificationT>,
        deserializer: (DataItem) -> NotificationT
    )
    suspend fun unregister(target: String, opaqueState: DataItem)

    companion object {
        val SILENT = object : RpcNotifier {
            override suspend fun<NotificationT: Any> register(
                target: String,
                opaqueState: DataItem,
                notifications: MutableSharedFlow<NotificationT>,
                deserializer: (DataItem) -> NotificationT
            ) {
            }

            override suspend fun unregister(target: String, opaqueState: DataItem) {
            }
        }
    }
}