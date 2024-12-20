package com.android.identity.storage.ephemeral

import com.android.identity.storage.Storage
import com.android.identity.storage.StorageTable
import com.android.identity.storage.StorageTableSpec
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class EphemeralStorage(val clock: Clock = Clock.System) : Storage {
    private val tables = mutableMapOf<String, EphemeralStorageTable>()

    override suspend fun getTable(tableSpec: StorageTableSpec): StorageTable {
        val table = tables[tableSpec.name]
        if (table == null) {
            val clockToUse = if (tableSpec.supportExpiration) clock else StoppedClock
            val newTable = EphemeralStorageTable(tableSpec, clockToUse)
            tables[tableSpec.name] = newTable
            return newTable
        } else if (table.spec != tableSpec) {
            throw IllegalArgumentException("Incompatible table config for '${tableSpec.name}'")
        } else {
            return table
        }
    }

    override suspend fun purgeExpired() {
        for (table in tables.values) {
            table.purgeExpired()
        }
    }

    companion object {
        object StoppedClock: Clock {
            override fun now(): Instant = Instant.DISTANT_PAST
        }
    }
}