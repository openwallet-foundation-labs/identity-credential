package org.multipaz.storage.ephemeral

import org.multipaz.cbor.Bstr
import org.multipaz.storage.base.BaseStorage
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.StorageTableSpec
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
            val bytes = data.toByteArray()
            val tables = mutableListOf<EphemeralStorageTable>()
            while (offset < bytes.size) {
                val (newOffset, table) = EphemeralStorageTable.deserialize(storage, clock, bytes, offset)
                offset = newOffset
                tables.add(table)
            }
            storage.initTables(tables)
            return storage
        }
    }
}