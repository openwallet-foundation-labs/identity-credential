package com.android.identity.flow.server

import kotlinx.io.bytestring.ByteString

/**
 * Simple persistent storage interface.
 *
 * Data records are organized using tables, keys, and peerIds. Table must be an ASCII string
 * that does not come from an external source (as it is not escaped). It may or may not be
 * case-sensitive. Key is a string that uniquely identifies a record in a table. Additionally,
 * peerId is a string that identifies a "counterpart" entity, i.e. a particular client in the
 * server environment, or a particular server in the client environment. In most cases all
 * three record identifiers must be provided (table, peerId, and the key).
 *
 * Payload of the record is always just a blob of data. Storage does not interpret that data
 * in any way.
 */
interface Storage {
    /**
     * Retrieves data from storage.
     *
     * Returns null if record is not found.
     */
    suspend fun get(table: String, peerId: String, key: String): ByteString?

    /**
     * Inserts a new record.
     *
     * If the [key] is empty, a new unique key is generated. New key is guaranteed to only use
     * URL-safe characters.
     */
    suspend fun insert(table: String, peerId: String, data: ByteString, key: String = ""): String

    /**
     * Updates the data of an existing record.
     */
    suspend fun update(table: String, peerId: String, key: String, data: ByteString)

    /**
     * Deletes a record.
     *
     * Returns true if record was deleted, false if it was not found.
     */
    suspend fun delete(table: String, peerId: String, key: String): Boolean

    /**
     * Enumerate keys of the records with given table and peerId in key lexicographic order.
     *
     * If [limit] is given, no more than the given number of keys are returned. If [notBeforeKey]
     * is given only keys that follow the give key are returned. By specifying the desired [limit]
     * and passing last key from the previously returned list as [notBeforeKey] allows enumerating
     * all of the key in manageable chunks.
     */
    suspend fun enumerate(table: String, peerId: String,
                          notBeforeKey: String = "", limit: Int = Int.MAX_VALUE): List<String>
}