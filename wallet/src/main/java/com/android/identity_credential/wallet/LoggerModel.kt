package com.android.identity_credential.wallet

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import com.android.identity_credential.mrtd.mrtdSetLogger
import java.io.File


class LoggerModel(val application: Application, val sharedPreferences: SharedPreferences) {
    companion object {
        const val TAG = "LoggerModel"

        // Logging
        const val LOG_FOLDER_NAME = "log"
        const val LOG_FILE_NAME = "log.txt"
    }

    val logToFile = MutableLiveData(false)

    private val logDir = File(application.cacheDir, LOG_FOLDER_NAME)
    private val logFile = File(logDir, LOG_FILE_NAME)

    fun init() {
        Logger.setLogPrinter(AndroidLogPrinter())

        // Initialize first, start observing later; observer is always called on attachment.
        this.logToFile.value = sharedPreferences.getBoolean(WalletApplication.LOG_TO_FILE, false)
        this.logToFile.observeForever { logToFile ->
            sharedPreferences.edit {
                putBoolean(WalletApplication.LOG_TO_FILE, logToFile)
            }
            if (logToFile) {
                logDir.mkdirs()
                Logger.startLoggingToFile(logFile)
                Logger.i(TAG, "Started logging to a file")
            } else {
                Logger.i(TAG, "Stopped logging to a file")
                Logger.stopLoggingToFile()
            }
        }

        mrtdSetLogger { level, tag, msg, err ->
            when (level) {
                Log.INFO -> if (err == null) Logger.i(tag, msg) else Logger.i(tag, msg, err)
                Log.DEBUG -> if (err == null) Logger.d(tag, msg) else Logger.d(tag, msg, err)
                Log.WARN -> if (err == null) Logger.w(tag, msg) else Logger.w(tag, msg, err)
                Log.ERROR -> if (err == null) Logger.e(tag, msg) else Logger.e(tag, msg, err)
                else -> throw IllegalArgumentException("Unknown level: $level")
            }
        }
    }

    fun clearLog() {
        if (logToFile.value!!) {
            Logger.stopLoggingToFile()
        }
        logFile.delete()
        if (logToFile.value!!) {
            Logger.startLoggingToFile(logFile)
        }
        Logger.i(TAG, "Log cleared")
    }

    fun createLogSharingIntent(context: Context): Intent {
        // NB: authority must match what given for <provider> in the manifest.
        val authority = "com.android.identity_credential.wallet"
        // NB: must be context for which the <provider> is defined in the manifest.
        val shareUri = FileProvider.getUriForFile(context, authority, logFile);
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.setType("text/plain")
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sharingIntent.putExtra(Intent.EXTRA_STREAM, shareUri)
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "identity_credential.wallet log")
        return sharingIntent
    }
}