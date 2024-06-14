package com.android.identity.flow.handler

import com.android.identity.cbor.DataItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.io.bytestring.ByteString

/** A low-level interface to register and unregister for notifications on the client. */
interface FlowNotifier {
    suspend fun<NotificationT: Any> register(
        flowName: String,
        opaqueState: DataItem,
        notifications: MutableSharedFlow<NotificationT>,
        deserializer: (DataItem) -> NotificationT
    )
    suspend fun unregister(flowName: String, opaqueState: DataItem)

    companion object {
        val SILENT = object : FlowNotifier {
            override suspend fun<NotificationT: Any> register(
                flowName: String,
                opaqueState: DataItem,
                notifications: MutableSharedFlow<NotificationT>,
                deserializer: (DataItem) -> NotificationT
            ) {
            }

            override suspend fun unregister(flowName: String, opaqueState: DataItem) {
            }
        }
    }
}