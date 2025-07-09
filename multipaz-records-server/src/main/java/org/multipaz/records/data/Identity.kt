package org.multipaz.records.data

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
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
        table.update(id, ByteString(data.toCbor()))
    }

    companion object {
        /** Creates new identity with the given data */
        suspend fun create(data: IdentityData): Identity {
            val table = BackendEnvironment.getTable(tableSpec)
            val id = table.insert(
                key = createKeyFor(data.core),
                data = ByteString(data.toCbor())
            )
            return Identity(id, data)
        }

        private fun dobPrefix(dob: LocalDate): String {
            val dobId = dob.year * 366 + dob.dayOfYear
            return byteArrayOf(
                ((dobId shr 16) and 0xFF).toByte(),
                ((dobId shr 8) and 0xFF).toByte(),
                (dobId and 0xFF).toByte()
            ).toBase64Url()
        }

        private fun createKeyFor(common: Map<String, DataItem>): String {
            // Create key based on date of birth to be able to search by it efficiently
            val dob = common["birth_date"]?.asDateString
                ?: throw IllegalArgumentException("No date of birth")
            val suffix = Random.Default.nextBytes(12).toBase64Url()
            return "${dobPrefix(dob)}$suffix"
        }

        /**
         * Finds list of [Identity] objects in storage that match the given name and date of birth.
         */
        suspend fun findByNameAndDateOfBirth(
            familyName: String,
            givenName: String,
            dateOfBirth: LocalDate
        ): List<Identity> = buildList {
            val limit = 10
            val dobPrefix = dobPrefix(dateOfBirth)
            var afterKey = dobPrefix
            val table = BackendEnvironment.getTable(tableSpec)
            while (true) {
                val keys = table.enumerate(afterKey = afterKey, limit = limit)
                for (key in keys) {
                    if (!key.startsWith(dobPrefix)) {
                        return@buildList
                    }
                    val identity = findById(key)
                    if (identity.data.core["family_name"]?.asTstr == familyName &&
                        identity.data.core["given_name"]?.asTstr == givenName) {
                        check(identity.data.core["birth_date"]?.asDateString == dateOfBirth)
                        add(identity)
                    }
                }
                if (keys.size < limit) {
                    break
                }
                afterKey = keys.last()
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