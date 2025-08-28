package org.multipaz.records.data

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tstr
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toBase64Url
import kotlin.random.Random

/**
 * Class that holds id and data for a given person.
 *
 * Id is immutable, and data can be modified.
 */
class Identity private constructor(
    val id: String,
    var data: IdentityData
) {
    /** Saves updates to data in storage. */
    suspend fun save() {
        val table = BackendEnvironment.getTable(tableSpec)
        check(id == data.core["utopia_id_number"]!!.asTstr)
        table.update(id, ByteString(data.toCbor()))
    }

    companion object {
        /** Creates new identity with the given data */
        suspend fun create(data: IdentityData): Identity {
            val table = BackendEnvironment.getTable(tableSpec)
            while (true) {
                val mutableCore = data.core.toMutableMap()
                val n = Random.Default.nextInt(100000000).toString().padStart(8, '0')
                val utopiaId = n.substring(0, 4) + "-" + n.substring(4, 8)
                mutableCore["utopia_id_number"] = Tstr(utopiaId)
                val dataToStore = IdentityData(mutableCore.toMap(), data.records)
                try {
                    val id = table.insert(
                        key = utopiaId,
                        data = ByteString(dataToStore.toCbor())
                    )
                    return Identity(id, dataToStore)
                } catch (_: KeyExistsStorageException) {
                    // try a different key
                }
            }
        }

        /**
         * Finds [Identity] object in the storage for the given [id].
         */
        suspend fun findById(id: String): Identity {
            val table = BackendEnvironment.getTable(tableSpec)
            val data = table.get(id) ?: throw IdentityNotFoundException()
            return Identity(id, IdentityData.fromCbor(data.toByteArray()))
        }

        /**
         * Deletes [Identity] object with the given [id] from the storage.
         */
        suspend fun deleteById(id: String): Boolean {
            val table = BackendEnvironment.getTable(tableSpec)
            return table.delete(id)
        }

        /**
         * Returns the list of all identity [id] values in the storage.
         *
         * TODO: this method will be replaced by giving each google login its own list of
         * identities.
         */
        suspend fun listAll(): List<String> {
            return BackendEnvironment.getTable(tableSpec).enumerate()
        }

        private val tableSpec = StorageTableSpec(
            name = "IdentityData",
            supportPartitions = false,
            supportExpiration = false
        )
    }
}