package com.android.identity.wallet.server

import com.android.identity.flow.environment.Storage
import kotlinx.io.bytestring.ByteString
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

class ServerStorage(
    private val jdbc: String,
    private var user: String = "",
    private val password: String = ""
): Storage {
    private val createdTables = mutableSetOf<String>()

    override suspend fun get(table: String, peerId: String, key: String): ByteString? {
        val connection = acquireConnection()
        ensureTable(connection, table)
        val statement = connection.prepareStatement(
            "SELECT data FROM $table WHERE (clientId = ? AND key = ?)")
        statement.setString(1, peerId)
        statement.setString(2, key)
        val resultSet = statement.executeQuery()
        if (!resultSet.next()) {
            return null
        }
        val bytes = resultSet.getBytes(1)
        releaseConnection(connection)
        return ByteString(bytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun insert(table: String, peerId: String, data: ByteString, key: String): String {
        val recordKey = key.ifEmpty { Base64.encode(Random.Default.nextBytes(18)) }
        val connection = acquireConnection()
        ensureTable(connection, table)
        val statement = connection.prepareStatement("INSERT INTO $table VALUES(?, ?, ?)")
        statement.setString(1, recordKey)
        statement.setString(2, peerId)
        statement.setBytes(3, data.toByteArray())
        val count = statement.executeUpdate()
        connection.commit()
        releaseConnection(connection)
        if (count != 1) {
            throw IllegalStateException("Value was not inserted")
        }
        return recordKey
    }

    override suspend fun update(table: String, peerId: String, key: String, data: ByteString) {
        val connection = acquireConnection()
        ensureTable(connection, table)
        val statement = connection.prepareStatement(
            "UPDATE $table SET data = ? WHERE (clientId = ? AND key = ?)")
        statement.setBytes(1, data.toByteArray())
        statement.setString(2, peerId)
        statement.setString(3, key)
        val count = statement.executeUpdate()
        connection.commit()
        releaseConnection(connection)
        if (count != 1) {
            throw IllegalStateException("Value was not updated")
        }
    }

    override suspend fun delete(table: String, peerId: String, key: String): Boolean {
        val connection = acquireConnection()
        ensureTable(connection, table)
        val statement = connection.prepareStatement(
            "DELETE FROM $table WHERE (clientId = ? AND key = ?)")
        statement.setString(1, peerId)
        statement.setString(2, key)
        val count = statement.executeUpdate()
        connection.commit()
        releaseConnection(connection)
        return count > 0
    }

    override suspend fun enumerate(table: String, peerId: String,
                                   notBeforeKey: String, limit: Int): List<String> {
        val connection = acquireConnection()
        ensureTable(connection, table)
        val opt = if (limit < Int.MAX_VALUE) " LIMIT 0, $limit" else ""
        val statement = connection.prepareStatement(
            "SELECT key FROM $table WHERE (clientId = ? AND key > ?) ORDER BY key$opt")
        statement.setString(1, peerId)
        statement.setString(2, notBeforeKey)
        val resultSet = statement.executeQuery()
        val list = mutableListOf<String>()
        while (resultSet.next()) {
            list.add(resultSet.getString(1))
        }
        releaseConnection(connection)
        return list
    }

    private fun ensureTable(connection: Connection, table: String) {
        if (!createdTables.contains(table)) {
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS $table (
                    key VARCHAR(64) PRIMARY KEY,
                    clientId VARCHAR(64),
                    data BLOB
                )
            """.trimIndent())
            connection.commit()
            createdTables.add(table)
        }
    }

    private fun acquireConnection(): Connection {
        return DriverManager.getConnection(jdbc, user, password)
    }

    private fun releaseConnection(connection: Connection) {
        connection.close()
    }
}