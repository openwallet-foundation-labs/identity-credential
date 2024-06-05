package com.android.identity.wallet.server

import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.flow.environment.Notifications
import com.android.identity.flow.environment.Storage
import com.android.identity.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ServerNotifications(
    private val storage: Storage,
) : Notifications {
    companion object {
        private const val TAG = "ServerNotifications"
    }

    private val _eventFlow = MutableSharedFlow<Pair<String, ByteArray>>()

    override val eventFlow
        get() = _eventFlow.asSharedFlow()

    override suspend fun emit(
        targetId: String,
        payload: ByteArray
    ) {
        _eventFlow.emit(Pair(targetId, payload))
        // TODO: emit notification via Firebase
    }
}