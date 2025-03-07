package org.multipaz.storage

import org.multipaz.storage.base.BaseStorage
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.storage.jdbc.JdbcStorage
import kotlinx.datetime.Clock

var count: Int = 0

/**
 * Creates a list of empty [Storage] objects for testing.
 */
actual fun createTransientStorageList(testClock: Clock): List<Storage> {
    org.hsqldb.jdbc.JDBCDriver()
    com.mysql.cj.jdbc.Driver()
    org.postgresql.Driver()
    return listOf(
        EphemeralStorage(testClock),
        JdbcStorage(
            jdbc = "jdbc:hsqldb:mem:tmp${count++}",
            clock = testClock,
            keySize = 3),
        /*
        // This can be enabled if MySQL installation is available for testing.
        // Steps to initialize suitable database:
        // CREATE USER 'wallet'@'localhost' IDENTIFIED BY 'XP4xpGNz'
        // CREATE DATABASE wallet;
        // GRANT ALL PRIVILEGES ON wallet.* TO 'wallet'@'localhost';
        JdbcStorage(
            jdbc = "jdbc:mysql://localhost:3306/wallet?serverTimezone=UTC",
            user = "wallet",
            password = "XP4xpGNz",
            clock = testClock,
            keySize = 3
        ),
        */
        /*
        // This can be enabled if Postgresql installation is available for testing:
        JdbcStorage(
            jdbc = "jdbc:postgresql://localhost:5432/test",
            user = "test",
            password = "",
            clock = testClock,
            keySize = 3
        )
         */
    )
}

actual fun createPersistentStorage(name: String, testClock: Clock): Storage? {
    return JdbcStorage(
        jdbc = "jdbc:hsqldb:mem:p${name}",
        clock = testClock,
        keySize = 3)
}