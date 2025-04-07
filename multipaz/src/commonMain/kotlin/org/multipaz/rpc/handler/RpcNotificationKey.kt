package org.multipaz.rpc.handler

import org.multipaz.cbor.DataItem

internal data class RpcNotificationKey(
    val target: String,
    val state: DataItem
)
