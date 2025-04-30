package org.multipaz.util

internal actual fun platformLogPrinter(level: Logger.Level, tag: String, msg: String, throwable: Throwable?) {
    println(Logger.prepareLine(level, tag, msg, throwable))
}
