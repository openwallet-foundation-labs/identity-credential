package com.android.identity.storage.ephemeral

import com.android.identity.storage.base.BaseStorage
import com.android.identity.storage.base.BaseStorageTable
import com.android.identity.storage.StorageTableSpec
import kotlinx.datetime.Clock

class EphemeralStorage(clock: Clock = Clock.System) : BaseStorage(clock) {
    override suspend fun createTable(tableSpec: StorageTableSpec): BaseStorageTable {
        val clockToUse = if (tableSpec.supportExpiration) clock else StoppedClock
        return EphemeralStorageTable(tableSpec, clockToUse)
    }
}