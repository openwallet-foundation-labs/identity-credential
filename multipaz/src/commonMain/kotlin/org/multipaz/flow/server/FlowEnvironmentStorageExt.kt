package org.multipaz.flow.server

import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec

suspend fun FlowEnvironment.getTable(tableSpec: StorageTableSpec): StorageTable {
    return getInterface(Storage::class)!!.getTable(tableSpec)
}