/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.util

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Logging facility.
 *
 * This prints out to system out by default and can be configured using [.setLogPrinter].
 */
object Logger {
    private const val TAG = "Logger"

    const val LEVEL_D = 0
    const val LEVEL_I = 1
    const val LEVEL_W = 2
    const val LEVEL_E = 3

    var isDebugEnabled = true // TODO: make false by default

    private var fileWriter: Sink? = null
    private var fileWriterPath: Path? = null
    private var logPrinter: LogPrinter? = null

    fun startLoggingToFile(logPath: Path) {
        if (fileWriter != null) {
            w(TAG, "startLoggingToFile: Already logging to file $fileWriterPath")
            fileWriter!!.close()
            fileWriter = null
            fileWriterPath = null
        }
        d(TAG, "Starting logging to file $fileWriterPath")
        fileWriter = SystemFileSystem.sink(logPath).buffered()
        fileWriterPath = logPath
    }

    fun stopLoggingToFile() {
        if (fileWriter == null) {
            w(TAG, "startLoggingToFile: Not logging to file")
            return
        }
        fileWriter!!.close()
        fileWriter = null
        d(TAG, "Stopped logging to file ${fileWriterPath!!.name}")
        fileWriterPath = null
    }

    fun setLogPrinter(logPrinter: LogPrinter?) {
        this.logPrinter = logPrinter
    }

    private fun prepareLine(
        level: Int,
        tag: String,
        msg: String,
        throwable: Throwable?
    ): String {
        val sb = StringBuilder()
        val now = Clock.System.now()
        val dt = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val timeStamp = dt.format(LocalDateTime.Formats.ISO)
        sb.append(timeStamp)
        sb.append(": ")
        when (level) {
            LEVEL_D -> sb.append("DEBUG")
            LEVEL_I -> sb.append("INFO")
            LEVEL_W -> sb.append("WARNING")
            LEVEL_E -> sb.append("ERROR")
        }
        sb.append(": ")
        sb.append(tag)
        sb.append(": ")
        sb.append(msg)
        if (throwable != null) {
            sb.append("\nEXCEPTION: ")
            sb.append(throwable)
        }
        return sb.toString()
    }

    private fun println(
        level: Int,
        tag: String,
        msg: String,
        throwable: Throwable?
    ) {
        var logLine: String? = null
        if (logPrinter != null) {
            logPrinter!!.printLn(level, tag, msg, throwable)
        } else {
            logLine = prepareLine(level, tag, msg, throwable)
            println(logLine)
        }
        if (fileWriter != null) {
            if (logLine == null) {
                logLine = prepareLine(level, tag, msg, throwable)
            }
            try {
                fileWriter!!.write((logLine + "\n").encodeToByteArray())
                fileWriter!!.flush()
            } catch (e: Throwable) {
                if (logPrinter != null) {
                    logPrinter!!.printLn(LEVEL_E, tag, "Error writing log message to file", e)
                } else {
                    println("Error writing log message to file: $e")
                }
                e.printStackTrace()
            }
        }
    }

    fun d(tag: String, msg: String) {
        if (isDebugEnabled) {
            println(LEVEL_D, tag, msg, null)
        }
    }

    fun d(tag: String, msg: String, throwable: Throwable) {
        if (isDebugEnabled) {
            println(LEVEL_D, tag, msg, throwable)
        }
    }

    fun i(tag: String, msg: String) {
        println(LEVEL_I, tag, msg, null)
    }

    fun i(tag: String, msg: String, throwable: Throwable) {
        println(LEVEL_I, tag, msg, throwable)
    }

    fun w(tag: String, msg: String) {
        println(LEVEL_W, tag, msg, null)
    }

    fun w(tag: String, msg: String, throwable: Throwable) {
        println(LEVEL_W, tag, msg, throwable)
    }

    fun e(tag: String, msg: String) {
        println(LEVEL_E, tag, msg, null)
    }

    fun e(tag: String, msg: String, throwable: Throwable) {
        println(LEVEL_E, tag, msg, throwable)
    }

    private fun hex(level: Int, tag: String, message: String, data: ByteArray) {
        val sb = "$message: ${data.size} bytes of data: " + data.toHex()
        println(level, tag, sb, null)
    }

    fun dHex(tag: String, message: String, data: ByteArray) {
        if (isDebugEnabled) {
            hex(LEVEL_D, tag, message, data)
        }
    }

    fun iHex(tag: String, message: String, data: ByteArray) {
        hex(LEVEL_I, tag, message, data)
    }

    fun wHex(tag: String, message: String, data: ByteArray) {
        hex(LEVEL_W, tag, message, data)
    }

    fun eHex(tag: String, message: String, data: ByteArray) {
        hex(LEVEL_E, tag, message, data)
    }

    private fun cbor(level: Int, tag: String, message: String, encodedCbor: ByteArray) {
        val sb = "$message: ${encodedCbor.size} bytes of CBOR: " + encodedCbor.toHex() +
                "\n" +
                "In diagnostic notation:\n" +
                Cbor.toDiagnostics(
                    encodedCbor,
                    setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR)
                )
        println(level, tag, sb, null)
    }

    fun dCbor(tag: String, message: String, encodedCbor: ByteArray) {
        if (isDebugEnabled) {
            cbor(LEVEL_D, tag, message, encodedCbor)
        }
    }

    fun iCbor(tag: String, message: String, encodedCbor: ByteArray) {
        cbor(LEVEL_I, tag, message, encodedCbor)
    }

    fun wCbor(tag: String, message: String, encodedCbor: ByteArray) {
        cbor(LEVEL_W, tag, message, encodedCbor)
    }

    fun eCbor(tag: String, message: String, encodedCbor: ByteArray) {
        cbor(LEVEL_E, tag, message, encodedCbor)
    }

    interface LogPrinter {
        fun printLn(
            level: Int, tag: String, msg: String, throwable: Throwable?
        )
    }
}