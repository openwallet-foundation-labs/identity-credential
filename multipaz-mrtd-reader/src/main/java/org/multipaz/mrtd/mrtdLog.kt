package org.multipaz.mrtd

internal var logger: ((level: Int, tag: String, msg: String, err: Throwable?) -> Unit)? = null

// match Android values
const val LOG_DEBUG = 3
const val LOG_INFO = 4
const val LOG_WARN = 5
const val LOG_ERROR = 6

fun mrtdSetLogger(
    externalLogger: ((level: Int, tag: String, msg: String, err: Throwable?) -> Unit)) {
    logger = externalLogger
}

fun mrtdLogE(tag: String, message: String, err: Throwable? = null) {
    val log = logger
    if (log != null) {
        log(LOG_ERROR, tag, message, err)
    }
}

fun mrtdLogW(tag: String, message: String, err: Throwable? = null) {
    val log = logger
    if (log != null) {
        log(LOG_WARN, tag, message, err)
    }
}

fun mrtdLogD(tag: String, message: String, err: Throwable? = null) {
    val log = logger
    if (log != null) {
        log(LOG_DEBUG, tag, message, err)
    }
}

fun mrtdLogI(tag: String, message: String, err: Throwable? = null) {
    val log = logger
    if (log != null) {
        log(LOG_INFO, tag, message, err)
    }
}
