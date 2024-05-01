package com.android.identity_credential.mrtd

import android.util.Log

internal var logger: ((level: Int, tag: String, msg: String, err: Throwable?) -> Unit)? = null

fun mrtdSetLogger(externalLogger: ((level: Int, tag: String, msg: String, err: Throwable?) -> Unit)) {
    logger = externalLogger
}

fun mrtdLogE(
    tag: String,
    message: String,
    err: Throwable? = null,
) {
    val log = logger
    if (log != null) {
        log(Log.ERROR, tag, message, err)
    } else {
        Log.e(tag, message, err)
    }
}

fun mrtdLogW(
    tag: String,
    message: String,
    err: Throwable? = null,
) {
    val log = logger
    if (log != null) {
        log(Log.WARN, tag, message, err)
    } else {
        Log.w(tag, message, err)
    }
}

fun mrtdLogD(
    tag: String,
    message: String,
    err: Throwable? = null,
) {
    val log = logger
    if (log != null) {
        log(Log.DEBUG, tag, message, err)
    } else {
        Log.e(tag, message, err)
    }
}

fun mrtdLogI(
    tag: String,
    message: String,
    err: Throwable? = null,
) {
    val log = logger
    if (log != null) {
        log(Log.INFO, tag, message, err)
    } else {
        Log.i(tag, message, err)
    }
}
