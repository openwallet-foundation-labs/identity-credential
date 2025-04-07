package org.multipaz.rpc.backend

import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec

suspend fun BackendEnvironment.getTable(tableSpec: StorageTableSpec): StorageTable {
    return getInterface(Storage::class)!!.getTable(tableSpec)
}

suspend fun BackendEnvironment.Companion.getTable(tableSpec: StorageTableSpec): StorageTable {
    return getInterface(Storage::class)!!.getTable(tableSpec)
}