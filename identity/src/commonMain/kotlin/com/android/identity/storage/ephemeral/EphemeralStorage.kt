package com.android.identity.storage.ephemeral

import com.android.identity.storage.base.BaseStorage
import com.android.identity.storage.base.BaseStorageTable
import com.android.identity.storage.StorageTableSpec
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder

class EphemeralStorage(clock: Clock = Clock.System) : BaseStorage(clock) {
    override suspend fun createTable(tableSpec: StorageTableSpec): BaseStorageTable {
        val clockToUse = if (tableSpec.supportExpiration) clock else StoppedClock
        return EphemeralStorageTable(this, tableSpec, clockToUse)
    }

    suspend fun serialize(): ByteString {
        val out = ByteStringBuilder()
        for (table in enumerateTables()) {
            (table as EphemeralStorageTable).serialize(out)
        }
        return out.toByteString()
    }

    companion object {
        fun deserialize(data: ByteString, clock: Clock = Clock.System): EphemeralStorage {
            val storage = EphemeralStorage(clock)
            var offset = 0
            val tables = mutableListOf<EphemeralStorageTable>()
            while (offset < data.size) {
                val (newOffset, table) = EphemeralStorageTable.deserialize(storage, clock, data, offset)
                offset = newOffset
                tables.add(table)
            }
            storage.initTables(tables)
            return storage
        }
    }
}