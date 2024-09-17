package com.android.identity.server

import com.android.identity.flow.server.Storage
import kotlinx.io.bytestring.ByteString
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

internal class ServerStorage(
    private val jdbc: String,
    private val user: String = "",
    private val password: String = ""
): Storage {
    private val createdTables = ConcurrentHashMap<String, Boolean>()
    private val blobType: String

    init {

        // initialize appropriate drives (this also ensures that dependencies don't get
        // stripped when building WAR file).
        if (jdbc.startsWith("jdbc:hsqldb:")) {
            blobType = "BLOB"
            org.hsqldb.jdbc.JDBCDriver()
        } else if (jdbc.startsWith("jdbc:mysql:")) {
            blobType = "LONGBLOB"  // MySQL BLOB is limited to 64k
            com.mysql.cj.jdbc.Driver()
        } else {
            blobType = "BLOB"
        }
    }

    override suspend fun get(table: String, peerId: String, key: String): ByteString? {
        val safeTable = sanitizeTable(table)
        val connection = acquireConnection()
        ensureTable(connection, safeTable)
        val statement = connection.prepareStatement(
            "SELECT data FROM $safeTable WHERE (peerId = ? AND id = ?)")
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
        val safeTable = sanitizeTable(table)
        val recordKey = key.ifEmpty { Base64.encode(Random.Default.nextBytes(18)) }
        val connection = acquireConnection()
        ensureTable(connection, safeTable)
        val statement = connection.prepareStatement("INSERT INTO $safeTable VALUES(?, ?, ?)")
        statement.setString(1, recordKey)
        statement.setString(2, peerId)
        statement.setBytes(3, data.toByteArray())
        val count = statement.executeUpdate()
        releaseConnection(connection)
        if (count != 1) {
            throw IllegalStateException("Value was not inserted")
        }
        return recordKey
    }

    override suspend fun update(table: String, peerId: String, key: String, data: ByteString) {
        val safeTable = sanitizeTable(table)
        val connection = acquireConnection()
        ensureTable(connection, safeTable)
        val statement = connection.prepareStatement(
            "UPDATE $safeTable SET data = ? WHERE (peerId = ? AND id = ?)")
        statement.setBytes(1, data.toByteArray())
        statement.setString(2, peerId)
        statement.setString(3, key)
        val count = statement.executeUpdate()
        releaseConnection(connection)
        if (count != 1) {
            throw IllegalStateException("Value was not updated")
        }
    }

    override suspend fun delete(table: String, peerId: String, key: String): Boolean {
        val safeTable = sanitizeTable(table)
        val connection = acquireConnection()
        ensureTable(connection, safeTable)
        val statement = connection.prepareStatement(
            "DELETE FROM $safeTable WHERE (peerId = ? AND id = ?)")
        statement.setString(1, peerId)
        statement.setString(2, key)
        val count = statement.executeUpdate()
        releaseConnection(connection)
        return count > 0
    }

    override suspend fun enumerate(table: String, peerId: String,
                                   notBeforeKey: String, limit: Int): List<String> {
        val safeTable = sanitizeTable(table)
        val connection = acquireConnection()
        ensureTable(connection, safeTable)
        val opt = if (limit < Int.MAX_VALUE) " LIMIT 0, $limit" else ""
        val statement = connection.prepareStatement(
            "SELECT id FROM $safeTable WHERE (peerId = ? AND id > ?) ORDER BY id$opt")
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

    private fun ensureTable(connection: Connection, safeTable: String) {
        if (!createdTables.contains(safeTable)) {
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS $safeTable (
                    id VARCHAR(64) PRIMARY KEY,
                    peerId VARCHAR(64),
                    data $blobType
                )
            """.trimIndent())
            createdTables[safeTable] = true
        }
    }

    private fun acquireConnection(): Connection {
        return DriverManager.getConnection(jdbc, user, password)
    }

    private fun releaseConnection(connection: Connection) {
        connection.close()
    }

    private fun sanitizeTable(table: String): String {
        return "Wt$table"
    }

    companion object {
        fun defaultDatabase(): String {
            val dbFile = File("environment/db/db.hsqldb").absoluteFile
            if (!dbFile.canRead()) {
                val parent = File(dbFile.parent)
                if (!parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw Exception("Cannot create database folder ${parent.absolutePath}")
                    }
                }
            }
            return "jdbc:hsqldb:file:${dbFile.absolutePath}"
        }
    }
}