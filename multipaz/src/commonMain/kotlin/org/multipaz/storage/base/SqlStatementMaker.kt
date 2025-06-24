package org.multipaz.storage.base

import org.multipaz.storage.StorageTableSpec

class SqlStatementMaker(
    val spec: StorageTableSpec,
    val textType: String,
    val blobType: String,
    val longType: String,
    val useReturningClause: Boolean,
    collationCharset: String?
) {
    val tableName = "Mz${spec.name}"

    private val collation = if (collationCharset != null) "COLLATE latin1_bin" else ""

    private val returning get() = if (useReturningClause) {
        "RETURNING 1"
    } else {
        ""
    }

    private val expirationCondition get() = if (spec.supportExpiration) {
        " AND expiration >= ?"
    } else {
        ""
    }

    private val partitionCondition get() = if (spec.supportPartitions) {
        " AND partitionId = ?"
    } else {
        ""
    }

    private val partitionDef = if (spec.supportPartitions) {
        "partitionId $textType $collation,"
    } else {
        ""
    }

    private val expirationDef = if (spec.supportExpiration) {
        "expiration $longType NOT NULL,"
    } else {
        ""
    }

    private val primaryKeyDef = if (spec.supportPartitions) {
        "PRIMARY KEY(partitionId, id)"
    } else {
        "PRIMARY KEY(id)"
    }

    val createTableStatement =
        """
            CREATE TABLE IF NOT EXISTS $tableName (
                $partitionDef
                id $textType $collation,
                $expirationDef
                data $blobType,
                $primaryKeyDef
            )
        """.trimIndent()

    val getStatement get() =
        """
            SELECT data
            FROM $tableName
            WHERE (id = ? $partitionCondition $expirationCondition)
        """.trimIndent()

    /**
     * SQL condition for the record with an id, a partition (if needed) and with
     * expiration check (if needed) already injected.
     *
     * This is needed for older Android Sqlite APIs that have no way to inject non-string
     * parameters.
     */
    fun conditionWithExpiration(nowSeconds: Long): String {
        val expirationCheck = if (spec.supportExpiration) {
            expirationCondition.replace("?", nowSeconds.toString())
        } else {
            ""
        }
        return "id = ? $partitionCondition $expirationCheck"
    }

    val purgeExpiredWithIdStatement =
        """
            DELETE
            FROM $tableName
            WHERE (id = ? $partitionCondition AND expiration < ?)
        """

    /**
     * SQL condition for the expired record with an id, and a partition (if needed).
     *
     * This is needed for older Android Sqlite APIs that have no way to inject non-string
     * parameters.
     */
    fun purgeExpiredWithIdCondition(timeSeconds: Long): String {
        return "id = ? $partitionCondition AND expiration < $timeSeconds"
    }

    val insertStatement: String = run {
        val names = StringBuilder()
        val values = StringBuilder()
        if (spec.supportPartitions) {
            names.append("partitionId, ")
            values.append("?, ")
        }
        names.append("id")
        values.append("?")
        if (spec.supportExpiration) {
            names.append(", expiration")
            values.append(", ?")
        }
        names.append(", data")
        values.append(", ?")
        "INSERT INTO $tableName ($names) VALUES($values)"
    }

    val updateStatement =
        """
            UPDATE $tableName SET data = ?
            WHERE (id = ? $partitionCondition $expirationCondition)
            $returning
        """.trimIndent()

    val updateWithExpirationStatement =
        """
            UPDATE $tableName SET data = ?, expiration = ?
            WHERE (id = ? $partitionCondition $expirationCondition)
            $returning
        """.trimIndent()

    fun enumerateStatement(withData: Boolean) =
        """
            SELECT ${if (withData) "id, data" else "id"}
            FROM $tableName
            WHERE (id > ? $partitionCondition $expirationCondition)
            ORDER BY id
        """.trimIndent()

    fun enumerateWithLimitStatement(withData: Boolean) =
        """
            SELECT ${if (withData) "id, data" else "id"}
            FROM $tableName
            WHERE (id > ? $partitionCondition $expirationCondition)
            ORDER BY id
            LIMIT ?
        """.trimIndent()

    /**
     * SQL condition for the record with an id, a partition (if needed) and with
     * expiration check (if needed) already injected.
     *
     * This is needed for older Android Sqlite APIs that have no way to inject non-string
     * parameters.
     */
    fun enumerateConditionWithExpiration(nowSeconds: Long): String {
        val expirationCheck = if (spec.supportExpiration) {
            expirationCondition.replace("?", nowSeconds.toString())
        } else {
            ""
        }
        return "id > ? $partitionCondition $expirationCheck"
    }

    val deleteOrUpdateCheckStatement =
        """
            SELECT 1 FROM $tableName
            WHERE (id = ? $partitionCondition $expirationCondition)
        """.trimIndent()

    val deleteStatement =
        """
            DELETE FROM $tableName
            WHERE (id = ? $partitionCondition $expirationCondition)
            $returning
        """.trimIndent()

    val deleteAllStatement =
        """
            DELETE FROM $tableName
        """.trimIndent()

    val deleteAllInPartitionStatement =
        """
            DELETE FROM $tableName
            WHERE (partitionId = ?)
        """.trimIndent()

    val purgeExpiredStatement =
        """
            DELETE
            FROM $tableName
            WHERE (expiration < ?)
        """.trimIndent()
}