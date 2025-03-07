package org.multipaz.flow.handler

import org.multipaz.cbor.DataItem

internal data class FlowNotificationKey(
    val flowName: String,
    val state: DataItem
)
