package org.multipaz.storage

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem
import org.multipaz.storage.base.BaseStorageTable
import kotlinx.io.bytestring.ByteString

/**
 * [StorageTable]'s name and features.
 *
 * NB: Once the table is created for the first time, its features must stay the same.
 *  - [name] name of the table, 60 characters at most, ASCII letters, digits or
 *    underscore, must start with a letter. Must be a unique name when compared
 *    with other table names in non-case-sensitive manner.
 *  - [supportPartitions] true if partitions are supported
 *  - [supportExpiration] true if expiration is supported
 *  - [schemaVersion] (optional) schema version of this table which typically defines
 *    the format of the data stored in the table. Initially it is set to 0. When the
 *    table is loaded for the first time, schema version is compared with the version
 *    saved in the storage and if there is a discrepancy, [schemaUpgrade] method is
 *    called. By default, this method throws [IllegalStateException], but it could,
 *    for instance just call [StorageTable.deleteAll] to wipe out incompatible data
 *    or it could change table data to make it compatible with the new schema.
 */
open class StorageTableSpec(
    val name: String,
    val supportPartitions: Boolean,
    val supportExpiration: Boolean,
    val schemaVersion: Long = 0
) {
    open suspend fun schemaUpgrade(oldTable: BaseStorageTable) {
        throw IllegalStateException("Schema change is not supported for '${oldTable.spec.name}'")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is StorageTableSpec) {
            return false
        }
        return name == other.name && supportPartitions == other.supportPartitions &&
            supportExpiration == other.supportExpiration && schemaVersion == other.schemaVersion
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + supportPartitions.hashCode()
        result = 31 * result + supportExpiration.hashCode()
        result = 31 * result + schemaVersion.hashCode()
        return result
    }

    internal fun encodeToByteString(): ByteString {
        val map = mutableMapOf<DataItem, DataItem>()
        map["name".toDataItem()] = name.toDataItem()
        map["supportPartitions".toDataItem()] = supportPartitions.toDataItem()
        map["supportExpiration".toDataItem()] = supportExpiration.toDataItem()
        map["schemaVersion".toDataItem()] = schemaVersion.toDataItem()
        return ByteString(Cbor.encode(CborMap(map)))
    }

    companion object {
        internal fun decodeByteString(data: ByteString): StorageTableSpec {
            val map = Cbor.decode(data.toByteArray())
            return StorageTableSpec(
                name = map["name"].asTstr,
                supportPartitions = map["supportPartitions"].asBoolean,
                supportExpiration = map["supportExpiration"].asBoolean,
                schemaVersion = map["schemaVersion"].asNumber
            )
        }
    }
}