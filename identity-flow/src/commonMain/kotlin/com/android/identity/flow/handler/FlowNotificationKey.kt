package com.android.identity.flow.handler

import com.android.identity.cbor.DataItem

internal data class FlowNotificationKey(
    val flowName: String,
    val state: DataItem
)
