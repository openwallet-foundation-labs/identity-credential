package org.multipaz.flow.client

import kotlinx.coroutines.flow.SharedFlow

interface FlowNotifiable<NotificationT>: FlowBase {
    val notifications: SharedFlow<NotificationT>
}