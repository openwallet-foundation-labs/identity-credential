package com.android.identity_credential.wallet.util

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.android.identity.util.Logger
import kotlinx.io.files.Path

/**
 * Activity Logging facility.
 *
 * This prints out to a local file by default.
 */

enum class Logging {
    DIAGNOSTIC, VERIFY, UPDATE, PRESENTATION, COMMUNICATION
}

data class ActivityLogEntry(
    val timestamp: String,
    val transactionType: String,
    val dataShared: String?,
    val verifierIdentity: String?
)

object ActivityLogger {
    private const val TAG = "ActivityLogger"

    private val activityLoggingStates = mutableMapOf(
        Logging.VERIFY to false,
        Logging.UPDATE to false,
        Logging.PRESENTATION to false,
        Logging.COMMUNICATION to false
    )

    private var activityFileWriter: FileWriter? = null
    private var activityFileWriterPath: String? = null

    @Throws(IOException::class)
    fun startLoggingActivityToFile(activityLogPath: Path, activity: Logging) {
        activityLoggingStates[activity] = true

        if (activityFileWriter == null) {
            activityFileWriterPath = activityLogPath.toString()
            Logger.i(TAG, "Starting '$activity' logging to file $activityFileWriterPath")
            activityFileWriter = FileWriter(activityLogPath.toString())
        } else {
            Logger.i(TAG, "Appending '$activity' logging to file $activityFileWriterPath")
        }
    }

    @Throws(IOException::class)
    fun stopLoggingActivityToFile(activityLogPath: Path, activity: Logging) {
        activityLoggingStates[activity] = false

        if (activityLoggingStates.values.all { !it }) { // Check if all activities are stopped
            activityFileWriter?.close()
            activityFileWriter = null
            activityFileWriterPath = activityLogPath.toString()
            Logger.i(TAG, "Stopped all activity logging to file $activityFileWriterPath")
            activityFileWriterPath = null
        } else {
            Logger.i(TAG, "Stopped logging '$activity' to file $activityFileWriterPath")
        }
    }

    @JvmStatic
    fun logActivity(
        transactionType: Logging,
        dataShared: String? = null,
        verifierIdentity: String? = null
    ) {
        if (!activityLoggingStates[transactionType]!!) { // Check if logging is enabled for this type
            Logger.i(TAG, "Logging is disabled for event $transactionType")
            return // Exit immediately if logging is disabled
        }

        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSS", Locale.US).format(Date())
        val entry = ActivityLogEntry(
            timestamp = timeStamp,
            transactionType = transactionType.toString(),
            dataShared = dataShared,
            verifierIdentity = verifierIdentity
        )
        val logLine = buildString {
            append(timeStamp)
            if (transactionType != null) {
                append(": ")
                append(transactionType)
            }
            if (dataShared != null) {
                append(": ")
                append(dataShared)
            }
            if (verifierIdentity != null) {
                append(": ")
                append(verifierIdentity)
            }
        }

        Logger.i(TAG, "Logged activity: $entry")
        if (activityFileWriter != null) {
            try {
                activityFileWriter!!.write(logLine)
                activityFileWriter!!.write('\n'.code)
                activityFileWriter!!.flush()
            } catch (e: IOException) {
                println("Error writing activity log message to file: $e")
                e.printStackTrace()
            }
        }
    }
}
